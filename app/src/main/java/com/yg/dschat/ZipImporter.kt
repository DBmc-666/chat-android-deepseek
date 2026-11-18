package com.yg.dschat

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.zip.ZipInputStream
import java.util.UUID

/**
 * 导入 DeepSeek 网页端 / App 导出的历史对话 ZIP。
 * 结构：manifest.json(zip 首个条目) → user.json(忽略) → chat_sessions/{id}.json → chat_session_index.json → 图片
 * 消息树靠 parent_id 串起来，用 current_message_id 回溯出线性会话。
 */
object ZipImporter {

    data class Result(val sessions: List<Session>, val error: String?)

    private const val SUPPORTED_VERSION = 3

    fun import(ctx: Context, uri: Uri): Result = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zin ->
                var manifestOk = false
                val out = mutableListOf<Session>()
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == "manifest.json" -> {
                            val obj = JSONObject(zin.readBytes().toString(Charsets.UTF_8))
                            val v = obj.optInt("export_format_version", -1)
                            if (v != SUPPORTED_VERSION) {
                                return Result(emptyList(), "导出文件版本 $v 不受支持（需要 $SUPPORTED_VERSION）")
                            }
                            manifestOk = true
                        }
                        name.startsWith("chat_sessions/") && name.endsWith(".json") -> {
                            val obj = runCatching {
                                JSONObject(zin.readBytes().toString(Charsets.UTF_8))
                            }.getOrNull()
                            if (obj != null) convertSession(obj)?.let { out.add(it) }
                        }
                        else -> Unit
                    }
                    runCatching { zin.closeEntry() }
                }
                if (!manifestOk) {
                    Result(emptyList(), "不是 DeepSeek 导出的历史对话 ZIP")
                } else {
                    Result(out, null)
                }
            }
        } ?: Result(emptyList(), "无法读取文件")
    }.getOrElse { Result(emptyList(), it.message ?: "导入失败") }

    private fun parseTime(s: String?): Long =
        runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun convertSession(o: JSONObject): Session? {
        val raw = o.optJSONArray("messages") ?: return null
        val byId = HashMap<Int, JSONObject>()
        for (i in 0 until raw.length()) {
            val m = raw.optJSONObject(i) ?: continue
            byId[m.optInt("id")] = m
        }
        if (byId.isEmpty()) return null

        // 从 current_message_id 回溯出主线
        val chain = ArrayDeque<JSONObject>()
        var cur: Int? = o.optInt("current_message_id", -1).takeIf { it >= 0 }
        val guard = HashSet<Int>()
        while (cur != null && byId.containsKey(cur) && guard.add(cur)) {
            val m = byId[cur]!!
            chain.addFirst(m)
            cur = if (m.isNull("parent_id")) null else m.optInt("parent_id", -1).takeIf { it >= 0 }
        }
        if (chain.isEmpty()) {
            for (i in 0 until raw.length()) raw.optJSONObject(i)?.let { chain.addLast(it) }
        }

        val messages = mutableListOf<Msg>()
        chain.forEach { m ->
            val role = if (m.optString("role") == "USER") "user" else "assistant"
            val blocks = mutableListOf<Block>()
            val frags = m.optJSONArray("fragments") ?: JSONArray()
            for (i in 0 until frags.length()) {
                val f = frags.optJSONObject(i) ?: continue
                when (f.optString("type")) {
                    "REQUEST", "RESPONSE" -> {
                        val text = f.optString("content")
                        if (text.isNotBlank()) blocks.add(Block.Text(text))
                    }
                    "THINK" -> {
                        val text = f.optString("content")
                        if (text.isNotBlank()) {
                            val secs = f.optInt("elapsed_secs", 0)
                            blocks.add(
                                Block.Thinking(
                                    text = text,
                                    startedAt = 0L,
                                    endedAt = if (secs > 0) secs * 1000L else 0L,
                                )
                            )
                        }
                    }
                    "TOOL_SEARCH" -> {
                        val hits = mutableListOf<SearchHit>()
                        f.optJSONArray("results")?.let { arr ->
                            for (j in 0 until arr.length()) {
                                val r = arr.optJSONObject(j) ?: continue
                                hits.add(
                                    SearchHit(
                                        title = r.optString("title"),
                                        url = r.optString("url"),
                                        site = r.optString("site_name"),
                                        snippet = r.optString("snippet"),
                                        date = r.optString("published_at").take(10),
                                        icon = r.optString("site_icon"),
                                    )
                                )
                            }
                        }
                        if (hits.isNotEmpty()) blocks.add(Block.Search("import-${UUID.randomUUID()}", hits))
                    }
                    "READ_LINK" -> {
                        f.optJSONArray("results")?.let { arr ->
                            for (j in 0 until arr.length()) {
                                val r = arr.optJSONObject(j) ?: continue
                                blocks.add(
                                    Block.Fetch(
                                        toolUseId = "import-${UUID.randomUUID()}",
                                        url = r.optString("url"),
                                        title = r.optString("title"),
                                        publishedAt = "",
                                        signature = r.optString("signature"),
                                    )
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
            messages.add(
                Msg(
                    role = role,
                    blocks = blocks,
                    at = parseTime(m.optString("inserted_at")),
                    status = if (m.optString("status") == "FINISHED") "done" else "done",
                )
            )
        }
        if (messages.isEmpty()) return null

        return Session(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "导入的对话",
            messages = messages,
            updated = parseTime(o.optString("updated_at")),
            pinned = o.optBoolean("pinned", false),
        )
    }
}
