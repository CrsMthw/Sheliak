package com.crsmthw.sheliak.data.db

import java.text.Normalizer

/**
 * Turns what the user typed into a safe FTS4 `MATCH` expression and a safe `LIKE` pattern. Pure; unit-tested in
 * FtsQueryTest.
 */
object FtsQuery {

    /** More words than this add nothing to a music search and only slow the query. */
    const val MAX_TOKENS: Int = 16

    /**
     * Every word of [userText] as a quoted prefix term — `beat it` → `"beat*" "it*"` — joined with spaces, which
     * FTS4 reads as AND. Null when nothing searchable is left (blank input, only punctuation): the caller then
     * skips the query instead of sending an empty MATCH, which SQLite rejects.
     *
     * Words are split exactly where the index's `unicode61` tokenizer splits them — on anything that is not a
     * letter, digit or combining mark — so `don't` searches `"don*" "t*"`, as the stored text was tokenised.
     * That is also the escaping: a double quote is a separator, so user text can never close a phrase early,
     * and FTS operators (`OR`, `NOT`, `-`, `*`, `NEAR`, parentheses) are inert inside quotes.
     */
    fun build(userText: String): String? {
        val tokens = tokens(userText)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"$it*\"" }
    }

    /** The searchable words of [userText], in order, at most [MAX_TOKENS]. */
    fun tokens(userText: String): List<String> {
        val text = Normalizer.normalize(userText, Normalizer.Form.NFC)
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length && tokens.size < MAX_TOKENS) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isTokenChar(cp)) {
                current.appendCodePoint(cp)
            } else if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty() && tokens.size < MAX_TOKENS) tokens += current.toString()
        return tokens
    }

    /** The escape character [likeContains] uses; the query must say `LIKE :pattern ESCAPE '\'`. */
    const val LIKE_ESCAPE: Char = '\\'

    /**
     * A `LIKE` pattern matching [userText] anywhere (`%text%`), with `%`, `_` and the escape character itself
     * escaped so they match literally. Null for blank input. For the small tables that have no FTS index
     * (playlists).
     */
    fun likeContains(userText: String): String? {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return null
        val escaped = buildString {
            trimmed.forEach { c ->
                if (c == '%' || c == '_' || c == LIKE_ESCAPE) append(LIKE_ESCAPE)
                append(c)
            }
        }
        return "%$escaped%"
    }

    private fun isTokenChar(cp: Int): Boolean = Character.isLetterOrDigit(cp) || when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}
