package com.crsmthw.sheliak.data.db

import java.text.Normalizer
import java.util.Locale

/**
 * The normalised strings the index stores beside the display text: the merge keys (`artist_key`, `album_key`,
 * `track_key`) that the "merge duplicates" switch groups by, and the sort keys (`title_sort`, `name_sort`) the
 * lists are ordered by. Pure; unit-tested in NormalizedKeysTest.
 *
 * Diacritics are stripped only after a Latin, Greek or Cyrillic letter ("Björk" → "bjork"), where they are
 * accents a tag may or may not carry. In other scripts a combining mark is part of the letter — a Devanagari
 * vowel sign, a Japanese dakuten (が ≠ か) — and is kept.
 */
object NormalizedKeys {

    /** Joins the parts of a composite key. [normalize] removes punctuation, so it can never occur in a part. */
    const val SEPARATOR: Char = '|'

    /**
     * A merge key: lower-case, diacritics stripped (see above), punctuation and symbols removed, whitespace
     * collapsed to single spaces, trimmed. "AC/DC" → "acdc", "Sigur Rós" → "sigur ros", "  The  Band " →
     * "the band". Null reads as empty.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val folded = stripDiacritics(text.lowercase(Locale.ROOT))
        val out = StringBuilder(folded.length)
        var pendingSpace = false
        var i = 0
        while (i < folded.length) {
            val cp = folded.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> pendingSpace = out.isNotEmpty()
                isWordChar(cp) -> {
                    if (pendingSpace) out.append(' ')
                    pendingSpace = false
                    out.appendCodePoint(cp)
                }
                // Punctuation and symbols are dropped without a space: "AC/DC" and "ACDC" meet.
            }
        }
        return out.toString()
    }

    /** `artist_key`. */
    fun artistKey(name: String?): String = normalize(name)

    /** `album_key`: the album artist and the title, so two artists' "Greatest Hits" stay apart. */
    fun albumKey(albumArtist: String?, title: String?): String =
        normalize(albumArtist) + SEPARATOR + normalize(title)

    /**
     * `track_key`: the same recording on two sources — artist, album, disc, track number and title. Two
     * copies of one file carry the same tags, so they meet; two songs that merely share a title do not.
     */
    fun trackKey(artist: String?, album: String?, discNo: Int?, trackNo: Int?, title: String?): String =
        listOf(normalize(artist), normalize(album), discNo?.toString().orEmpty(), trackNo?.toString().orEmpty(),
            normalize(title)).joinToString(SEPARATOR.toString())

    /**
     * A sort key: lower-case, diacritics stripped, leading punctuation and spaces removed ("'Til Tuesday" sorts
     * under T, "(What's the Story)" under W), inner whitespace collapsed. Inner punctuation is kept — a sort key
     * is compared, not matched. A title made only of punctuation ("!!!") keeps it, so it still sorts somewhere
     * stable instead of collapsing to "".
     */
    fun sortKey(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val folded = stripDiacritics(text.lowercase(Locale.ROOT)).trim().replace(WHITESPACE, " ")
        val start = folded.indexOfFirst { Character.isLetterOrDigit(it) }
        return if (start < 0) folded else folded.substring(start)
    }

    private val WHITESPACE = Regex("\\s+")

    private val STRIPPED_SCRIPTS = setOf(
        Character.UnicodeScript.LATIN,
        Character.UnicodeScript.GREEK,
        Character.UnicodeScript.CYRILLIC,
    )

    /** A letter, a digit, or a combining mark that survived [stripDiacritics]. */
    private fun isWordChar(cp: Int): Boolean = Character.isLetterOrDigit(cp) || isMark(cp)

    private fun isMark(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    /**
     * Decomposes (NFD), drops the combining marks that follow a Latin, Greek or Cyrillic base letter, and
     * recomposes (NFC) so every other script — Hangul in particular, which NFD splits into jamo — reads as before.
     */
    private fun stripDiacritics(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var baseScript: Character.UnicodeScript? = null
        var i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            i += Character.charCount(cp)
            if (isMark(cp)) {
                if (baseScript !in STRIPPED_SCRIPTS) out.appendCodePoint(cp)
            } else {
                baseScript = Character.UnicodeScript.of(cp)
                out.appendCodePoint(cp)
            }
        }
        return Normalizer.normalize(out, Normalizer.Form.NFC)
    }
}
