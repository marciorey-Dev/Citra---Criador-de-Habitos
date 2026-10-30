package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

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

    fun highlight(code: String, language: String = "kotlin"): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            // 1. Strings: "..." or '...'
            val stringRegex = Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
            for (match in stringRegex.findAll(code)) {
                addStyle(SpanStyle(color = ColorString), match.range.first, match.range.last + 1)
            }

            // 2. Comments: //... or /*...*/ or #...
            val commentRegex = Regex("//.*|/\\*[\\s\\S]*?\\*/|#.*")
            for (match in commentRegex.findAll(code)) {
                addStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
            }

            // 3. Numbers: e.g. 123, 0.45, 0xFF
            val numberRegex = Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?f?)\\b")
            for (match in numberRegex.findAll(code)) {
                addStyle(SpanStyle(color = ColorNumber), match.range.first, match.range.last + 1)
            }

            // 4. Words: Keywords & Types
            val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
            for (match in wordRegex.findAll(code)) {
                val word = match.value
                when {
                    word == "true" || word == "false" || word == "null" -> {
                        addStyle(SpanStyle(color = ColorBoolean, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                    }
                    word in commonKeywords || word.uppercase() in commonKeywords -> {
                        addStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                    }
                    word in commonTypes -> {
                        addStyle(SpanStyle(color = ColorType), match.range.first, match.range.last + 1)
                    }
                }
            }

            // 5. Annotations: @Composable, @Test
            val annotationRegex = Regex("@[a-zA-Z_][a-zA-Z0-9_]*")
            for (match in annotationRegex.findAll(code)) {
                addStyle(SpanStyle(color = Color(0xFFFCD34D), fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
            }
        }
    }
}
