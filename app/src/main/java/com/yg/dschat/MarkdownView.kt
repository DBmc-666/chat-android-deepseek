package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 轻量 Markdown：标题、无序/有序列表、代码块、行内粗体/斜体/删除线/行内码。 */
sealed class MdBlock {
    data class Para(val text: AnnotatedString) : MdBlock()
    data class Heading(val level: Int, val text: AnnotatedString) : MdBlock()
    data class Bullet(val index: Int, val ordered: Boolean, val text: AnnotatedString) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Math(val tex: String) : MdBlock()
    data class Mermaid(val code: String) : MdBlock()
}

private val orderedRegex = Regex("^\\s*(\\d+)\\.\\s+(.*)$")

fun parseMarkdown(src: String, colors: DsColors): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.split("\n")
    val buf = StringBuilder()

    fun flush() {
        val t = buf.toString().trim()
        if (t.isNotEmpty()) blocks.add(MdBlock.Para(inlineMarkdown(t, colors)))
        buf.clear()
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw
        when {
            line.trimStart().startsWith("$$") -> {
                flush()
                val rest = line.trim().removePrefix("$$").removeSuffix("$$")
                if (line.trim().length > 4 && line.trim().endsWith("$$")) {
                    blocks.add(MdBlock.Math(rest.trim()))
                } else {
                    val sb = StringBuilder()
                    if (rest.isNotBlank()) sb.append(rest)
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("$$")) {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(lines[i])
                        i++
                    }
                    blocks.add(MdBlock.Math(sb.toString().trim()))
                }
            }
            line.trimStart().startsWith("```") -> {
                flush()
                val lang = line.trim().removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i])
                    i++
                }
                if (lang.equals("mermaid", ignoreCase = true)) {
                    blocks.add(MdBlock.Mermaid(sb.toString()))
                } else {
                    blocks.add(MdBlock.Code(lang, sb.toString()))
                }
            }
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks.add(MdBlock.Heading(level, inlineMarkdown(line.drop(level).trim(), colors)))
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flush()
                blocks.add(MdBlock.Bullet(0, false, inlineMarkdown(line.trimStart().drop(2), colors)))
            }
            orderedRegex.matches(line) -> {
                flush()
                val m = orderedRegex.find(line)!!
                blocks.add(
                    MdBlock.Bullet(
                        m.groupValues[1].toIntOrNull() ?: 1,
                        true,
                        inlineMarkdown(m.groupValues[2], colors),
                    )
                )
            }
            line.isBlank() -> flush()
            else -> {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line)
            }
        }
        i++
    }
    flush()
    return blocks
}

fun inlineMarkdown(src: String, colors: DsColors): AnnotatedString = buildAnnotatedString {
    val sb = StringBuilder()

    fun flushText() {
        if (sb.isNotEmpty()) {
            append(sb.toString())
            sb.clear()
        }
    }

    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > i) {
                    flushText()
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(src.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    sb.append("**")
                    i += 2
                }
            }
            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i) {
                    flushText()
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(src.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    sb.append("~~")
                    i += 2
                }
            }
            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    flushText()
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = colors.brandText,
                        )
                    )
                    append(src.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    sb.append('`')
                    i++
                }
            }
            src[i] == '*' -> {
                val end = src.indexOf('*', i + 1)
                if (end > i) {
                    flushText()
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(src.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    sb.append('*')
                    i++
                }
            }
            else -> {
                sb.append(src[i])
                i++
            }
        }
    }
    flushText()
}

@Composable
fun MarkdownView(src: String, streaming: Boolean = false) {
    val colors = Ds.colors

    if (!streaming) {
        val blocks = remember(src) { parseMarkdown(src, colors) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { MdBlockItem(it) }
        }
        return
    }

    // 流式：以空行切开，已完成段落缓存解析结果，只有尾部那段每次重算。
    // 否则每 40ms 都要把整段回答重新解析一遍，长回答越写越卡。
    val cut = src.lastIndexOf("\n\n")
    val stable = if (cut > 0) src.substring(0, cut) else ""
    val tail = if (cut > 0) src.substring(cut + 2) else src
    val stableBlocks = remember(stable) {
        if (stable.isBlank()) emptyList() else parseMarkdown(stable, colors)
    }
    val tailBlocks = remember(tail) {
        if (tail.isBlank()) emptyList() else parseMarkdown(tail, colors)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stableBlocks.forEach { MdBlockItem(it) }
        tailBlocks.forEach { MdBlockItem(it) }
    }
}

@Composable
private fun MdBlockItem(b: MdBlock) {
    val colors = Ds.colors
    when (b) {
        is MdBlock.Para -> Text(
            b.text,
            fontSize = Ds.fsMd,
            lineHeight = Ds.lhMd,
            color = colors.labelPrimary,
        )
        is MdBlock.Heading -> Text(
            b.text,
            fontSize = when (b.level) {
                1 -> 22.sp
                2 -> 20.sp
                else -> 17.sp
            },
            fontWeight = FontWeight.SemiBold,
            color = colors.labelPrimary,
        )
        is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
            Text(
                if (b.ordered) "${b.index}." else "•",
                modifier = Modifier.width(22.dp),
                fontSize = Ds.fsMd,
                lineHeight = Ds.lhMd,
                color = colors.labelSecondary,
            )
            Text(
                b.text,
                fontSize = Ds.fsMd,
                lineHeight = Ds.lhMd,
                color = colors.labelPrimary,
            )
        }
        is MdBlock.Code -> CodeBlockView(b.lang, b.code)
        is MdBlock.Math -> WebRender("math", b.tex, dark = colors.dark)
        is MdBlock.Mermaid -> WebRender("mermaid", b.code, dark = colors.dark)
    }
}

@Composable
private fun CodeBlockView(lang: String, code: String) {
    val colors = Ds.colors
    val clip = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.codeBlock)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang.ifBlank { "code" },
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                color = colors.labelTertiary,
            )
            Text(
                if (copied) "已复制" else "复制",
                fontSize = 11.sp,
                color = colors.labelSecondary,
                modifier = Modifier.clickable {
                    clip.setText(AnnotatedString(code))
                    copied = true
                }.padding(4.dp),
            )
        }
        Text(
            remember(code, lang, colors) { highlightCode(code, lang, colors) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.labelPrimary,
        )
    }
}
