package com.example.util

import java.text.BreakIterator

object EmojiHelper {
    /**
     * Extracts only the first single emoji / grapheme cluster from the given input string.
     * If the input is empty or blank, returns fallback.
     * Supports compound emojis with skin tones, zero-width joiners (ZWJ), flags, etc.
     */
    fun filterToSingleEmoji(input: String, fallback: String = "✨"): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return fallback

        val it = BreakIterator.getCharacterInstance()
        it.setText(trimmed)
        val firstEnd = it.next()
        if (firstEnd == BreakIterator.DONE || firstEnd <= 0) return fallback

        return trimmed.substring(0, firstEnd)
    }
}
