package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

object LatexFormulaRenderer {

    private val superscriptMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ', 'b' to 'ᵇ',
        'i' to 'ⁱ', 'j' to 'ʲ', 'k' to 'ᵏ', 'm' to 'ᵐ', 'r' to 'ʳ',
        't' to 'ᵗ', 'c' to 'ᶜ', 'e' to 'ᵉ', 'o' to 'ᵒ', 'p' to 'ᵖ'
    )

    private val subscriptMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    private val greekSymbols = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
        "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
        "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
        "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\Lambda" to "Λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\Xi" to "Ξ",
        "\\pi" to "π", "\\Pi" to "Π", "\\rho" to "ρ", "\\sigma" to "σ", "\\Sigma" to "Σ",
        "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ", "\\Phi" to "Φ",
        "\\chi" to "χ", "\\psi" to "ψ", "\\Psi" to "Ψ", "\\omega" to "ω", "\\Omega" to "Ω"
    )

    private val mathOperators = mapOf(
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\sim" to "∼", "\\propto" to "∝", "\\sum" to "∑", "\\prod" to "∏",
        "\\int" to "∫", "\\iint" to "∬", "\\oint" to "∮",
        "\\partial" to "∂", "\\nabla" to "∇", "\\infty" to "∞", "\\degree" to "°",
        "\\to" to "→", "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\Leftrightarrow" to "⇔",
        "\\leftrightarrow" to "↔", "<=" to "≤", ">=" to "≥", "!=" to "≠", "+-" to "±",
        "\\forall" to "∀", "\\exists" to "∃", "\\in" to "∈", "\\notin" to "∉",
        "\\subset" to "⊂", "\\supset" to "⊃", "\\cup" to "∪", "\\cap" to "∩",
        "\\emptyset" to "∅", "\\neg" to "¬"
    )

    /**
     * Checks if a LaTeX formula has structural syntax errors (such as unclosed braces).
     */
    fun validateFormula(raw: String): String? {
        var openBraces = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                i += 2 // skip escaped character
                continue
            }
            if (c == '{') openBraces++
            if (c == '}') openBraces--
            if (openBraces < 0) return "Chave fechada '}' sem abertura"
            i++
        }
        if (openBraces > 0) return "Chave '{' não finalizada"
        return null
    }

    /**
     * Converts raw LaTeX chemistry notation inside \ce{...} or ce{...}
     * into readable Unicode chemical formulas (e.g. \ce{H2O} -> H₂O, \ce{CO2} -> CO₂).
     */
    /**
     * Converts raw LaTeX chemistry notation inside \ce{...} or plain formulas (e.g. H2O, CO2, 2H2 + O2 -> 2H2O)
     * into readable Unicode chemical formulas.
     */
    fun renderChemistry(raw: String): String {
        return try {
            val content = raw.removePrefix("\\ce{").removePrefix("ce{").removeSuffix("}").trim()

            var out = content

            // 1. Reactions arrows: -> to →, <=> to ⇌
            out = out.replace("->", " → ").replace("<=>", " ⇌ ")

            // 2. Charges: e.g. ^2+ or ^{2+} to ²⁺, ^- or ^{-} to ⁻, ^+ or ^{+} to ⁺
            out = out.replace(Regex("\\^\\{?([0-9]*[+-])\\}?")) { match ->
                val charge = match.groupValues[1]
                charge.map { superscriptMap[it] ?: it }.joinToString("")
            }

            // 3. Numbers following element symbols (A-Z or a-z or closing parenthesis) become subscripts (e.g. H2O -> H₂O, SO4 -> SO₄, (OH)2 -> (OH)₂)
            out = out.replace(Regex("([a-zA-Z\\)])(\\d+)")) { match ->
                val prefix = match.groupValues[1]
                val digits = match.groupValues[2]
                val subs = digits.map { subscriptMap[it] ?: it }.joinToString("")
                "$prefix$subs"
            }

            // 4. Hydrate dots (e.g. CuSO4 . 5H2O -> CuSO₄ · 5H₂O)
            out = out.replace(Regex("\\s*(\\.|\\*)\\s*"), " · ")

            out
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Converts a LaTeX math formula (or chemistry) into clean formatted text.
     */
    fun renderFormulaToText(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        return try {
            var text = trimmed

            // 1. Check for chemical notation: \ce{...} or auto-detect chemistry
            val ceRegex = Regex("(\\\\ce\\{[^}]+\\}|ce\\{[^}]+\\})")
            if (ceRegex.containsMatchIn(text)) {
                text = ceRegex.replace(text) { match ->
                    renderChemistry(match.value)
                }
            } else if (text.contains("->") || text.contains("<=>") || Regex("[A-Z][a-z]?\\d+").containsMatchIn(text)) {
                // Auto-render chemical equations without \ce{}
                text = renderChemistry(text)
            }

            // 2. Fractions: \frac{num}{den} -> (num) / (den) or clean form
            val fracRegex = Regex("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}")
            var prevText = ""
            while (fracRegex.containsMatchIn(text) && prevText != text) {
                prevText = text
                text = fracRegex.replace(text) { match ->
                    val num = match.groupValues[1].trim()
                    val den = match.groupValues[2].trim()
                    if (num.length <= 2 && den.length <= 2 && !num.contains(" ") && !den.contains(" ")) {
                        "$num⁄$den"
                    } else {
                        "($num) / ($den)"
                    }
                }
            }

            // 3. Square roots: \sqrt{...} or \sqrt[n]{...}
            text = text.replace(Regex("\\\\sqrt\\[(\\d+)\\]\\{([^{}]+)\\}")) { match ->
                val root = match.groupValues[1]
                val body = match.groupValues[2]
                val rootSup = root.map { superscriptMap[it] ?: it }.joinToString("")
                "$rootSup√($body)"
            }
            text = text.replace(Regex("\\\\sqrt\\{([^{}]+)\\}")) { match ->
                "√(${match.groupValues[1]})"
            }

            // 4. Greek symbols
            for ((tex, sym) in greekSymbols) {
                text = text.replace(tex, sym)
            }

            // 5. Math operators
            for ((tex, op) in mathOperators) {
                text = text.replace(tex, op)
            }

            // 6. Powers/exponents with braces: ^{...} e.g. x^{2}, E=mc^2
            text = text.replace(Regex("\\^\\{([^{}]+)\\}")) { match ->
                val exp = match.groupValues[1]
                exp.map { superscriptMap[it] ?: it }.joinToString("")
            }
            // Powers without braces: ^2, ^n, ^x
            text = text.replace(Regex("\\^([0-9a-zA-Z+-])")) { match ->
                val char = match.groupValues[1].first()
                superscriptMap[char]?.toString() ?: "^$char"
            }

            // 7. Subscripts with braces: _{...} e.g. x_{12}, a_{n}
            text = text.replace(Regex("_\\{([^{}]+)\\}")) { match ->
                val sub = match.groupValues[1]
                sub.map { subscriptMap[it] ?: it }.joinToString("")
            }
            // Subscripts without braces: _1, _n
            text = text.replace(Regex("_([0-9a-zA-Z+-])")) { match ->
                val char = match.groupValues[1].first()
                subscriptMap[char]?.toString() ?: "_$char"
            }

            // 8. Clean up LaTeX formatting helpers: \text{...}, \mathrm{...}, \left, \right
            text = text.replace(Regex("\\\\(text|mathrm|mathbf|mathit)\\{([^{}]+)\\}")) { match ->
                match.groupValues[2]
            }
            text = text.replace("\\left", "").replace("\\right", "")
            text = text.replace("\\,", " ").replace("\\;", " ").replace("\\quad", "   ").replace("\\qquad", "     ")
            text = text.replace("\\{", "{").replace("\\}", "}")

            // 9. Extra polish for standalone multiplication and equality
            text = text.replace(Regex("(?<=[a-zA-Z0-9])\\s*\\*\\s*(?=[a-zA-Z0-9])"), " · ")

            text.trim().ifEmpty { trimmed }
        } catch (_: Exception) {
            trimmed
        }
    }

    /**
     * Formats an inline formula into an AnnotatedString with math typography and background badge.
     */
    fun formatAnnotatedInline(raw: String, isDark: Boolean): AnnotatedString {
        val rendered = renderFormulaToText(raw).ifBlank { raw.trim() }
        val pillBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        val textCol = if (isDark) Color(0xFF67E8F9) else Color(0xFF0369A1)
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    background = pillBg,
                    color = textCol
                )
            ) {
                append(" $rendered ")
            }
        }
    }

    /**
     * Formats a block formula ($$formula$$) into an AnnotatedString with prominent highlighted typography.
     */
    fun formatAnnotatedBlock(raw: String, isDark: Boolean, accentColor: Color = Color(0xFFF97316)): AnnotatedString {
        val rendered = renderFormulaToText(raw).ifBlank { raw.trim() }
        val blockBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFEDD5)
        val textCol = if (isDark) Color(0xFFFDE047) else Color(0xFF9A3412)
        return buildAnnotatedString {
            append("\n")
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    background = blockBg,
                    color = textCol
                )
            ) {
                append("   📐 $rendered   ")
            }
            append("\n")
        }
    }
}
