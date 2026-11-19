package com.yg.dschat.memo

import java.text.Normalizer
import java.util.Locale

/**
 * 归一化 + 分词 + 查询构造。
 *
 * 依据 docs/03-数据模型.md 与 docs/06-开源参考.md 结论 1：
 * FTS4 的 simple 分词器不切中文，所以由我们自己把文本切成 bigram 写进索引列，
 * 查询时也切成 bigram，并用**短语查询**（不是 AND）保证顺序。
 *
 * 纯 Kotlin，无 Android 依赖，可直接跑 JVM 单测。
 */
object TextIndex {

    /** 归一化：NFKC → 小写 → 假名折叠 → 去 Markdown 标记 → 空白折叠。索引与查询必须共用。 */
    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""

        // 1) 全角/半角、兼容字符统一
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        // 2) 小写
        s = s.lowercase(Locale.ROOT)
        // 3) 片假名折叠成平假名：さーばー 能搜到 サーバー
        s = foldKana(s)
        // 4) 去 Markdown 标记，但保留正文
        s = stripMarkdown(s)
        // 5) 空白折叠，换行保留为词边界
        s = s.replace('\u00a0', ' ').replace('\u3000', ' ')
        s = s.replace(Regex("[ \t]+"), " ")
        s = s.replace(Regex(" *\n *"), "\n")
        return s.trim()
    }

    private fun foldKana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            // ァ..ヶ (30A1..30F6) → ぁ..ゖ
            sb.append(if (c in '\u30a1'..'\u30f6') (c.code - 0x60).toChar() else c)
        }
        return sb.toString()
    }

    private fun stripMarkdown(s: String): String {
        var out = s
        // 代码块整体丢弃（避免代码里的符号污染索引）
        out = out.replace(Regex("```[\\s\\S]*?```"), " ")
        // 图片/链接：[文本](url) / ![alt](url) → 文本
        out = out.replace(Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        // 引用式链接 [文本][ref] → 文本
        out = out.replace(Regex("\\[([^\\]]*)\\]\\[[^\\]]*\\]"), "$1")
        // 行首标记：标题 / 引用 / 列表
        out = out.replace(Regex("(?m)^[ \\t]*(#{1,6}|>|[-+*]|\\d+[.)])[ \\t]+"), " ")
        // 行内强调与行内代码符号：直接抹掉而不是换成空格，
        // 否则 屋根の**防水**工事 会被切成「屋根の / 防水 / 工事」，`防水工事` 就搜不到了
        out = out.replace(Regex("[`*_~#]+"), "")
        // 剩余方括号/圆括号结构符号不影响分词，交给分词器
        return out
    }

    fun isCjk(c: Char): Boolean =
        c in '\u3400'..'\u4dbf' ||            // CJK 扩展 A
            c in '\u4e00'..'\u9fff' ||        // CJK 基本区
            c in '\u3040'..'\u30ff' ||        // 平假名 + 片假名
            c in '\uac00'..'\ud7af'           // 谚文

    fun hasCjk(s: String): Boolean = s.any { isCjk(it) }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() && !isCjk(c)

    /**
     * 分词：CJK 段 → 重叠 bigram；拉丁/数字段 → 整词。
     * 返回的 token 列表可直接拼成 FTS4 索引列（空格分隔）。
     */
    fun tokens(norm: String): List<String> {
        if (norm.isEmpty()) return emptyList()
        val out = ArrayList<String>(norm.length)
        var i = 0
        while (i < norm.length) {
            val c = norm[i]
            when {
                isCjk(c) -> {
                    var j = i
                    while (j < norm.length && isCjk(norm[j])) j++
                    val seg = norm.substring(i, j)
                    if (seg.length == 1) {
                        out.add(seg)
                    } else {
                        for (k in 0..seg.length - 2) out.add(seg.substring(k, k + 2))
                    }
                    i = j
                }
                isWordChar(c) -> {
                    var j = i
                    while (j < norm.length && isWordChar(norm[j])) j++
                    out.add(norm.substring(i, j))
                    i = j
                }
                else -> i++
            }
        }
        return out
    }

    /** 索引列内容：token 用空格连接。 */
    fun grams(norm: String): String = tokens(norm).joinToString(" ")

    fun gramsOf(raw: String): String = grams(normalize(raw))

    /**
     * 构造 FTS4 MATCH 表达式。
     * - 中文词 → 短语查询 `"备忘 忘录"`（AND 会造成「国産車」误命中「国産の日産車」）
     * - 拉丁词 → 整词，末词可加前缀 `*`（输入中提示）
     * - 返回 null 表示无法构造（单字/纯符号），调用方降级 LIKE
     */
    fun queryExpr(userQuery: String, prefixLast: Boolean = true): String? {
        val norm = normalize(userQuery)
        if (norm.isBlank()) return null
        val words = norm.split(' ', '\n').filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val parts = ArrayList<String>(words.size)
        for ((idx, w) in words.withIndex()) {
            val toks = tokens(w)
            if (toks.isEmpty()) continue
            val last = idx == words.lastIndex
            if (toks.size == 1) {
                val t = toks[0]
                // 单个 CJK 字没有 bigram 语义，走 LIKE 更准
                if (hasCjk(t) && t.length == 1) continue
                parts.add(if (prefixLast && last && !hasCjk(t) && t.length >= 2) "$t*" else quote(t))
            } else {
                parts.add("\"" + toks.joinToString(" ") + "\"")
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" AND ")
    }

    /** 是否适合走 FTS：至少有一个长度 ≥2 的 CJK 词，或一个拉丁词。 */
    fun canUseFts(userQuery: String): Boolean = queryExpr(userQuery) != null

    private fun quote(t: String): String = "\"" + t.replace("\"", "\"\"") + "\""

    /**
     * 命中片段：在原文里定位后取前后文。
     * 归一化会改变字符宽度，所以先用原文小写匹配，失败再退回开头。
     */
    fun snippet(rawBody: String, needle: String, radius: Int = 60): String {
        val clean = rawBody.replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        if (clean.isEmpty()) return ""
        val words = normalize(needle).split(' ', '\n').filter { it.isNotBlank() }
        var idx = -1
        var matched = ""
        for (w in words) {
            val at = clean.indexOf(w, ignoreCase = true)
            if (at >= 0) {
                idx = at
                matched = w
                break
            }
        }
        if (idx < 0) {
            return if (clean.length > radius * 2) clean.take(radius * 2) + "…" else clean
        }
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + matched.length + radius).coerceAtMost(clean.length)
        val sb = StringBuilder()
        if (start > 0) sb.append('…')
        sb.append(clean, start, end)
        if (end < clean.length) sb.append('…')
        return sb.toString()
    }

    /** 给 UI 用：把命中词在片段里标出来（返回分段，不返回 HTML）。 */
    fun highlightParts(snippet: String, needle: String): List<Pair<String, Boolean>> {
        val words = normalize(needle).split(' ', '\n').filter { it.isNotBlank() }
        if (words.isEmpty() || snippet.isEmpty()) return listOf(snippet to false)
        // 取所有命中区间
        val ranges = ArrayList<IntRange>()
        for (w in words) {
            var from = 0
            while (true) {
                val at = snippet.indexOf(w, from, ignoreCase = true)
                if (at < 0) break
                ranges.add(at until at + w.length)
                from = at + w.length
            }
        }
        if (ranges.isEmpty()) return listOf(snippet to false)
        ranges.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        for (r in ranges) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                merged.add(r)
            }
        }
        val parts = ArrayList<Pair<String, Boolean>>()
        var cursor = 0
        for (r in merged) {
            if (r.first > cursor) parts.add(snippet.substring(cursor, r.first) to false)
            parts.add(snippet.substring(r.first, r.last + 1) to true)
            cursor = r.last + 1
        }
        if (cursor < snippet.length) parts.add(snippet.substring(cursor) to false)
        return parts
    }
}
