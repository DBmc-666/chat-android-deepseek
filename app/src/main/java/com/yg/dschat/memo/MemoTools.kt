package com.yg.dschat.memo

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备忘工具：声明 + 本地执行。
 *
 * 走 Anthropic Messages 的客户端工具（tool_use / tool_result），
 * 和 deepseek_web_search 那种服务端执行的工具不是一回事。
 *
 * 产品红线：助手只能「弃置」，没有物理删除入口。
 */
object MemoTools {

    const val NAME_SEARCH = "memo_search"
    const val NAME_READ = "memo_read"
    const val NAME_SAVE = "memo_save"

    /** 单条工具结果上限，防止一整篇长文把上下文撑爆 */
    private const val MAX_RESULT_CHARS = 6000

    /**
     * 只返回写入工具。
     *
     * 服务端限制：同一个请求里声明了客户端工具后，`deepseek_web_search` 这类服务端工具会失效，
     * 模型看不到它们。实测确认过，所以检索改为客户端预检索后注入上下文，
     * 工具面只留一个写入口。
     */
    fun writeOnlyDefs(): JSONArray = JSONArray().apply {
        put(
            JSONObject()
                .put("name", NAME_SAVE)
                .put(
                    "description",
                    "把一段内容存进用户的备忘库。不传 id 是新建；传 id 是改写该条。" +
                        "用户说「记一下」「帮我存起来」「记住这个」时调用。"
                )
                .put(
                    "input_schema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string").put("description", "标题，简短"))
                                .put("content", JSONObject().put("type", "string").put("description", "正文"))
                                .put("id", JSONObject().put("type", "string").put("description", "要改写的备忘 id，新建时不传"))
                        )
                        .put("required", JSONArray().put("title").put("content"))
                )
        )
    }

    fun defs(): JSONArray = JSONArray().apply {
        put(
            JSONObject()
                .put("name", NAME_SEARCH)
                .put(
                    "description",
                    "在用户的备忘库里检索。返回匹配条目的 id、标题和命中片段。" +
                        "回答用户关于「之前记过什么」「我的笔记里」这类问题前先用它。"
                )
                .put(
                    "input_schema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "query",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "检索词，用空格分隔多个词")
                                )
                                .put(
                                    "limit",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "最多返回几条，默认 8")
                                )
                        )
                        .put("required", JSONArray().put("query"))
                )
        )
        put(
            JSONObject()
                .put("name", NAME_READ)
                .put("description", "读取某条备忘的完整内容。先用 memo_search 拿到 id，再用它读全文。")
                .put(
                    "input_schema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "id",
                                JSONObject().put("type", "string").put("description", "备忘 id")
                            )
                        )
                        .put("required", JSONArray().put("id"))
                )
        )
        put(
            JSONObject()
                .put("name", NAME_SAVE)
                .put(
                    "description",
                    "把一段内容存进备忘库。不传 id 是新建；传 id 是改写该条。" +
                        "用户说「记一下」「帮我存起来」「记住这个」时调用。"
                )
                .put(
                    "input_schema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string").put("description", "标题，简短"))
                                .put("content", JSONObject().put("type", "string").put("description", "正文"))
                                .put("id", JSONObject().put("type", "string").put("description", "要改写的备忘 id，新建时不传"))
                        )
                        .put("required", JSONArray().put("title").put("content"))
                )
        )
    }

    /** 执行一次工具调用，返回给模型的文本结果 */
    fun execute(store: NoteStore, name: String, input: JSONObject): String = when (name) {
        NAME_SEARCH -> search(store, input)
        NAME_READ -> read(store, input)
        NAME_SAVE -> save(store, input)
        else -> "未知工具：$name"
    }

    private fun search(store: NoteStore, input: JSONObject): String {
        val query = input.optString("query").trim()
        if (query.isBlank()) return "检索词为空"
        val limit = input.optInt("limit", 8).coerceIn(1, 20)
        val hits = runCatching { store.queryNotes(needle = query, limit = limit) }.getOrElse {
            return "检索失败：${it.message}"
        }
        if (hits.isEmpty()) return "没有找到与「$query」相关的备忘。"
        val arr = JSONArray()
        hits.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.note.id)
                    .put("title", h.note.title)
                    .put("snippet", h.snippet)
            )
        }
        return cap("共 ${hits.size} 条：\n$arr")
    }

    private fun read(store: NoteStore, input: JSONObject): String {
        val id = input.optString("id").trim()
        if (id.isBlank()) return "缺少 id"
        val note = runCatching { store.note(id) }.getOrNull() ?: return "找不到 id 为 $id 的备忘。"
        if (note.isAbandoned || note.isDeleted) return "该条目已被移出备忘库。"
        return cap("《${note.title}》\n\n${note.body}")
    }

    private fun save(store: NoteStore, input: JSONObject): String {
        val title = input.optString("title").trim()
        val content = input.optString("content").trim()
        if (title.isBlank() || content.isBlank()) return "标题和正文都不能为空"
        val id = input.optString("id").trim()

        if (id.isNotBlank()) {
            val old = runCatching { store.note(id) }.getOrNull() ?: return "找不到 id 为 $id 的备忘。"
            if (old.isAbandoned || old.isDeleted) return "该条目已被移出备忘库，不能改写。"
            val saved = runCatching {
                store.saveContent(id = id, title = title, body = content, origin = "ai:memo_save")
            }.getOrNull() ?: return "改写失败"
            return "已更新备忘《${saved.title}》（id=${saved.id}）"
        }

        // 新建：同名拦截，避免模型反复堆同名条目
        val dup = runCatching { store.existsTitle(title, null) }.getOrNull()
        if (dup != null) {
            return "已存在同名备忘《${dup.title}》（id=${dup.id}）。如需修改请用 memo_save 并带上这个 id。"
        }
        val created = runCatching {
            store.createNote(title = title, body = content, keywords = emptyList())
        }.getOrNull() ?: return "保存失败"
        return "已保存备忘《${created.title}》（id=${created.id}）"
    }

    private fun cap(s: String): String =
        if (s.length <= MAX_RESULT_CHARS) s else s.take(MAX_RESULT_CHARS) + "\n…（内容过长，已截断）"
}
