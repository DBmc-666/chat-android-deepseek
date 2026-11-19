package com.yg.dschat.memo

import org.json.JSONArray
import org.json.JSONObject

/**
 * 客户端工具集（备忘 + 联网）。
 *
 * 只在「备忘开启 + 客户端工具模式」下声明。这个模式下完全不用 DeepSeek 的服务端联网工具，
 * 因为官方限制：other tools cannot be used together with deepseek_web_search / deepseek_read_url yet。
 * 代价是联网换成外部搜索源，好处是助手能自己检索和写入备忘。
 */
object ClientTools {

    const val WEB_SEARCH = "web_search"
    const val WEB_FETCH = "web_fetch"

    private const val MAX_RESULT_CHARS = 6000

    fun defs(webEnabled: Boolean): JSONArray = JSONArray().apply {
        if (webEnabled) {
            put(
                JSONObject()
                    .put("name", WEB_SEARCH)
                    .put(
                        "description",
                        "联网搜索，返回标题、链接和摘要。需要查实时信息、新闻、" +
                            "你不确定的最新事实时调用。"
                    )
                    .put(
                        "input_schema",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("query", JSONObject().put("type", "string").put("description", "搜索词"))
                                    .put("count", JSONObject().put("type", "integer").put("description", "结果条数，默认 5"))
                            )
                            .put("required", JSONArray().put("query"))
                    )
            )
            put(
                JSONObject()
                    .put("name", WEB_FETCH)
                    .put("description", "抓取一个网页的正文。先用 web_search 拿到链接，再来读全文。")
                    .put(
                        "input_schema",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject().put("url", JSONObject().put("type", "string").put("description", "网页地址"))
                            )
                            .put("required", JSONArray().put("url"))
                    )
            )
        }
        val memoDefs = MemoTools.defs()
        for (i in 0 until memoDefs.length()) put(memoDefs.getJSONObject(i))
    }

    /** 工具执行结果：text 回传给模型，hits 用于在思考区留下痕迹 */
    data class ToolResult(val text: String, val hits: List<WebHit> = emptyList())

    fun execute(
        store: NoteStore,
        web: WebClient,
        name: String,
        input: JSONObject,
        /** 非空时走官方搜索 API（独立接口，可与自定义工具混用） */
        official: Pair<String, String>? = null,
    ): ToolResult = when (name) {
        WEB_SEARCH -> search(web, input, official)
        WEB_FETCH -> ToolResult(fetch(web, input))
        else -> ToolResult(MemoTools.execute(store, name, input))
    }

    private fun search(
        web: WebClient,
        input: JSONObject,
        official: Pair<String, String>?,
    ): ToolResult {
        val query = input.optString("query").trim()
        if (query.isBlank()) return ToolResult("搜索词为空")
        val n = input.optInt("count", 5).coerceIn(1, 8)
        if (official == null) return ToolResult("搜索不可用：缺少接口地址")
        val hits = runCatching {
            web.searchOfficial(official.first, official.second, query, n)
        }.getOrElse {
            return ToolResult("搜索失败：${it.message}")
        }
        if (hits.isEmpty()) return ToolResult("没有搜到结果。")
        val sb = StringBuilder("共 ${hits.size} 条：\n")
        hits.forEachIndexed { i, h ->
            sb.append("\n${i + 1}. ${h.title}\n   ${h.url}\n   ${h.snippet.take(600)}\n")
        }
        return ToolResult(cap(sb.toString()), hits)
    }

    private fun fetch(web: WebClient, input: JSONObject): String {
        val url = input.optString("url").trim()
        if (url.isBlank()) return "缺少 url"
        val doc = runCatching { web.fetch(url) }.getOrElse {
            return "抓取失败：${it.message}"
        }
        return cap("《${doc.title}》\n$url\n\n${doc.text}")
    }

    private fun cap(s: String): String =
        if (s.length <= MAX_RESULT_CHARS) s else s.take(MAX_RESULT_CHARS) + "\n…（内容过长，已截断）"
}
