package com.yg.dschat.memo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class FetchedDoc(
    val id: String,
    val url: String,
    val title: String,
    val text: String,
)

/**
 * 联网能力：搜索 + 抓正文 + 本地缓存。
 *
 * 只做「把网页变成文本」这一件事，页面解析全部交给 [WebParsers]（纯函数、可单测）。
 * 抓回来的正文按 url 哈希落盘，模型可以分页读，不用反复请求。
 */
class WebClient(
    private val cacheDir: File,
    /** duckduckgo / searxng */
    var provider: String = "duckduckgo",
    /** SearXNG 实例地址，provider 为 searxng 时使用 */
    var endpoint: String = "",
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val ua =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // ── 搜索 ───────────────────────────────────────────────────────────

    fun search(query: String, count: Int): List<WebHit> {
        val q = query.trim()
        if (q.isEmpty()) throw IOException("搜索词为空")
        val n = count.coerceIn(1, 8)
        return when (provider.lowercase()) {
            "searxng" -> searxng(q, n)
            else -> duckduckgo(q, n)
        }
    }

    private fun duckduckgo(query: String, n: Int): List<WebHit> {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", ua)
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        val html = execute(req)
        val hits = WebParsers.parseDuckDuckGo(html, n)
        if (hits.isEmpty()) throw IOException("没有搜到结果（DuckDuckGo 可能限流，换一个搜索源试试）")
        return hits
    }

    private fun searxng(query: String, n: Int): List<WebHit> {
        val base = endpoint.trim().trimEnd('/')
        if (base.isBlank()) throw IOException("先在设置里填 SearXNG 地址")
        val url = "$base/search?format=json&q=" + URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", ua)
            .get()
            .build()
        val json = execute(req)
        val hits = WebParsers.parseSearxng(json, n)
        if (hits.isEmpty()) throw IOException("SearXNG 没有返回结果，检查地址与 format=json 是否开启")
        return hits
    }



    // ── 抓正文 ─────────────────────────────────────────────────────────

    fun fetch(url: String): FetchedDoc {
        WebParsers.checkUrl(url)?.let { throw IOException(it) }
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", ua)
            .addHeader("Accept", "text/html,application/xhtml+xml")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()

        val bytes = http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("响应为空")
            val len = body.contentLength()
            if (len > MAX_BYTES) throw IOException("页面太大（${len / 1024}KB），跳过")
            body.bytes()
        }
        val (title, text) = WebParsers.extractMainFromBytes(bytes, url)
        if (text.isBlank()) throw IOException("没解析出正文（可能是纯 JS 页面）")

        val doc = FetchedDoc(hash(url), url, title.ifBlank { url }, text)
        runCatching { save(doc) }
        return doc
    }

    fun readCached(docId: String, offset: Int, limit: Int): FetchedDoc? {
        val text = runCatching { File(cacheDir, "$docId.txt").readText() }.getOrNull() ?: return null
        val meta = runCatching { File(cacheDir, "$docId.meta").readText() }.getOrNull().orEmpty()
        val lines = meta.split('\n')
        val url = lines.getOrNull(0).orEmpty()
        val title = lines.getOrNull(1).orEmpty()
        val start = offset.coerceIn(0, text.length)
        val end = (start + limit).coerceAtMost(text.length)
        return FetchedDoc(docId, url, title, text.substring(start, end))
    }

    fun cachedLength(docId: String): Int =
        runCatching { File(cacheDir, "$docId.txt").readText().length }.getOrDefault(0)

    /** 7 天前的抓取缓存清掉，避免长期堆积。 */
    fun cleanupCache(days: Long = 7) {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
        runCatching {
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) f.delete()
            }
        }
    }

    private fun save(doc: FetchedDoc) {
        cacheDir.mkdirs()
        File(cacheDir, "${doc.id}.txt").writeText(doc.text)
        File(cacheDir, "${doc.id}.meta").writeText("${doc.url}\n${doc.title}")
    }

    private fun hash(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun execute(req: Request): String {
        return http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException(
                    when (res.code) {
                        401, 403 -> "搜索接口鉴权失败（HTTP ${res.code}）"
                        429 -> "搜索接口限流（HTTP 429），稍后再试或换搜索源"
                        else -> "搜索失败（HTTP ${res.code}）"
                    }
                )
            }
            res.body?.string() ?: ""
        }
    }

    companion object {
        private const val MAX_BYTES = 4L * 1024 * 1024
    }
}
