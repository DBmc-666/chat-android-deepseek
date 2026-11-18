package com.yg.dschat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** 轻量语法着色：关键字 / 字符串 / 数字 / 注释。够看，不追求完整语法树。 */
private val KEYWORDS: Map<String, Set<String>> = mapOf(
    "kotlin" to setOf(
        "val", "var", "fun", "class", "object", "interface", "data", "sealed", "enum",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue",
        "null", "true", "false", "import", "package", "suspend", "override", "private",
        "public", "internal", "protected", "companion", "init", "by", "in", "is", "as",
        "try", "catch", "finally", "throw", "this", "super", "typealias", "inline",
    ),
    "java" to setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements",
        "static", "final", "void", "int", "long", "double", "float", "boolean", "char",
        "if", "else", "for", "while", "return", "new", "null", "true", "false", "try",
        "catch", "finally", "throw", "import", "package", "this", "super",
    ),
    "python" to setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
        "return", "None", "True", "False", "try", "except", "finally", "raise", "with",
        "lambda", "yield", "async", "await", "self", "and", "or", "not", "in", "is",
    ),
    "javascript" to setOf(
        "const", "let", "var", "function", "class", "extends", "return", "if", "else",
        "for", "while", "new", "null", "undefined", "true", "false", "try", "catch",
        "finally", "throw", "import", "export", "from", "async", "await", "this",
    ),
    "typescript" to setOf(
        "const", "let", "var", "function", "class", "interface", "type", "extends",
        "implements", "return", "if", "else", "for", "while", "new", "null", "undefined",
        "true", "false", "try", "catch", "finally", "throw", "import", "export", "from",
        "async", "await", "this", "enum", "namespace", "readonly", "public", "private",
    ),
    "shell" to setOf(
        "if", "then", "else", "elif", "fi", "for", "do", "done", "while", "case", "esac",
        "function", "echo", "export", "local", "return", "cd", "source",
    ),
)

private val SLASH_COMMENT = setOf("kotlin", "java", "javascript", "typescript", "c", "cpp", "go", "rust", "swift")
private val HASH_COMMENT = setOf("python", "shell", "bash", "sh", "yaml", "toml", "ruby", "dockerfile")

fun highlightCode(code: String, lang: String, c: DsColors): AnnotatedString = buildAnnotatedString {
    val key = when (lang.lowercase()) {
        "kt", "kts" -> "kotlin"
        "js", "jsx", "mjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "sh", "bash", "zsh", "console" -> "shell"
        else -> lang.lowercase()
    }
    val keywords = KEYWORDS[key] ?: emptySet()
    val slash = key in SLASH_COMMENT
    val hash = key in HASH_COMMENT

    var i = 0
    while (i < code.length) {
        val ch = code[i]
        when {
            (slash && code.startsWith("//", i)) || (hash && ch == '#') -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                withStyle(SpanStyle(color = c.codeComment)) { append(code.substring(i, end)) }
                i = end
            }
            ch == '"' || ch == '\'' -> {
                val end = code.indexOf(ch, i + 1).let { if (it < 0) code.length - 1 else it }
                withStyle(SpanStyle(color = c.codeString)) { append(code.substring(i, end + 1)) }
                i = end + 1
            }
            ch.isDigit() -> {
                val start = i
                while (i < code.length && (code[i].isDigit() || code[i] == '.')) i++
                withStyle(SpanStyle(color = c.codeNumber)) { append(code.substring(start, i)) }
            }
            ch.isLetter() || ch == '_' -> {
                val start = i
                while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                if (word in keywords) {
                    withStyle(SpanStyle(color = c.codeKeyword)) { append(word) }
                } else {
                    append(word)
                }
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}
