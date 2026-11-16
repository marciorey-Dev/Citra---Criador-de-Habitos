package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

enum class CodeTokenType(
    val colorHex: String,
    val colorInt: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
) {
    COMMENT("94A3B8", 0xFF94A3B8.toInt(), isItalic = true),
    STRING("34D399", 0xFF34D399.toInt()),
    ANNOTATION("FCD34D", 0xFFFCD34D.toInt(), isBold = true),
    BOOLEAN("F87171", 0xFFF87171.toInt(), isBold = true),
    KEYWORD("C084FC", 0xFFC084FC.toInt(), isBold = true),
    TYPE("38BDF8", 0xFF38BDF8.toInt()),
    NUMBER("FBBF24", 0xFFFBBF24.toInt())
}

data class CodeTokenSpan(
    val start: Int,
    val endExclusive: Int,
    val type: CodeTokenType
)

object CodeHighlighter {

    // Prism.js Tomorrow Night inspired theme colors
    val ColorKeyword = Color(0xFFC084FC)    // Light purple / magenta
    val ColorString = Color(0xFF34D399)     // Emerald green
    val ColorNumber = Color(0xFFFBBF24)     // Amber / warm yellow
    val ColorComment = Color(0xFF94A3B8)    // Muted slate gray
    val ColorType = Color(0xFF38BDF8)       // Sky blue
    val ColorFunction = Color(0xFF60A5FA)   // Blue
    val ColorBoolean = Color(0xFFF87171)    // Soft coral red
    val ColorOperator = Color(0xFFF472B6)   // Pink
    val ColorAnnotation = Color(0xFFFCD34D) // Gold

    private val commonKeywords = setOf(
        "fun", "val", "var", "class", "interface", "object", "return", "if", "else",
        "while", "for", "do", "when", "in", "is", "as", "import", "package", "try",
        "catch", "finally", "throw", "null", "true", "false", "this", "super",
        "def", "let", "const", "function", "public", "private", "protected", "static",
        "void", "int", "float", "double", "boolean", "async", "await", "yield",
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "JOIN", "TABLE",
        "fn", "mut", "impl", "struct", "enum", "type", "switch", "case", "default"
    )

    private val commonTypes = setOf(
        "String", "Int", "Boolean", "Float", "Double", "Long", "List", "Map", "Set",
        "Array", "Unit", "Any", "Nothing", "Promise", "Observable", "Task", "Note", "Habit"
    )

    private val stringRegex = Regex("\"[^\"\\\\\\n]*(?:\\\\.[^\"\\\\\\n]*)*\"|'[^'\\\\\\n]*(?:\\\\.[^'\\\\\\n]*)*'")
    private val commentRegex = Regex("//[^\\n]*|/\\*[^*]*(?:\\*+[^*/][^*]*)*\\*+/|#[^\\n]*")
    private val numberRegex = Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?f?)\\b")
    private val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
    private val annotationRegex = Regex("@[a-zA-Z_][a-zA-Z0-9_]*")

    private const val MAX_HIGHLIGHT_CHARS = 20_000

    /**
     * Produces sorted, non-overlapping syntax spans resolved by precedence:
     * comment > string > annotation > boolean > keyword > type > number.
     */
    fun tokenize(code: String, @Suppress("UNUSED_PARAMETER") language: String = "kotlin"): List<CodeTokenSpan> {
        if (code.isEmpty()) return emptyList()
        val target = if (code.length > MAX_HIGHLIGHT_CHARS) code.substring(0, MAX_HIGHLIGHT_CHARS) else code

        // Track claimed character positions so higher-precedence tokens aren't broken by inner words/numbers
        val occupied = BooleanArray(target.length)
        val spans = mutableListOf<CodeTokenSpan>()

        fun claim(start: Int, endExclusive: Int, type: CodeTokenType) {
            if (start < 0 || endExclusive > target.length || start >= endExclusive) return
            for (i in start until endExclusive) {
                if (occupied[i]) return
            }
            for (i in start until endExclusive) {
                occupied[i] = true
            }
            spans.add(CodeTokenSpan(start, endExclusive, type))
        }

        // 1. Comments (highest priority)
        for (match in commentRegex.findAll(target)) {
            claim(match.range.first, match.range.last + 1, CodeTokenType.COMMENT)
        }

        // 2. Strings
        for (match in stringRegex.findAll(target)) {
            claim(match.range.first, match.range.last + 1, CodeTokenType.STRING)
        }

        // 3. Annotations
        for (match in annotationRegex.findAll(target)) {
            claim(match.range.first, match.range.last + 1, CodeTokenType.ANNOTATION)
        }

        // 4. Words: Booleans / Null, Keywords, Types
        for (match in wordRegex.findAll(target)) {
            val word = match.value
            val type = when {
                word == "true" || word == "false" || word == "null" -> CodeTokenType.BOOLEAN
                word in commonKeywords || word.uppercase() in commonKeywords -> CodeTokenType.KEYWORD
                word in commonTypes -> CodeTokenType.TYPE
                else -> null
            }
            if (type != null) {
                claim(match.range.first, match.range.last + 1, type)
            }
        }

        // 5. Numbers
        for (match in numberRegex.findAll(target)) {
            claim(match.range.first, match.range.last + 1, CodeTokenType.NUMBER)
        }

        spans.sortBy { it.start }
        return spans
    }

    fun highlight(code: String, language: String = "kotlin"): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        return try {
            val spans = tokenize(code, language)
            buildAnnotatedString {
                append(code)
                for (span in spans) {
                    val s = span.start.coerceIn(0, code.length)
                    val e = span.endExclusive.coerceIn(s, code.length)
                    if (s >= e) continue
                    val color = when (span.type) {
                        CodeTokenType.COMMENT -> ColorComment
                        CodeTokenType.STRING -> ColorString
                        CodeTokenType.ANNOTATION -> ColorAnnotation
                        CodeTokenType.BOOLEAN -> ColorBoolean
                        CodeTokenType.KEYWORD -> ColorKeyword
                        CodeTokenType.TYPE -> ColorType
                        CodeTokenType.NUMBER -> ColorNumber
                    }
                    addStyle(
                        SpanStyle(
                            color = color,
                            fontWeight = if (span.type.isBold) FontWeight.Bold else null,
                            fontStyle = if (span.type.isItalic) FontStyle.Italic else null
                        ),
                        s,
                        e
                    )
                }
            }
        } catch (_: Throwable) {
            AnnotatedString(code)
        }
    }
}

