package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GreenAccent

@Composable
fun MarkdownViewer(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val lines = markdownText.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Code Blocks (``` ... ```)
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                // Skip the closing ```
                if (i < lines.size) i++

                val codeContent = codeLines.joinToString("\n")
                CodeBlockView(code = codeContent, language = if (lang.isBlank()) "código" else lang)
                continue
            }

            // 2. Math Blocks ($$ ... $$)
            if (trimmed.startsWith("$$")) {
                val mathLines = mutableListOf<String>()
                if (trimmed.endsWith("$$") && trimmed.length > 2) {
                    mathLines.add(trimmed.removePrefix("$$").removeSuffix("$$").trim())
                    i++
                } else {
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        mathLines.add(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        mathLines.add(lines[i].trim().removeSuffix("$$").trim())
                        i++
                    }
                }
                MathFormulaView(formula = mathLines.joinToString(" ").trim())
                continue
            }

            // 3. Tables (| Col 1 | Col 2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                MarkdownTableView(tableLines = tableLines)
                continue
            }

            // 4. Horizontal Rule / Page Break (--- or ***)
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                i++
                continue
            }

            // 5. Image syntax: ![alt](url)
            val imgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                MarkdownImageView(alt = alt, url = url)
                i++
                continue
            }

            // 6. Headers H1 - H5
            when {
                trimmed.startsWith("##### ") -> {
                    Text(
                        text = parseFormattedSpans(trimmed.removePrefix("##### ")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("#### ") -> {
                    Text(
                        text = parseFormattedSpans(trimmed.removePrefix("#### ")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseFormattedSpans(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseFormattedSpans(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseFormattedSpans(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }

                // 7. Checklists
                trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = "Concluído",
                            tint = GreenAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseFormattedSpans(trimmed.substring(6)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = TextDecoration.LineThrough
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                trimmed.startsWith("- [ ] ") -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Pendente",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseFormattedSpans(trimmed.substring(6)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 8. Bullet lists
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.substring(2)
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = "•",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseFormattedSpans(content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 9. Blockquote
                trimmed.startsWith("> ") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = parseFormattedSpans(trimmed.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                else -> {
                    Text(
                        text = parseFormattedSpans(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            i++
        }
    }
}

/**
 * Visual Table component in Compose for Markdown tables
 */
@Composable
private fun MarkdownTableView(tableLines: List<String>) {
    // Parse header and rows (skipping delimiter like | --- | --- |)
    val parsedRows = tableLines.mapNotNull { rowLine ->
        val cells = rowLine.split("|")
            .map { it.trim() }
            .filterIndexed { index, _ -> index > 0 && index < rowLine.split("|").size - 1 }
        // Check if delimiter row
        if (cells.all { it.matches(Regex("^-+$")) }) {
            null
        } else {
            cells
        }
    }

    if (parsedRows.isEmpty()) return

    val headers = parsedRows.firstOrNull() ?: emptyList()
    val dataRows = parsedRows.drop(1)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                headers.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .widthIn(min = 90.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Data Rows
            dataRows.forEachIndexed { idx, rowCells ->
                Row(
                    modifier = Modifier
                        .background(
                            if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    rowCells.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual Math / LaTeX formula box
 */
@Composable
private fun MathFormulaView(formula: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formula,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Code Block view with language badge and monospace styling
 */
@Composable
private fun CodeBlockView(code: String, language: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B), // Dark slate editor container
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with language tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = language.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Image view in Markdown - displays image without exposing the raw link
 */
@Composable
private fun MarkdownImageView(alt: String, url: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = alt,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (alt.isNotBlank() && alt != "Imagem" && alt != "imagem") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alt,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Parses bold (**text**), italic (*text*), link ([title](url)), and inline code (`code`)
 */
private fun parseFormattedSpans(input: String) = buildAnnotatedString {
    val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
    val italicPattern = Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)")
    val linkPattern = Regex("\\[(.*?)\\]\\((.*?)\\)")
    val inlineCodePattern = Regex("`(.*?)`")

    var remaining = input
    while (remaining.isNotEmpty()) {
        val boldMatch = boldPattern.find(remaining)
        val italicMatch = italicPattern.find(remaining)
        val linkMatch = linkPattern.find(remaining)
        val codeMatch = inlineCodePattern.find(remaining)

        val firstMatch = listOfNotNull(boldMatch, italicMatch, linkMatch, codeMatch)
            .minByOrNull { it.range.first }

        if (firstMatch == null) {
            append(remaining)
            break
        }

        if (firstMatch.range.first > 0) {
            append(remaining.substring(0, firstMatch.range.first))
        }

        when (firstMatch) {
            boldMatch -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(firstMatch.groupValues[1])
                pop()
            }
            italicMatch -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(firstMatch.groupValues[1])
                pop()
            }
            linkMatch -> {
                pushStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline))
                append(firstMatch.groupValues[1])
                pop()
            }
            codeMatch -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080)))
                append(" ${firstMatch.groupValues[1]} ")
                pop()
            }
        }

        remaining = remaining.substring(firstMatch.range.last + 1)
    }
}
