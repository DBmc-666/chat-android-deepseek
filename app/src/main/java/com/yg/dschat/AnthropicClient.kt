package com.yg.dschat

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 官方 Messages 协议客户端（Anthropic 兼容）。
 *
 * 协议要点（对照 api-docs.deepseek.com/zh-cn/guides/build_a_chatbot）：
 *  - POST {base}/anthropic/v1/messages，鉴权头 x-api-key
 *  - 工具以 {type,name} 声明，服务端执行，客户端只渲染结果块
 *  - 历史轮次的思考块与搜索块不回传；refusal 轮次整轮丢弃
 *  - 本版只开 deepseek_web_search，未开 deepseek_read_url（避免 read_link 签名回传的复杂度）
 */
/** optString 对 JSONObject.NULL 会返回字面量 "null"，统一清成空串 */
private fun String.orNullString(): String = if (this == "null") "" else this

/** 只有 PDF / Word / PPT 需要走文字识别；图片、文本、表格、电子书都用 user_data */
private fun needsDocParsing(mime: String, name: String): Boolean {
    val m = mime.lowercase()
    val n = name.lowercase()
    if (m.startsWith("image/")) return false
    return m == "application/pdf" ||
        m.contains("word") || m.contains("presentation") || m.contains("powerpoint") ||
        listOf(".pdf", ".doc", ".docx", ".ppt", ".pptx").any { n.endsWith(it) }
}

/** 客户端工具调用（备忘），与 server_tool_use 不是一回事 */
data class ToolUseCall(val id: String, val name: String, val inputJson: String)

/** 流式期间累积的 tool_use：id/name 在 start 到齐，input 由 input_json_delta 拼出来 */
private class PendingTool(val id: String, val name: String) {
    val buf = StringBuilder()
}

class AnthropicClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var current: Call? = null

    fun cancel() {
        current?.cancel()
        current = null
    }

    /** 上传附件到 Files API，返回 file_id；失败返回 null。 */
    fun uploadFile(
        settings: AppSettings,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        isImage: Boolean,
    ): String? {
        val base = settings.baseUrl.trim().trimEnd('/')
        if (base.isBlank() || settings.apiKey.isBlank()) return null
        // 官方规则：text_recognition 只吃 PDF/Word/PPT/图片；
        // 纯文本、表格、电子书必须用 user_data，用错会 400
        val purpose = if (needsDocParsing(mime, fileName)) "text_recognition" else "user_data"
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // purpose 必须出现在 file 字段之前
            .addFormDataPart("purpose", purpose)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$base/anthropic/v1/files")
            .addHeader("x-api-key", settings.apiKey)
            .addHeader("anthropic-beta", "files-api-2025-04-14")
            .post(multipart)
            .build()
        return runCatching {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IOException(readableError(res.code, text))
                JSONObject(text).optString("id").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    fun stream(
        settings: AppSettings,
        history: List<Msg>,
        question: String,
        attachments: List<Attachment> = emptyList(),
        into: Msg,
        /** 工具循环时由外层传入已构造好的 messages，跳过 buildMessages */
        prebuiltMessages: JSONArray? = null,
        /** 非空时收集本轮客户端工具调用 */
        toolSink: MutableList<ToolUseCall>? = null,
        /** 注入备忘工具声明 */
        memoEnabled: Boolean = false,
        onUpdate: () -> Unit,
        onTitle: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Call? {
        if (settings.apiKey.isBlank()) {
            onError("请先在设置里填写 DeepSeek API Key")
            return null
        }
        val base = settings.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            onError("请先在设置里填写 API 地址")
            return null
        }

        val payload = JSONObject().apply {
            put("model", settings.model)
            put("stream", true)
            put("messages", prebuiltMessages ?: buildMessages(history, question, attachments, settings))
            // 官方文档：思考模式默认打开、effort 默认 high。
            // 所以关掉时必须显式传 disabled，省略等于放任默认值（仍然会思考）。
            put(
                "thinking",
                JSONObject().put("type", if (settings.deepThinking) "enabled" else "disabled"),
            )
            val tools = JSONArray()
            // 开备忘时全部改用客户端工具：搜索走官方 /v1/tools/search 接口，
            // 这样和服务端工具不冲突，助手既能联网又能自己翻备忘。
            // 官方限制参考：other tools cannot be used together with deepseek_web_search yet
            val clientMode = memoEnabled && settings.memoActive
            if (clientMode) {
                val defs = com.yg.dschat.memo.ClientTools.defs(settings.webSearch)
                for (i in 0 until defs.length()) tools.put(defs.getJSONObject(i))
            } else {
                if (settings.webSearch) {
                    tools.put(JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search"))
                }
                // 阅读链接总是开启：只在用户消息里出现 URL 时才会真正抓取
                tools.put(JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url"))
            }
            put("tools", tools)
            if (settings.safetyHigh) put("safety_check", JSONObject().put("level", "high"))
            if (settings.autoTitle) put("title", JSONObject().put("enabled", true))
        }.toString()

        lastPayload = payload

        val requestBuilder = Request.Builder()
            .url("$base/anthropic/v1/messages")
            .addHeader("x-api-key", settings.apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")

        // 只要消息里引用了已上传文件（image/document block），就必须带 files-api beta 头，
        // 否则服务端返回 400 "Missing or invalid anthropic-beta header"
        val hasFiles = attachments.any { !it.fileId.isNullOrBlank() } ||
            history.any { u -> u.attachments.any { !it.fileId.isNullOrBlank() } }
        if (hasFiles) {
            requestBuilder.addHeader("anthropic-beta", "files-api-2025-04-14")
        }

        val request = requestBuilder
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = http.newCall(request)
        current = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                onError(e.message ?: "网络请求失败")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val text = runCatching { res.body?.string() }.getOrNull().orEmpty()
                        onError(readableError(res.code, text))
                        return
                    }
                    val source = res.body?.source()
                    if (source == null) {
                        onError("响应为空")
                        return
                    }

                    val indexMap = HashMap<Int, Int>()   // 服务端块序号 -> blocks 下标
                    // server_tool_use 块不展示，但要原样留着，和结果块成对回传
                    val toolUseRaw = HashMap<String, String>()
                    // 客户端工具（备忘）的调用，按块序号累积
                    val pendingTools = HashMap<Int, PendingTool>()
                    var stopReason: String? = null
                    var remoteTitle: String? = null
                    var finished = false

                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty() || data == "[DONE]") continue
                            val ev = runCatching { JSONObject(data) }.getOrNull() ?: continue

                            when (ev.optString("type")) {
                                "content_block_start" -> {
                                    val idx = ev.optInt("index")
                                    val cb = ev.optJSONObject("content_block")
                                    when (cb?.optString("type")) {
                                        "thinking" -> into.withBlocks { list ->
                                            indexMap[idx] = list.size
                                            list.add(Block.Thinking(startedAt = System.currentTimeMillis()))
                                        }
                                        "text" -> into.withBlocks { list ->
                                            indexMap[idx] = list.size
                                            list.add(Block.Text())
                                        }
                                        "web_search_tool_result" -> {
                                            val hits = mutableListOf<SearchHit>()
                                            cb.optJSONArray("content")?.let { arr ->
                                                for (i in 0 until arr.length()) {
                                                    val r = arr.optJSONObject(i) ?: continue
                                                    // 搜索失败时 content 里是 *_tool_result_error，不能当结果
                                                    if (r.optString("type") != "web_search_result") continue
                                                    hits.add(
                                                        SearchHit(
                                                            title = r.optString("title"),
                                                            url = r.optString("url"),
                                                            site = r.optString("site_name").orNullString(),
                                                            snippet = r.optString("snippet"),
                                                            date = r.optString("page_age").take(10),
                                                            icon = r.optString("site_icon").orNullString(),
                                                        )
                                                    )
                                                }
                                            }
                                            into.withBlocks { list ->
                                                indexMap[idx] = list.size
                                                val tid = cb.optString("tool_use_id")
                                                list.add(Block.Search(tid, hits).also {
                                                    it.raw = cb.toString()
                                                    it.rawToolUse = toolUseRaw[tid].orEmpty()
                                                })
                                            }
                                        }
                                        "web_fetch_tool_result" -> {
                                            val c = cb.optJSONObject("content")
                                            val inner = c?.optJSONObject("content")
                                            // 抓取失败时 content 是 error 对象，没有 url，不进上下文也不展示
                                            if (c?.optString("type") == "web_fetch_result") {
                                                val tid = cb.optString("tool_use_id")
                                                into.withBlocks { list ->
                                                    indexMap[idx] = list.size
                                                    list.add(
                                                        Block.Fetch(
                                                            toolUseId = tid,
                                                            url = c.optString("url").orEmpty(),
                                                            title = inner?.optString("title").orEmpty(),
                                                            publishedAt = c.optString("published_at")
                                                                .orNullString().orEmpty(),
                                                            signature = cb.optString("signature")
                                                                .orNullString().orEmpty(),
                                                            rawResult = cb.toString(),
                                                            rawToolUse = toolUseRaw[tid].orEmpty(),
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        "server_tool_use" -> {
                                            // 服务端调用工具的记录，UI 不展示，只保存原文
                                            cb.optString("id").takeIf { it.isNotBlank() }?.let {
                                                toolUseRaw[it] = cb.toString()
                                            }
                                        }
                                        "tool_use" -> {
                                            // 客户端工具调用：记下 id/name，input 后续用 delta 拼
                                            pendingTools[idx] = PendingTool(
                                                id = cb.optString("id"),
                                                name = cb.optString("name"),
                                            )
                                        }
                                    }
                                    onUpdate()
                                }

                                "content_block_delta" -> {
                                    val deltaIdx = ev.optInt("index")
                                    val d = ev.optJSONObject("delta") ?: continue
                                    // 工具参数是碎片字符串，必须拼接
                                    if (d.optString("type") == "input_json_delta") {
                                        pendingTools[deltaIdx]?.buf?.append(d.optString("partial_json"))
                                        continue
                                    }
                                    val pos = indexMap[deltaIdx] ?: continue
                                    into.withBlocks { list ->
                                        val block = list.getOrNull(pos) ?: return@withBlocks
                                        when (d.optString("type")) {
                                            "text_delta" ->
                                                if (block is Block.Text) block.text += d.optString("text")
                                            "thinking_delta" ->
                                                if (block is Block.Thinking) block.text += d.optString("thinking")
                                            "signature_delta" ->
                                                if (block is Block.Thinking) block.signature += d.optString("signature")
                                            "citations_delta" -> if (block is Block.Text) {
                                                d.optJSONObject("citation")?.let { c ->
                                                    block.citations.add(
                                                        Citation(
                                                            url = c.optString("url"),
                                                            title = c.optString("title"),
                                                            cited = c.optString("cited_text"),
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    onUpdate()
                                }

                                "content_block_stop" -> {
                                    val pos = indexMap[ev.optInt("index")] ?: continue
                                    into.withBlocks { list ->
                                        (list.getOrNull(pos) as? Block.Thinking)?.let {
                                            if (it.endedAt == 0L && it.startedAt > 0L) {
                                                it.endedAt = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                }

                                "message_start" -> {
                                    ev.optJSONObject("message")?.optJSONObject("usage")?.let { u ->
                                        into.usage = Usage(
                                            input = u.optInt("input_tokens"),
                                            output = u.optInt("output_tokens"),
                                            cacheRead = u.optInt("cache_read_input_tokens"),
                                        )
                                    }
                                }

                                "message_delta" -> {
                                    val d = ev.optJSONObject("delta")
                                    stopReason = d?.optString("stop_reason")?.takeIf { it.isNotBlank() }
                                    d?.optString("title")?.takeIf { it.isNotBlank() }?.let { remoteTitle = it }
                                    // 最终 output_tokens 在这里；input 沿用 message_start 的值
                                    ev.optJSONObject("usage")?.let { u ->
                                        val cur = into.usage ?: Usage()
                                        into.usage = cur.copy(
                                            input = if (u.has("input_tokens")) u.optInt("input_tokens") else cur.input,
                                            output = if (u.has("output_tokens")) u.optInt("output_tokens") else cur.output,
                                            cacheRead = if (u.has("cache_read_input_tokens")) {
                                                u.optInt("cache_read_input_tokens")
                                            } else {
                                                cur.cacheRead
                                            },
                                        )
                                    }
                                }

                                "message_stop" -> {
                                    finished = true
                                    break
                                }

                                "error" -> {
                                    val msg = ev.optJSONObject("error")?.optString("message")
                                        ?: ev.optString("message").ifBlank { "流式返回错误" }
                                    onError(msg)
                                    return
                                }
                            }
                        }

                        // 安全拒绝：已下发的思考与正文按协议丢弃，只保留工具块
                        if (stopReason == "refusal") {
                            into.withBlocks { list ->
                                list.removeAll { it is Block.Text || it is Block.Thinking }
                            }
                            into.status = "refusal"
                        } else {
                            into.status = if (finished) "done" else "stopped"
                        }
                        into.stopReason = stopReason
                        remoteTitle?.let(onTitle)
                        // 把本轮客户端工具调用交给外层循环
                        if (pendingTools.isNotEmpty() && toolSink != null) {
                            pendingTools.values.forEach { pt ->
                                toolSink.add(
                                    ToolUseCall(pt.id, pt.name, pt.buf.toString().ifBlank { "{}" })
                                )
                            }
                        }
                        onUpdate()
                        onDone()
                    } catch (e: Exception) {
                        if (!call.isCanceled()) onError(e.message ?: "流式读取失败")
                    }
                }
            }
        })
        return call
    }

    /**
     * 带客户端工具循环的对话：模型请求备忘工具 → 本地执行 → tool_result 回传 → 继续生成，
     * 单次回复最多 [MAX_TOOL_ROUNDS] 轮。
     */
    fun streamWithTools(
        settings: AppSettings,
        history: List<Msg>,
        question: String,
        attachments: List<Attachment>,
        into: Msg,
        memoEnabled: Boolean,
        memoStore: com.yg.dschat.memo.NoteStore?,
        web: com.yg.dschat.memo.WebClient? = null,
        onUpdate: () -> Unit,
        onTitle: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) {
        val messages = buildMessages(history, question, attachments, settings)
        val store = memoStore
        var round = 0

        fun fire() {
            if (round > MAX_TOOL_ROUNDS) {
                onDone()
                return
            }
            val calls = mutableListOf<ToolUseCall>()
            // 工具轮次不清空已有内容：思考、搜索、正文按发生顺序依次累积，
            // 和联网搜索的展示方式保持一致
            stream(
                settings = settings,
                history = history,
                question = question,
                attachments = attachments,
                into = into,
                prebuiltMessages = messages,
                toolSink = calls,
                memoEnabled = memoEnabled,
                onUpdate = onUpdate,
                onTitle = onTitle,
                onError = onError,
                onDone = {
                    val needRetry = into.stopReason == "tool_use" &&
                        calls.isNotEmpty() &&
                        memoEnabled &&
                        store != null &&
                        round < MAX_TOOL_ROUNDS
                    if (!needRetry) {
                        onDone()
                        return@stream
                    }
                    // assistant 带上本轮产生的全部内容块，再跟上 tool_use。
                    // 只传 tool_use 会丢掉本轮的搜索/浏览结果，模型下一轮就"看不见"已查到的内容。
                    val assistantBlocks = JSONArray()
                    val resultBlocks = JSONArray()
                    val activeStore = store ?: return@stream
                    into.blocks.forEach { b ->
                        when (b) {
                            is Block.Thinking -> {
                                // 思考模式下继续工具调用必须回传 thinking（含 signature），
                                // 否则服务端恢复不了推理上下文
                                if (b.text.isNotBlank() && b.signature.isNotBlank()) {
                                    assistantBlocks.put(
                                        JSONObject()
                                            .put("type", "thinking")
                                            .put("thinking", b.text)
                                            .put("signature", b.signature)
                                    )
                                }
                            }
                            is Block.Text -> if (b.text.isNotBlank()) {
                                assistantBlocks.put(JSONObject().put("type", "text").put("text", b.text))
                            }
                            is Block.Search -> {
                                // server_tool_use 与 web_search_tool_result 必须成对，缺一个就 400
                                if (b.rawToolUse.isNotBlank()) {
                                    runCatching { JSONObject(b.rawToolUse) }.getOrNull()?.let { assistantBlocks.put(it) }
                                }
                                if (b.raw.isNotBlank()) {
                                    runCatching { JSONObject(b.raw) }.getOrNull()?.let { assistantBlocks.put(it) }
                                }
                            }
                            is Block.Fetch -> {
                                if (b.rawToolUse.isNotBlank()) {
                                    runCatching { JSONObject(b.rawToolUse) }.getOrNull()?.let { assistantBlocks.put(it) }
                                }
                                if (b.rawResult.isNotBlank()) {
                                    runCatching { JSONObject(b.rawResult) }.getOrNull()?.let { assistantBlocks.put(it) }
                                }
                            }
                            else -> Unit
                        }
                    }
                    calls.forEach { c ->
                        val input = runCatching { JSONObject(c.inputJson) }.getOrDefault(JSONObject())
                        assistantBlocks.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", c.id)
                                .put("name", c.name)
                                .put("input", input)
                        )
                        val official = settings.baseUrl.trimEnd('/') to settings.apiKey
                        val res = runCatching {
                            if (web != null) {
                                com.yg.dschat.memo.ClientTools.execute(
                                    activeStore,
                                    web,
                                    c.name,
                                    input,
                                    official,
                                    history,
                                )
                            } else {
                                com.yg.dschat.memo.ClientTools.ToolResult(
                                    com.yg.dschat.memo.MemoTools.execute(activeStore, c.name, input)
                                )
                            }
                        }.getOrElse {
                            com.yg.dschat.memo.ClientTools.ToolResult("工具执行失败：${it.message}")
                        }
                        // 搜索在思考区留下痕迹，和官方联网搜索的展示一致
                        if (res.hits.isNotEmpty()) {
                            into.withBlocks { list ->
                                list.add(
                                    Block.Search(
                                        toolUseId = c.id,
                                        results = res.hits.map {
                                            SearchHit(
                                                title = it.title,
                                                url = it.url,
                                                site = runCatching { java.net.URI(it.url).host }
                                                    .getOrNull().orEmpty(),
                                                snippet = it.snippet,
                                                date = "",
                                            )
                                        }.toMutableList(),
                                    )
                                )
                            }
                        }
                        resultBlocks.put(
                            JSONObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", c.id)
                                .put("content", res.text)
                        )
                    }
                    messages.put(JSONObject().put("role", "assistant").put("content", assistantBlocks))
                    messages.put(JSONObject().put("role", "user").put("content", resultBlocks))
                    round++
                    fire()
                },
            )
        }

        fire()
    }

    companion object {
        /** 单次回复内允许的工具轮次上限 */
        const val MAX_TOOL_ROUNDS = 20

        /** 最近一次实际发出的请求体，仅用于排查问题 */
        @Volatile
        var lastPayload: String = ""
            private set


        private fun isUsable(a: Msg): Boolean =            a.status in setOf("done", "stopped") &&
                a.stopReason != "refusal" &&
                a.plainText().isNotBlank()

        /**
         * 格式：<date>,<weekday>,<location>,<platform>,<language>
         * 例：2026-11-17,星期一,中国,App,中文
         * weekday 用 language 对应的语言书写，language 必须是官方支持列表里的写法。
         */
        private fun reminderText(s: AppSettings): String {
            val now = Date()
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            val weekday = SimpleDateFormat("EEEE", Locale.SIMPLIFIED_CHINESE).format(now)
            val location = s.reminderPlace.trim()
            return "$date,$weekday,$location,App,中文"
        }

        /** 把服务端的 JSON 错误翻成一句人话，别把 400 原文糊满屏幕 */
        private fun readableError(code: Int, raw: String): String {
            val msg = runCatching {
                JSONObject(raw).optJSONObject("error")?.optString("message")
            }.getOrNull().orEmpty().ifBlank { raw }
            return when {
                msg.contains("latest_reminder", true) -> "请求参数有误（latest_reminder），请更新应用"
                code == 401 || msg.contains("invalid api key", true) || msg.contains("authentication", true) ->
                    "API Key 无效，请在设置里更换"
                code == 402 || msg.contains("insufficient", true) || msg.contains("balance", true) ->
                    "账户余额不足，请充值后重试"
                code == 429 || msg.contains("rate limit", true) -> "请求过于频繁，请稍后再试"
                code >= 500 -> "服务端繁忙，请稍后再试"
                msg.isBlank() -> "HTTP $code"
                else -> msg.take(160)
            }
        }

        /**
         * 按 reply.ts 的规则组装 messages：
         * 只保留「提问 + 有正文的回复」成对轮次；思考块与搜索块不回传；
         * 安全拒绝、失败、空回答的轮次整轮跳过；latest_reminder 只在内容变化时插一次。
         */
        fun buildMessages(
            history: List<Msg>,
            question: String,
            attachments: List<Attachment>,
            s: AppSettings,
        ): JSONArray {
            val out = JSONArray()
            val reminder = if (s.reminderOn) reminderText(s) else null
            var lastReminder: String? = null

            // 先收集合法的 user/assistant 对
            var pairs = ArrayList<Pair<Msg, Msg>>()
            var i = 0
            while (i + 1 < history.size) {
                val u = history[i]
                val a = history[i + 1]
                if (u.role == "user" && a.role == "assistant" && isUsable(a)) {
                    pairs.add(u to a)
                    i += 2
                } else {
                    i += 1
                }
            }

            // 压缩依据是服务端返回的真实 input_tokens（存在最近一条回答上），不做字符估算。
            // 做法是整轮丢弃最早的对话，不截断任何一条回答的内容。
            var dropped = 0
            if (s.compressEnabled && s.compressThreshold > 0) {
                val lastInput = pairs.lastOrNull()?.second?.usage?.input ?: 0
                if (lastInput > s.compressThreshold && pairs.size > 2) {
                    val keep = maxOf(2, (pairs.size * (s.compressThreshold.toDouble() / lastInput)).toInt())
                    if (keep < pairs.size) {
                        dropped = pairs.size - keep
                        pairs = ArrayList(pairs.takeLast(keep))
                    }
                }
            }

            if (dropped > 0) {
                out.put(
                    JSONObject().put("role", "user")
                        .put("content", "（前面还有 $dropped 轮对话，因上下文过长已省略）")
                )
                out.put(JSONObject().put("role", "assistant").put("content", "好的。"))
            }

            for ((u, a) in pairs) {
                if (reminder != null && reminder != lastReminder) {
                    out.put(JSONObject().put("role", "latest_reminder").put("content", reminder))
                    lastReminder = reminder
                }
                out.put(JSONObject().put("role", "user").put("content", userContent(u.plainText(), u.attachments)))
                out.put(JSONObject().put("role", "assistant").put("content", assistantContent(a)))
            }

            if (reminder != null && reminder != lastReminder) {
                out.put(JSONObject().put("role", "latest_reminder").put("content", reminder))
            }
            out.put(JSONObject().put("role", "user").put("content", userContent(question, attachments)))
            return out
        }

        /** 估算一段历史要占多少字符（中文约 1 字 ≈ 1 token） */
        fun estimateChars(history: List<Msg>): Int =
            history.sumOf { it.plainText().length + 40 }

        /** 最近一次真实 input_tokens，用于判断上下文是否已经过长 */
        fun lastInputTokens(history: List<Msg>): Int =
            history.lastOrNull { it.role == "assistant" }?.usage?.input ?: 0

        /** 有 read_link 抓取块时，assistant content 必须用块数组并把 signature 原样带回。 */
        private fun assistantContent(a: Msg): Any {
            val fetches = a.blocks
                .filterIsInstance<Block.Fetch>()
                .filter { it.signature.isNotBlank() || it.rawResult.isNotBlank() }
            val text = a.plainText()
            if (fetches.isEmpty()) return text
            val arr = JSONArray()
            fetches.forEach { f ->
                arr.put(rawOrBuiltToolUse(f))
                arr.put(rawOrBuiltResult(f))
            }
            if (text.isNotBlank()) arr.put(JSONObject().put("type", "text").put("text", text))
            return arr
        }

        /** 优先用服务端给的原始块，保证 id / input / caller 等字段完全一致 */
        private fun rawOrBuiltToolUse(f: Block.Fetch): JSONObject {
            if (f.rawToolUse.isNotBlank()) {
                runCatching { JSONObject(f.rawToolUse) }.getOrNull()?.let { return it }
            }
            return JSONObject()
                .put("type", "server_tool_use")
                .put("id", f.toolUseId)
                .put("name", "deepseek_read_url")
                .put("input", JSONObject().put("action", "read_link").put("url", f.url))
                .put("caller", JSONObject().put("type", "direct"))
                .put("kind", "read_link")
        }

        /** 同理：原始结果块里的加密正文和 signature 都在，自己拼容易漏字段 */
        private fun rawOrBuiltResult(f: Block.Fetch): JSONObject {
            if (f.rawResult.isNotBlank()) {
                runCatching { JSONObject(f.rawResult) }.getOrNull()?.let { return it }
            }
            return JSONObject()
                .put("type", "web_fetch_tool_result")
                .put("tool_use_id", f.toolUseId)
                .put(
                    "content",
                    JSONObject()
                        .put("type", "web_fetch_result")
                        .put("url", f.url)
                        .put(
                            "content",
                            JSONObject()
                                .put("type", "document")
                                .put(
                                    "source",
                                    JSONObject()
                                        .put("type", "text")
                                        .put("media_type", "text/plain")
                                        .put("data", "")
                                )
                                .put("title", f.title)
                        )
                        .put("published_at", f.publishedAt.ifBlank { null })
                )
                .put("kind", "read_link")
                .put("signature", f.signature)
        }

        /** 有附件时 content 用 block 数组，否则保持纯字符串。 */
        private fun userContent(text: String, atts: List<Attachment>): Any {
            val usable = atts.filter { !it.fileId.isNullOrBlank() }
            if (usable.isEmpty()) return text
            val arr = JSONArray()
            usable.forEach { a ->
                arr.put(
                    JSONObject()
                        .put("type", if (a.isImage) "image" else "document")
                        .put("source", JSONObject().put("type", "file").put("file_id", a.fileId))
                )
            }
            if (text.isNotBlank()) arr.put(JSONObject().put("type", "text").put("text", text))
            return arr
        }
    }
}
