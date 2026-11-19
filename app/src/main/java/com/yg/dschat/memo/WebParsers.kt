package com.yg.dschat.memo

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URI

data class WebHit(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * 搜索结果与网页正文的解析。
 *
 * 全部做成纯函数，不碰网络：这样解析逻辑可以在 JVM 上直接用固定报文测，
 * 不用为了测一个 CSS 选择器去 mock OkHttp（见 WebParsersTest）。
 */
object WebParsers {

    // ── DuckDuckGo HTML ────────────────────────────────────────────────

    fun parseDuckDuckGo(html: String, limit: Int): List<WebHit> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val out = ArrayList<WebHit>()
        doc.select("div.result, div.web-result").forEach { node ->
            if (out.size >= limit) return@forEach
            val a = node.selectFirst("a.result__a") ?: return@forEach
            val href = cleanDdgUrl(a.attr("href"))
            if (href.isBlank()) return@forEach
            val snippet = node.selectFirst(".result__snippet")?.text().orEmpty()
            out.add(WebHit(a.text().trim(), href, snippet.trim()))
        }
        if (out.isEmpty()) {
            // 有些版本直接把结果放在 a.result__a 下，不包 web-result
            doc.select("a.result__a").forEach { a ->
                if (out.size >= limit) return@forEach
                val href = cleanDdgUrl(a.attr("href"))
                if (href.isNotBlank()) out.add(WebHit(a.text().trim(), href, ""))
            }
        }
        return out
    }

    /** DDG 的跳转链接形如 //duckduckgo.com/l/?uddg=<encoded>，要还原成真实地址。 */
    fun cleanDdgUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (url.startsWith("//")) url = "https:$url"
        val marker = "uddg="
        val at = url.indexOf(marker)
        if (at >= 0) {
            val encoded = url.substring(at + marker.length).substringBefore('&')
            url = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(url)
        }
        return if (url.startsWith("http://") || url.startsWith("https://")) url else ""
    }

    // ── SearXNG ────────────────────────────────────────────────────────

    fun parseSearxng(json: String, limit: Int): List<WebHit> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<WebHit>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                WebHit(
                    title = o.optString("title").ifBlank { url },
                    url = url,
                    snippet = o.optString("content"),
                )
            )
        }
        return out
    }

    // ── Tavily ─────────────────────────────────────────────────────────

    fun parseTavily(json: String, limit: Int): List<WebHit> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<WebHit>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                WebHit(
                    title = o.optString("title").ifBlank { url },
                    url = url,
                    snippet = o.optString("content"),
                )
            )
        }
        return out
    }

    // ── Brave ──────────────────────────────────────────────────────────

    fun parseBrave(json: String, limit: Int): List<WebHit> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<WebHit>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val o = arr.optJSONObject(i) ?: return emptyList()
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                WebHit(
                    title = o.optString("title").ifBlank { url },
                    url = url,
                    snippet = o.optString("description"),
                )
            )
        }
        return out
    }

    // ── 正文抽取 ───────────────────────────────────────────────────────

    /**
     * 去掉脚本/样式/导航/页脚，取正文最大的那一块。
     * 返回 (标题, 正文)。正文里保留换行，方便模型分页阅读。
     */
    fun extractMain(html: String): Pair<String, String> = extractFromDoc(Jsoup.parse(html))

    /**
     * 直接从字节解析：jsoup 会按 <meta charset> 自动判编码，
     * 中文站点用 UTF-8 硬解会出乱码。
     */
    fun extractMainFromBytes(bytes: ByteArray, baseUrl: String): Pair<String, String> =
        extractFromDoc(Jsoup.parse(java.io.ByteArrayInputStream(bytes), null, baseUrl))

    private fun extractFromDoc(doc: Document): Pair<String, String> {
        val title = (doc.title().ifBlank { doc.selectFirst("h1")?.text().orEmpty() }).trim()
        doc.select("script, style, noscript, iframe, svg, nav, header, footer, aside, form").remove()
        doc.select("[aria-hidden=true], .nav, .sidebar, .footer, .menu, .ad, .ads, .advert").remove()

        val candidates = listOf("article", "main", "[role=main]", "#content", ".content", ".post", ".article")
        var best = ""
        for (sel in candidates) {
            val el = doc.selectFirst(sel) ?: continue
            val text = blockText(el)
            if (text.length > best.length) best = text
        }
        if (best.length < 200) best = blockText(doc.body())
        if (best.isBlank()) best = doc.text()
        return title to best.trim()
    }

    private fun blockText(el: org.jsoup.nodes.Element): String {
        val sb = StringBuilder()
        el.select("p, li, h1, h2, h3, h4, pre, blockquote, br").forEach { node ->
            val t = node.wholeText().trim()
            if (t.isNotEmpty()) sb.append(t).append('\n')
        }
        if (sb.isBlank()) return el.text().trim()
        return sb.toString()
    }

    // ── SSRF 防护 ──────────────────────────────────────────────────────

    /**
     * 只允许 http/https，且不允许打到本机或内网。
     * 模型可能被网页内容诱导去戳 127.0.0.1 或云元数据地址，这层是硬拦截。
     */
    fun checkUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return "URL 为空"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "只支持 http/https"
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return "URL 不合法"
        if (host.isBlank()) return "URL 缺少域名"
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return "不允许访问本机地址"
        }
        val ip = parseIpv4(host)
        if (ip != null) {
            val (a, b) = ip
            val private = a == 10 || a == 127 || a == 0 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
            if (private) return "不允许访问内网地址"
        }
        if (host.contains(':')) return "不支持 IPv6 直连"
        return null
    }

    private fun parseIpv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it < 0 || it > 255 }) return null
        return nums[0] to nums[1]
    }
}
