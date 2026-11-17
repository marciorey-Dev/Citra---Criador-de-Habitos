package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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

    private val symbolCommands = mapOf(
        // Greek lowercase & uppercase
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "Gamma" to "Γ",
        "delta" to "δ", "Delta" to "Δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "Theta" to "Θ", "vartheta" to "ϑ",
        "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "Lambda" to "Λ",
        "mu" to "μ", "nu" to "ν", "xi" to "ξ", "Xi" to "Ξ",
        "pi" to "π", "Pi" to "Π", "varpi" to "ϖ",
        "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "Sigma" to "Σ", "varsigma" to "ς",
        "tau" to "τ", "upsilon" to "υ", "Upsilon" to "Υ",
        "phi" to "φ", "varphi" to "ϕ", "Phi" to "Φ",
        "chi" to "χ", "psi" to "ψ", "Psi" to "Ψ", "omega" to "ω", "Omega" to "Ω",

        // Arithmetic & relations
        "times" to "×", "cdot" to "·", "ast" to "∗", "div" to "÷",
        "pm" to "±", "mp" to "∓",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "ll" to "≪", "gg" to "≫",

        // Calculus, algebra, set theory, logic
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "partial" to "∂", "nabla" to "∇", "infty" to "∞", "degree" to "°",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "mapsto" to "↦", "implies" to "⟹", "iff" to "⟺",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
        "cup" to "∪", "cap" to "∩", "setminus" to "∖",
        "emptyset" to "∅", "varnothing" to "∅", "neg" to "¬",
        "land" to "∧", "lor" to "∨", "wedge" to "∧", "vee" to "∨",
        "therefore" to "∴", "because" to "∵",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "prime" to "′", "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
        "triangle" to "△", "circ" to "∘", "bullet" to "•",
        "ell" to "ℓ", "hbar" to "ℏ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ"
    )

    private val functionCommands = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "coth",
        "log", "ln", "lg", "exp", "lim", "limsup", "liminf",
        "max", "min", "sup", "inf", "arg", "det", "dim", "gcd", "ker", "deg",
        "mod", "bmod", "Pr", "hom"
    )

    private val sizePrefixCommands = setOf(
        "left", "right", "big", "Big", "bigg", "Bigg",
        "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "Biggl", "Biggr"
    )

    private sealed class MathNode {
        data class Seq(val items: List<MathNode>) : MathNode()
        data class Literal(val text: String, val upright: Boolean = false) : MathNode()
        data class Space(val width: String) : MathNode()
        data class Frac(val num: MathNode, val den: MathNode) : MathNode()
        data class Sqrt(val degree: MathNode?, val body: MathNode) : MathNode()
        data class Script(val base: MathNode?, val sub: MathNode?, val sup: MathNode?) : MathNode()
        data class Group(val inner: MathNode) : MathNode()
        data class Accent(val kind: String, val body: MathNode) : MathNode()
        data class Chem(val rendered: String) : MathNode()
    }

    /**
     * Checks if a LaTeX formula has structural syntax errors (such as unclosed braces).
     */
    fun validateFormula(raw: String): String? {
        var openBraces = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                i += 2
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
     * Converts raw LaTeX chemistry notation inside \ce{...} or plain formulas
     * into readable Unicode chemical formulas.
     */
    fun renderChemistry(raw: String): String {
        return try {
            var content = raw.trim()
            if (content.startsWith("\\ce{") && content.endsWith("}")) {
                content = content.removePrefix("\\ce{").dropLast(1).trim()
            } else if (content.startsWith("ce{") && content.endsWith("}")) {
                content = content.removePrefix("ce{").dropLast(1).trim()
            }

            var out = content
            out = out.replace("->", " → ").replace("<=>", " ⇌ ")
            out = out.replace(Regex("\\^\\{?([0-9]*[+-])\\}?")) { match ->
                match.groupValues[1].map { superscriptMap[it] ?: it }.joinToString("")
            }
            out = out.replace(Regex("([a-zA-Z\\)])(\\d+)")) { match ->
                val prefix = match.groupValues[1]
                val digits = match.groupValues[2]
                val subs = digits.map { subscriptMap[it] ?: it }.joinToString("")
                "$prefix$subs"
            }
            out = out.replace(Regex("\\s*(\\.|\\*)\\s*"), " · ")
            out
        } catch (_: Exception) {
            raw
        }
    }

    private fun isStandaloneChemistry(text: String): Boolean {
        if (text.contains("\\")) return false
        if (text.contains("=")) {
            return text.contains("<=>")
        }
        if (text.contains("->") || text.contains("<=>")) return true
        return Regex("\\b[A-Z][a-z]?\\d+[A-Za-z0-9()]*\\b").containsMatchIn(text)
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseAll(): MathNode {
            val nodes = parseSequence(stopOnCloseBrace = false)
            return simplifySeq(nodes)
        }

        private fun simplifySeq(nodes: List<MathNode>): MathNode {
            val flat = mutableListOf<MathNode>()
            for (n in nodes) {
                if (n is MathNode.Seq) flat.addAll(n.items)
                else flat.add(n)
            }
            return when (flat.size) {
                0 -> MathNode.Literal("")
                1 -> flat[0]
                else -> MathNode.Seq(flat)
            }
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun parseSequence(stopOnCloseBrace: Boolean): List<MathNode> {
            val list = mutableListOf<MathNode>()
            while (pos < src.length) {
                val ch = src[pos]
                if (stopOnCloseBrace && ch == '}') {
                    pos++ // consume closing brace
                    break
                }
                if (ch.isWhitespace()) {
                    pos++
                    // Preserve meaningful separation between adjacent alphanumeric/operator atoms
                    if (list.isNotEmpty() && list.last() !is MathNode.Space) {
                        val nextChar = src.getOrNull(pos)
                        if (nextChar != null && nextChar != '}' && !nextChar.isWhitespace()) {
                            list.add(MathNode.Space(" "))
                        }
                    }
                    continue
                }
                val atom = parseAtom()
                if (atom != null) {
                    val withScripts = parseTrailingScripts(atom)
                    list.add(withScripts)
                }
            }
            // Trim trailing space
            while (list.isNotEmpty() && list.last() is MathNode.Space) {
                list.removeAt(list.lastIndex)
            }
            return list
        }

        private fun parseTrailingScripts(initialBase: MathNode): MathNode {
            var base: MathNode = initialBase
            var sub: MathNode? = null
            var sup: MathNode? = null

            while (pos < src.length) {
                val save = pos
                skipWhitespace()
                if (pos >= src.length) {
                    pos = save
                    break
                }
                val marker = src[pos]
                if (marker == '_' && sub == null) {
                    pos++
                    skipWhitespace()
                    sub = parseScriptArg()
                } else if (marker == '^' && sup == null) {
                    pos++
                    skipWhitespace()
                    sup = parseScriptArg()
                } else {
                    pos = save
                    break
                }
            }

            return if (sub != null || sup != null) {
                MathNode.Script(base = base, sub = sub, sup = sup)
            } else {
                base
            }
        }

        private fun parseScriptArg(): MathNode {
            skipWhitespace()
            if (pos >= src.length) return MathNode.Literal("")
            return if (src[pos] == '{') {
                pos++
                MathNode.Group(simplifySeq(parseSequence(stopOnCloseBrace = true)))
            } else {
                parseAtom() ?: MathNode.Literal("")
            }
        }

        private fun parseBraceGroupOrAtom(): MathNode {
            skipWhitespace()
            if (pos >= src.length) return MathNode.Literal("")
            return if (src[pos] == '{') {
                pos++
                simplifySeq(parseSequence(stopOnCloseBrace = true))
            } else {
                parseAtom() ?: MathNode.Literal("")
            }
        }

        private fun readRawBraceContent(): String {
            skipWhitespace()
            if (pos >= src.length) return ""
            if (src[pos] != '{') {
                val ch = src[pos++]
                return ch.toString()
            }
            pos++ // consume '{'
            var depth = 1
            val sb = StringBuilder()
            while (pos < src.length && depth > 0) {
                val c = src[pos++]
                when (c) {
                    '\\' -> {
                        sb.append(c)
                        if (pos < src.length) sb.append(src[pos++])
                    }
                    '{' -> {
                        depth++
                        sb.append(c)
                    }
                    '}' -> {
                        depth--
                        if (depth > 0) sb.append(c)
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun readBracketGroup(): MathNode? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '[') return null
            pos++ // consume '['
            val sb = StringBuilder()
            var braceDepth = 0
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '{') braceDepth++
                else if (c == '}') braceDepth--
                else if (c == ']' && braceDepth <= 0) break
                sb.append(c)
            }
            return Parser(sb.toString()).parseAll()
        }

        private fun parseAtom(): MathNode? {
            if (pos >= src.length) return null
            val ch = src[pos]

            if (ch == '{') {
                pos++
                return MathNode.Group(simplifySeq(parseSequence(stopOnCloseBrace = true)))
            }
            if (ch == '}') {
                pos++
                return null
            }

            // Standalone leading _ or ^ without base
            if (ch == '_' || ch == '^') {
                val marker = ch
                pos++
                val arg = parseScriptArg()
                return if (marker == '_') MathNode.Script(null, sub = arg, sup = null)
                else MathNode.Script(null, sub = null, sup = arg)
            }

            if (ch == '\\') {
                return parseCommand()
            }

            // Check bare chemistry ce{...}
            if (src.startsWith("ce{", pos) && (pos == 0 || !src[pos - 1].isLetterOrDigit())) {
                pos += 2 // leave '{' for readRawBraceContent
                val rawChem = readRawBraceContent()
                return MathNode.Chem(renderChemistry("\\ce{$rawChem}"))
            }

            // Multi-char ASCII operators <= >= != +-
            if (src.startsWith("<=", pos)) { pos += 2; return MathNode.Literal(" ≤ ") }
            if (src.startsWith(">=", pos)) { pos += 2; return MathNode.Literal(" ≥ ") }
            if (src.startsWith("!=", pos)) { pos += 2; return MathNode.Literal(" ≠ ") }
            if (src.startsWith("+-", pos)) { pos += 2; return MathNode.Literal(" ± ") }
            if (src.startsWith("-+", pos)) { pos += 2; return MathNode.Literal(" ∓ ") }

            // Grouping numbers like 24, 2.5 together or single variable char
            if (ch.isDigit()) {
                val start = pos
                while (pos < src.length && (src[pos].isDigit() || (src[pos] == '.' && src.getOrNull(pos + 1)?.isDigit() == true))) {
                    pos++
                }
                return MathNode.Literal(src.substring(start, pos), upright = true)
            }

            if (ch.isLetter()) {
                pos++
                return MathNode.Literal(ch.toString(), upright = false)
            }

            pos++
            return when (ch) {
                '+' -> MathNode.Literal(" + ", upright = true)
                '-' -> MathNode.Literal("-", upright = true)
                '=' -> MathNode.Literal(" = ", upright = true)
                '<' -> MathNode.Literal(" < ", upright = true)
                '>' -> MathNode.Literal(" > ", upright = true)
                ',' -> MathNode.Literal(",", upright = true)
                '*' -> MathNode.Literal(" · ", upright = true)
                else -> MathNode.Literal(ch.toString(), upright = true)
            }
        }

        private fun parseCommand(): MathNode {
            pos++ // consume '\\'
            if (pos >= src.length) return MathNode.Literal("\\")
            val first = src[pos]

            // Punctuation / spacing commands like \, \; \: \! \{ \} \\
            if (!first.isLetter()) {
                pos++
                return when (first) {
                    ',', ';', ':', ' ' -> MathNode.Space(" ")
                    '!' -> MathNode.Space("")
                    '\\' -> MathNode.Literal("\n", upright = true)
                    '{', '}', '%', '$', '&', '#', '_' -> MathNode.Literal(first.toString(), upright = true)
                    else -> MathNode.Literal(first.toString(), upright = true)
                }
            }

            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val cmd = src.substring(start, pos)

            return when (cmd) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    val num = parseBraceGroupOrAtom()
                    val den = parseBraceGroupOrAtom()
                    MathNode.Frac(num, den)
                }
                "sqrt" -> {
                    val deg = readBracketGroup()
                    val body = parseBraceGroupOrAtom()
                    MathNode.Sqrt(deg, body)
                }
                "ce" -> {
                    val rawChem = readRawBraceContent()
                    MathNode.Chem(renderChemistry("\\ce{$rawChem}"))
                }
                "text", "mathrm", "mathbf", "mathit", "operatorname", "mathcal", "mathbb", "textbf", "textit" -> {
                    val textBody = readRawBraceContent()
                    MathNode.Literal(textBody, upright = cmd != "mathit")
                }
                "bar", "overline" -> MathNode.Accent("bar", parseBraceGroupOrAtom())
                "hat", "widehat" -> MathNode.Accent("hat", parseBraceGroupOrAtom())
                "vec", "overrightarrow" -> MathNode.Accent("vec", parseBraceGroupOrAtom())
                "tilde", "widetilde" -> MathNode.Accent("tilde", parseBraceGroupOrAtom())
                "dot" -> MathNode.Accent("dot", parseBraceGroupOrAtom())
                "ddot" -> MathNode.Accent("ddot", parseBraceGroupOrAtom())
                "underline" -> MathNode.Accent("underline", parseBraceGroupOrAtom())
                "quad" -> MathNode.Space("   ")
                "qquad" -> MathNode.Space("     ")
                "displaystyle", "textstyle", "scriptstyle", "limits", "nolimits" -> MathNode.Space("")
                in sizePrefixCommands -> {
                    skipWhitespace()
                    if (pos < src.length && src[pos] == '.') {
                        pos++ // \left. or \right. -> invisible delimiter
                        MathNode.Space("")
                    } else if (pos < src.length && src[pos] == '\\') {
                        val saved = pos
                        pos++
                        if (pos < src.length && (src[pos] == '{' || src[pos] == '}' || src[pos] == '|')) {
                            val d = src[pos++]
                            MathNode.Literal(if (d == '|') "‖" else d.toString(), upright = true)
                        } else {
                            pos = saved
                            MathNode.Space("")
                        }
                    } else {
                        MathNode.Space("")
                    }
                }
                in functionCommands -> MathNode.Literal(cmd, upright = true)
                else -> {
                    val sym = symbolCommands[cmd]
                    if (sym != null) {
                        MathNode.Literal(sym, upright = true)
                    } else {
                        // Unknown command: strip leading backslash cleanly so text stays readable
                        MathNode.Literal(cmd, upright = true)
                    }
                }
            }
        }
    }

    private fun parseToAst(input: String): MathNode {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return MathNode.Literal("")
        if (isStandaloneChemistry(trimmed)) {
            return MathNode.Chem(renderChemistry(trimmed))
        }
        return Parser(trimmed).parseAll()
    }

    // --- Plain-text Unicode rendering (used in PDF/DOCX/HTML/NotePreviewParser/Clipboard) ---

    private fun nodeNeedsParensAsFractionPart(node: MathNode, isDenominator: Boolean): Boolean {
        val rendered = renderPlain(node).trim()
        if (rendered.isEmpty()) return false
        if ((rendered.startsWith("(") && rendered.endsWith(")")) ||
            (rendered.startsWith("[") && rendered.endsWith("]"))
        ) {
            // Only treat as already-wrapped if outer '(' matches the very last ')'
            var depth = 0
            var wrapsWhole = true
            for (i in rendered.indices) {
                when (rendered[i]) {
                    '(', '[' -> depth++
                    ')', ']' -> {
                        depth--
                        if (depth == 0 && i < rendered.lastIndex) {
                            wrapsWhole = false
                            break
                        }
                    }
                }
            }
            if (wrapsWhole) return false
        }
        return when (node) {
            is MathNode.Literal -> {
                if (isDenominator) rendered.length > 1 && rendered.any { it.isWhitespace() }
                else false
            }
            is MathNode.Sqrt, is MathNode.Chem -> false
            is MathNode.Group -> nodeNeedsParensAsFractionPart(node.inner, isDenominator)
            is MathNode.Script -> {
                if (node.base == null) false
                else nodeNeedsParensAsFractionPart(node.base, isDenominator)
            }
            is MathNode.Seq -> {
                val items = node.items.filterNot { it is MathNode.Space && it.width.isEmpty() }
                if (items.size <= 1) {
                    items.firstOrNull()?.let { nodeNeedsParensAsFractionPart(it, isDenominator) } ?: false
                } else {
                    // Check for single function call like sin(A) or ln(a) or log_b(a) -> unambiguous even in denominator
                    val first = items.first()
                    val firstIsFunc = (first is MathNode.Literal && first.text in functionCommands) ||
                        (first is MathNode.Script && first.base is MathNode.Literal && first.base.text in functionCommands)
                    if (firstIsFunc && items.size >= 2 && items[1] is MathNode.Literal && (items[1] as MathNode.Literal).text == "(") {
                        var depth = 0
                        var closedAtEnd = false
                        for (idx in 1 until items.size) {
                            val it = items[idx]
                            if (it is MathNode.Literal) {
                                if (it.text == "(") depth++
                                else if (it.text == ")") {
                                    depth--
                                    if (depth == 0) {
                                        closedAtEnd = (idx == items.lastIndex)
                                        break
                                    }
                                }
                            }
                        }
                        if (closedAtEnd) return false
                    }
                    if (!isDenominator) {
                        // In numerator, wrap if it contains binary +, -, ±, =, spacing
                        rendered.contains(" + ") || rendered.contains(" - ") ||
                            rendered.contains("±") || rendered.contains("∓") ||
                            rendered.contains("=") || rendered.contains(" -") ||
                            (rendered.startsWith("-") && rendered.contains(" "))
                    } else {
                        // In denominator, wrap any multi-factor product like 2a, 4a, σ√(2π)
                        true
                    }
                }
            }
            else -> isDenominator
        }
    }

    private fun toUnicodeScript(raw: String, isSuper: Boolean): String? {
        val map = if (isSuper) superscriptMap else subscriptMap
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val mapped = map[ch] ?: return null
            sb.append(mapped)
        }
        return sb.toString()
    }

    private fun renderPlain(node: MathNode): String {
        val raw = when (node) {
            is MathNode.Literal -> node.text
            is MathNode.Space -> node.width
            is MathNode.Chem -> node.rendered
            is MathNode.Group -> renderPlain(node.inner)
            is MathNode.Seq -> {
                val sb = StringBuilder()
                for (i in node.items.indices) {
                    val item = node.items[i]
                    if (item is MathNode.Literal && item.text == "-" && i > 0) {
                        val prev = node.items[i - 1]
                        val prevStr = renderPlain(prev)
                        val isUnary = prevStr.isEmpty() || prevStr.endsWith("(") || prevStr.endsWith("[") ||
                            prevStr.endsWith("= ") || prevStr.endsWith(", ") || prevStr.endsWith("+ ") || prevStr.endsWith(" ")
                        // Handle unary vs binary '-' cleanly
                        val nextPrevIsVarOrClose = prev !is MathNode.Space &&
                            !(prev is MathNode.Literal && (prev.text in listOf("(", "[", "{", "=", ",", "+", "-", "±", "∓")))
                        if (nextPrevIsVarOrClose) sb.append(" - ") else sb.append("-")
                    } else {
                        sb.append(renderPlain(item))
                    }
                }
                sb.toString()
            }
            is MathNode.Frac -> {
                val numStr = renderPlain(node.num).trim()
                val denStr = renderPlain(node.den).trim()
                val numOut = if (nodeNeedsParensAsFractionPart(node.num, isDenominator = false)) "($numStr)" else numStr
                val denOut = if (nodeNeedsParensAsFractionPart(node.den, isDenominator = true)) "($denStr)" else denStr
                "$numOut / $denOut"
            }
            is MathNode.Sqrt -> {
                val bodyStr = renderPlain(node.body).trim()
                val degStr = node.degree?.let { renderPlain(it).trim() }
                val rootPrefix = if (!degStr.isNullOrEmpty()) {
                    toUnicodeScript(degStr, isSuper = true) ?: "^($degStr)"
                } else ""
                "$rootPrefix√($bodyStr)"
            }
            is MathNode.Accent -> {
                val bodyStr = renderPlain(node.body).trim()
                val mark = when (node.kind) {
                    "bar" -> "\u0304"
                    "hat" -> "\u0302"
                    "vec" -> "\u20D7"
                    "tilde" -> "\u0303"
                    "dot" -> "\u0307"
                    "ddot" -> "\u0308"
                    "underline" -> "\u0332"
                    else -> ""
                }
                if (bodyStr.length == 1 && mark.isNotEmpty()) "$bodyStr$mark" else "${node.kind}($bodyStr)"
            }
            is MathNode.Script -> {
                val baseStr = node.base?.let { renderPlain(it) } ?: ""
                val subStr = node.sub?.let { s ->
                    val inner = renderPlain(s).trim()
                    toUnicodeScript(inner, isSuper = false) ?: if (inner.length == 1) "_$inner" else "_($inner)"
                } ?: ""
                val supStr = node.sup?.let { s ->
                    val inner = renderPlain(s).trim()
                    toUnicodeScript(inner, isSuper = true) ?: if (inner.length == 1) "^$inner" else "^($inner)"
                } ?: ""
                "$baseStr$subStr$supStr"
            }
        }
        return raw
    }

    /**
     * Converts a LaTeX math formula (or chemistry) into clean formatted plain text.
     */
    fun renderFormulaToText(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val ast = parseToAst(trimmed)
            val cleaned = renderPlain(ast)
                .replace(Regex("[ ]{2,}"), " ")
                .replace(" ,", ",")
                .replace("( ", "(")
                .replace(" )", ")")
                .trim()
            cleaned.ifEmpty { trimmed }
        } catch (_: Exception) {
            trimmed
        }
    }

    // --- Rich AnnotatedString rendering (uses genuine Compose Superscript/Subscript spans) ---

    private fun appendAnnotatedNode(
        builder: AnnotatedString.Builder,
        node: MathNode,
        baseFontSize: TextUnit,
        isScript: Boolean = false
    ) {
        when (node) {
            is MathNode.Literal -> {
                val style = if (node.upright) {
                    SpanStyle(fontStyle = FontStyle.Normal)
                } else {
                    SpanStyle(fontStyle = FontStyle.Italic)
                }
                builder.withStyle(style) { append(node.text) }
            }
            is MathNode.Space -> builder.append(node.width)
            is MathNode.Chem -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append(node.rendered) }
            is MathNode.Group -> appendAnnotatedNode(builder, node.inner, baseFontSize, isScript)
            is MathNode.Seq -> {
                for (i in node.items.indices) {
                    val item = node.items[i]
                    if (item is MathNode.Literal && item.text == "-" && i > 0) {
                        val prev = node.items[i - 1]
                        val nextPrevIsVarOrClose = prev !is MathNode.Space &&
                            !(prev is MathNode.Literal && (prev.text in listOf("(", "[", "{", "=", ",", "+", "-", "±", "∓")))
                        builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) {
                            append(if (nextPrevIsVarOrClose) " - " else "-")
                        }
                    } else {
                        appendAnnotatedNode(builder, item, baseFontSize, isScript)
                    }
                }
            }
            is MathNode.Frac -> {
                val wrapNum = nodeNeedsParensAsFractionPart(node.num, isDenominator = false)
                val wrapDen = nodeNeedsParensAsFractionPart(node.den, isDenominator = true)
                if (wrapNum) builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append("(") }
                appendAnnotatedNode(builder, node.num, baseFontSize, isScript)
                if (wrapNum) builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append(")") }
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append(" / ") }
                if (wrapDen) builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append("(") }
                appendAnnotatedNode(builder, node.den, baseFontSize, isScript)
                if (wrapDen) builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append(")") }
            }
            is MathNode.Sqrt -> {
                if (node.degree != null) {
                    builder.withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = baseFontSize * 0.72f,
                            fontStyle = FontStyle.Normal
                        )
                    ) {
                        appendAnnotatedNode(builder, node.degree, baseFontSize * 0.72f, isScript = true)
                    }
                }
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append("√(") }
                appendAnnotatedNode(builder, node.body, baseFontSize, isScript)
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Normal)) { append(")") }
            }
            is MathNode.Accent -> {
                val innerText = renderPlain(node.body).trim()
                val mark = when (node.kind) {
                    "bar" -> "\u0304"
                    "hat" -> "\u0302"
                    "vec" -> "\u20D7"
                    "tilde" -> "\u0303"
                    "dot" -> "\u0307"
                    "ddot" -> "\u0308"
                    "underline" -> "\u0332"
                    else -> ""
                }
                if (innerText.length == 1 && mark.isNotEmpty()) {
                    builder.append("$innerText$mark")
                } else {
                    appendAnnotatedNode(builder, node.body, baseFontSize, isScript)
                }
            }
            is MathNode.Script -> {
                if (node.base != null) {
                    appendAnnotatedNode(builder, node.base, baseFontSize, isScript)
                }
                if (node.sub != null) {
                    val subSize = (baseFontSize.value * 0.74f).coerceAtLeast(9f).sp
                    builder.withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = subSize
                        )
                    ) {
                        appendAnnotatedNode(builder, node.sub, subSize, isScript = true)
                    }
                }
                if (node.sup != null) {
                    val supSize = (baseFontSize.value * 0.74f).coerceAtLeast(9f).sp
                    builder.withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = supSize
                        )
                    ) {
                        appendAnnotatedNode(builder, node.sup, supSize, isScript = true)
                    }
                }
            }
        }
    }

    /**
     * Formats an inline formula into an AnnotatedString with math typography and background badge.
     */
    fun formatAnnotatedInline(raw: String, isDark: Boolean): AnnotatedString {
        val pillBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        val textCol = if (isDark) Color(0xFF67E8F9) else Color(0xFF0369A1)
        val ast = try { parseToAst(raw) } catch (_: Exception) { MathNode.Literal(raw.trim()) }
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    background = pillBg,
                    color = textCol
                )
            ) {
                append(" ")
                appendAnnotatedNode(this, ast, 14.sp)
                append(" ")
            }
        }
    }

    /**
     * Formats a block formula ($$formula$$) into an AnnotatedString with prominent highlighted typography.
     */
    fun formatAnnotatedBlock(raw: String, isDark: Boolean, accentColor: Color = Color(0xFFF97316)): AnnotatedString {
        val blockBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFEDD5)
        val textCol = if (isDark) Color(0xFFFDE047) else Color(0xFF9A3412)
        val ast = try { parseToAst(raw) } catch (_: Exception) { MathNode.Literal(raw.trim()) }
        return buildAnnotatedString {
            append("\n")
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    background = blockBg,
                    color = textCol
                )
            ) {
                append("   ")
                appendAnnotatedNode(this, ast, 18.sp)
                append("   ")
            }
            append("\n")
        }
    }

    // --- True 2D Visual Compose Renderer for MathBlock cards (real stacked fractions & scripts) ---

    @Composable
    fun LatexFormulaBlockView(
        formula: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = 17.sp
    ) {
        val ast = remember(formula) {
            try {
                parseToAst(formula)
            } catch (_: Exception) {
                MathNode.Literal(formula.trim())
            }
        }
        Box(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            RenderMathNodeCompose(
                node = ast,
                textColor = textColor,
                fontSize = fontSize
            )
        }
    }

    @Composable
    private fun RenderMathNodeCompose(
        node: MathNode,
        textColor: Color,
        fontSize: TextUnit
    ) {
        when (node) {
            is MathNode.Literal -> {
                Text(
                    text = node.text,
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (node.upright) FontStyle.Normal else FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                    color = textColor,
                    maxLines = 1
                )
            }
            is MathNode.Space -> {
                if (node.width.isNotEmpty()) {
                    val wDp = when {
                        node.width.length >= 5 -> 20.dp
                        node.width.length >= 3 -> 14.dp
                        else -> 4.dp
                    }
                    Spacer(modifier = Modifier.width(wDp))
                }
            }
            is MathNode.Chem -> {
                Text(
                    text = node.rendered,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Normal,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                    color = textColor
                )
            }
            is MathNode.Group -> {
                RenderMathNodeCompose(node.inner, textColor, fontSize)
            }
            is MathNode.Seq -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in node.items.indices) {
                        val item = node.items[i]
                        if (item is MathNode.Literal && item.text == "-" && i > 0) {
                            val prev = node.items[i - 1]
                            val nextPrevIsVarOrClose = prev !is MathNode.Space &&
                                !(prev is MathNode.Literal && (prev.text in listOf("(", "[", "{", "=", ",", "+", "-", "±", "∓")))
                            Text(
                                text = if (nextPrevIsVarOrClose) " - " else "-",
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Normal,
                                fontWeight = FontWeight.Medium,
                                fontSize = fontSize,
                                color = textColor
                            )
                        } else {
                            RenderMathNodeCompose(item, textColor, fontSize)
                        }
                    }
                }
            }
            is MathNode.Frac -> {
                val fracFontSize = (fontSize.value * 0.86f).coerceAtLeast(10f).sp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(horizontal = 3.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 3.dp)) {
                        RenderMathNodeCompose(node.num, textColor, fracFontSize)
                    }
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .widthIn(min = 18.dp)
                            .fillMaxWidth()
                            .height(1.2.dp)
                            .background(textColor.copy(alpha = 0.85f))
                    )
                    Box(modifier = Modifier.padding(horizontal = 3.dp)) {
                        RenderMathNodeCompose(node.den, textColor, fracFontSize)
                    }
                }
            }
            is MathNode.Sqrt -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.degree != null) {
                        Box(modifier = Modifier.offset(y = (-5).dp)) {
                            RenderMathNodeCompose(node.degree, textColor, (fontSize.value * 0.65f).coerceAtLeast(9f).sp)
                        }
                    }
                    Text(
                        text = "√",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = (fontSize.value * 1.08f).sp,
                        color = textColor
                    )
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(start = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.1.dp)
                                .background(textColor.copy(alpha = 0.8f))
                        )
                        Box(modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)) {
                            RenderMathNodeCompose(node.body, textColor, fontSize)
                        }
                    }
                }
            }
            is MathNode.Accent -> {
                val textStr = renderPlain(node.body).trim()
                val mark = when (node.kind) {
                    "bar" -> "\u0304"
                    "hat" -> "\u0302"
                    "vec" -> "\u20D7"
                    "tilde" -> "\u0303"
                    "dot" -> "\u0307"
                    "ddot" -> "\u0308"
                    else -> ""
                }
                if (textStr.length == 1 && mark.isNotEmpty()) {
                    Text(
                        text = "$textStr$mark",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = fontSize,
                        color = textColor
                    )
                } else {
                    RenderMathNodeCompose(node.body, textColor, fontSize)
                }
            }
            is MathNode.Script -> {
                val scriptSize = (fontSize.value * 0.72f).coerceAtLeast(9f).sp
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.base != null) {
                        RenderMathNodeCompose(node.base, textColor, fontSize)
                    }
                    if (node.sub != null && node.sup != null) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(start = 1.dp)
                        ) {
                            RenderMathNodeCompose(node.sup, textColor, scriptSize)
                            RenderMathNodeCompose(node.sub, textColor, scriptSize)
                        }
                    } else if (node.sup != null) {
                        Box(
                            modifier = Modifier
                                .padding(start = 1.dp, bottom = 6.dp)
                                .align(Alignment.Top)
                        ) {
                            RenderMathNodeCompose(node.sup, textColor, scriptSize)
                        }
                    } else if (node.sub != null) {
                        Box(
                            modifier = Modifier
                                .padding(start = 1.dp, top = 6.dp)
                                .align(Alignment.Bottom)
                        ) {
                            RenderMathNodeCompose(node.sub, textColor, scriptSize)
                        }
                    }
                }
            }
        }
    }
}
