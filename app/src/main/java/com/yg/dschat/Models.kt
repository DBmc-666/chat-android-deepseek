package com.yg.dschat

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一条消息里的最小内容块集合。搜索块只用于展示，不回传。 */
sealed class Block {
    data class Thinking(
        var text: String = "",
        var signature: String = "",
        var startedAt: Long = 0L,
        var endedAt: Long = 0L,
    ) : Block()
    data class Text(var text: String = "", val citations: MutableList<Citation> = mutableListOf()) : Block()
    data class Search(val toolUseId: String, val results: MutableList<SearchHit> = mutableListOf()) : Block()
    /** read_link / open_page 的抓取结果；signature 必须原样回传，否则下一轮网页正文拼不回来 */
    data class Fetch(
        val toolUseId: String,
        val url: String,
        val title: String,
        val publishedAt: String,
        val signature: String,
        /** 服务端原始 web_fetch_tool_result 块；非空时回传用它，避免自己拼出来的结构和服务端不一致 */
        val rawResult: String = "",
        /** 服务端原始 server_tool_use 块，必须和结果块成对回传 */
        val rawToolUse: String = "",
    ) : Block()
}

data class Citation(val url: String, val title: String, val cited: String)
data class SearchHit(
    val title: String,
    val url: String,
    val site: String,
    val snippet: String,
    val date: String = "",
    val icon: String = "",
)

data class Attachment(
    val uri: String,
    val name: String,
    val mime: String,
    val isImage: Boolean,
    var fileId: String? = null,
)

/** 一次调用的 token 用量，来自 SSE 的 message_start / message_delta */
data class Usage(
    val input: Int = 0,
    val output: Int = 0,
    val cacheRead: Int = 0,
) {
    val total: Int get() = input + output
}

data class Msg(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val blocks: MutableList<Block> = mutableListOf(),
    val attachments: MutableList<Attachment> = mutableListOf(),
    val at: Long = System.currentTimeMillis(),
    var status: String = "done",
    var stopReason: String? = null,
    var error: String? = null,
    var usage: Usage? = null,
    /** 历史版本快照（只在内存保留，重启后旧版本丢弃）。 */
    val versions: MutableList<List<Block>> = mutableListOf(),
    var versionIndex: Int = 0,
) {
    /** SSE 线程写 blocks、主线程读，两边都走这把锁，避免 ConcurrentModificationException */
    private val lockObj = Any()

    fun plainText(): String = synchronized(lockObj) {
        blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
    }

    fun blockSnapshot(): List<Block> = synchronized(lockObj) { blocks.toList() }

    fun withBlocks(f: (MutableList<Block>) -> Unit) = synchronized(lockObj) { f(blocks) }
}

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val messages: MutableList<Msg> = mutableListOf(),
    var updated: Long = System.currentTimeMillis(),
    var pinned: Boolean = false,
)

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-flash",
    val webSearch: Boolean = true,
    val deepThinking: Boolean = true,
    val safetyHigh: Boolean = true,
    val autoTitle: Boolean = true,
    val reminderOn: Boolean = true,
    val reminderPlace: String = "中国",
    val reminderLang: String = "zh-CN",
    val theme: String = "system",
    val fontScale: Float = 1f,
    val followSystemFont: Boolean = true,
    /** 语音转写完成后直接发送；false 则填进输入框并切回键盘，让用户自己改 */
    val voiceAutoSend: Boolean = false,
    /** 每条回答下方显示 token 消耗 */
    val showUsage: Boolean = false,
)

// ---------- JSON ----------

private fun JSONObject.putOrNull(k: String, v: String?) {
    put(k, if (v == null) JSONObject.NULL else v)
}

fun Citation.toJson() = JSONObject().put("url", url).put("title", title).put("cited", cited)
fun SearchHit.toJson() = JSONObject().put("title", title).put("url", url).put("site", site).put("snippet", snippet).put("date", date).put("icon", icon)

fun Block.toJson(): JSONObject = JSONObject().apply {
    when (val b = this@toJson) {
        is Block.Text -> {
            put("type", "text")
            put("text", b.text)
            put("citations", JSONArray().also { a -> b.citations.forEach { a.put(it.toJson()) } })
        }
        is Block.Thinking -> {
            put("type", "thinking")
            put("text", b.text)
            put("signature", b.signature)
        }
        is Block.Search -> {
            put("type", "search")
            put("toolUseId", b.toolUseId)
            put("results", JSONArray().also { a -> b.results.forEach { a.put(it.toJson()) } })
        }
        is Block.Fetch -> {
            put("type", "fetch")
            put("toolUseId", b.toolUseId)
            put("url", b.url)
            put("title", b.title)
            put("publishedAt", b.publishedAt)
            put("signature", b.signature)
            if (b.rawResult.isNotEmpty()) put("rawResult", b.rawResult)
            if (b.rawToolUse.isNotEmpty()) put("rawToolUse", b.rawToolUse)
        }
    }
}

fun blockFromJson(o: JSONObject): Block? = when (o.optString("type")) {
    "text" -> Block.Text(
        text = o.optString("text"),
        citations = o.optJSONArray("citations")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                Citation(it.optString("url"), it.optString("title"), it.optString("cited"))
            }.toMutableList()
        } ?: mutableListOf(),
    )
    "thinking" -> Block.Thinking(o.optString("text"), o.optString("signature"))
    "search" -> Block.Search(
        toolUseId = o.optString("toolUseId"),
        results = o.optJSONArray("results")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                SearchHit(it.optString("title"), it.optString("url"), it.optString("site"), it.optString("snippet"), it.optString("date"), it.optString("icon"))
            }.toMutableList()
        } ?: mutableListOf(),
    )
    "fetch" -> Block.Fetch(
        toolUseId = o.optString("toolUseId"),
        url = o.optString("url"),
        title = o.optString("title"),
        publishedAt = o.optString("publishedAt"),
        signature = o.optString("signature"),
        rawResult = o.optString("rawResult"),
        rawToolUse = o.optString("rawToolUse"),
    )
    else -> null
}

fun Attachment.toJson() = JSONObject()
    .put("uri", uri)
    .put("name", name)
    .put("mime", mime)
    .put("isImage", isImage)
    .putOrNull("fileId", fileId)

fun attachmentFromJson(o: JSONObject) = Attachment(
    uri = o.optString("uri"),
    name = o.optString("name"),
    mime = o.optString("mime"),
    isImage = o.optBoolean("isImage"),
    fileId = o.optString("fileId").takeIf { it.isNotBlank() && it != "null" },
)

fun Msg.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("role", role)
    put("at", at)
    put("status", status)
    putOrNull("stopReason", stopReason)
    putOrNull("error", error)
    put("blocks", JSONArray().also { a -> blocks.forEach { a.put(it.toJson()) } })
    put("attachments", JSONArray().also { a -> attachments.forEach { a.put(it.toJson()) } })
    put(
        "versions",
        JSONArray().also { outer ->
            versions.forEach { v ->
                outer.put(JSONArray().also { inner -> v.forEach { inner.put(it.toJson()) } })
            }
        },
    )
    put("versionIndex", versionIndex)
    usage?.let {
        put("usage", JSONObject().put("input", it.input).put("output", it.output).put("cacheRead", it.cacheRead))
    }
}

fun msgFromJson(o: JSONObject): Msg = Msg(
    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
    role = o.optString("role", "user"),
    blocks = o.optJSONArray("blocks")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { blockFromJson(it) } }.toMutableList()
    } ?: mutableListOf(),
    attachments = o.optJSONArray("attachments")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { attachmentFromJson(it) } }.toMutableList()
    } ?: mutableListOf(),
    at = o.optLong("at", System.currentTimeMillis()),
    status = o.optString("status", "done"),
    stopReason = o.optString("stopReason").takeIf { it.isNotBlank() && it != "null" },
    error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
    versions = o.optJSONArray("versions")?.let { outer ->
        (0 until outer.length()).mapNotNull { i ->
            outer.optJSONArray(i)?.let { inner ->
                (0 until inner.length()).mapNotNull { j ->
                    inner.optJSONObject(j)?.let { blockFromJson(it) }
                }
            }
        }.toMutableList()
    } ?: mutableListOf(),
    versionIndex = o.optInt("versionIndex", 0),
    usage = o.optJSONObject("usage")?.let {
        Usage(
            input = it.optInt("input"),
            output = it.optInt("output"),
            cacheRead = it.optInt("cacheRead"),
        )
    },
)

fun Session.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("updated", updated)
    put("pinned", pinned)
    put("messages", JSONArray().also { a -> messages.forEach { a.put(it.toJson()) } })
}

fun sessionFromJson(o: JSONObject): Session = Session(
    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
    title = o.optString("title", "新对话"),
    messages = o.optJSONArray("messages")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { msgFromJson(it) } }.toMutableList()
    } ?: mutableListOf(),
    updated = o.optLong("updated", System.currentTimeMillis()),
    pinned = o.optBoolean("pinned", false),
)
