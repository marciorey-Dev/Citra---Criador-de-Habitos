package com.example.util

object MathBeautifier {

    private val superscriptDigits = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ', 'b' to 'ᵇ'
    )

    private val fractions = mapOf(
        "1/2" to "½",
        "1/3" to "⅓",
        "2/3" to "⅔",
        "1/4" to "¼",
        "3/4" to "¾",
        "1/5" to "⅕",
        "2/5" to "⅖",
        "3/5" to "⅗",
        "4/5" to "⅘",
        "1/6" to "⅙",
        "5/6" to "⅚",
        "1/8" to "⅛",
        "3/8" to "⅜",
        "5/8" to "⅝",
        "7/8" to "⅞",
        "1/10" to "⅒"
    )

    private val mathSymbols = listOf(
        "sqrt" to "√",
        "cbrt" to "∛",
        "pi" to "π",
        "infinity" to "∞",
        "inf" to "∞",
        "<=" to "≤",
        ">=" to "≥",
        "!=" to "≠",
        "+-" to "±",
        "-+" to "∓",
        "~=" to "≈",
        "=~" to "≈",
        "sum" to "∑",
        "prod" to "∏",
        "int" to "∫",
        "delta" to "Δ",
        "theta" to "θ",
        "alpha" to "α",
        "beta" to "β",
        "lambda" to "λ",
        "omega" to "Ω",
        "->" to "→",
        "<->" to "↔",
        "in " to "∈ ",
        "notin" to "∉"
    )

    /**
     * Dynamically beautifies mathematical input into clean, readable math notation.
     */
    fun beautify(input: String): String {
        var text = input

        // 1. Fractions: e.g. 3/5 -> ⅗
        for ((raw, frac) in fractions) {
            text = text.replace(Regex("(?<!\\d)${Regex.escape(raw)}(?!\\d)"), frac)
        }

        // 2. Powers: e.g. 2^3 -> 2³, x^2 -> x², a^n -> aⁿ
        val powerRegex = Regex("([a-zA-Z0-9])\\^([0-9a-zA-Z+-]+)")
        text = powerRegex.replace(text) { match ->
            val base = match.groupValues[1]
            val exp = match.groupValues[2]
            val sup = exp.map { superscriptDigits[it] ?: it }.joinToString("")
            "$base$sup"
        }

        // 3. Isolated caret followed by digit or var: ^2 -> ²
        val singleCaretRegex = Regex("\\^([0-9a-zA-Z+-])")
        text = singleCaretRegex.replace(text) { match ->
            val char = match.groupValues[1].first()
            superscriptDigits[char]?.toString() ?: match.value
        }

        // 4. Arithmetic operators and symbols
        for ((pattern, symbol) in mathSymbols) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(pattern)}\\b"), symbol)
        }

        // Isolated * to × and isolated / to ÷ (when surrounded by spaces or numbers)
        text = text.replace(Regex("(\\d|\\w)\\s*\\*\\s*(\\d|\\w)"), "$1 × $2")
        text = text.replace(Regex("(\\d|\\w)\\s*\\/\\s*(\\d|\\w)(?!\\w)"), "$1 ÷ $2")
        text = text.replace("<=", "≤")
        text = text.replace(">=", "≥")
        text = text.replace("!=", "≠")
        text = text.replace("+-", "±")

        return text
    }
}
