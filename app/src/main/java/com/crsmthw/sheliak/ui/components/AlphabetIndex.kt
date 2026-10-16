package com.crsmthw.sheliak.ui.components

import androidx.compose.runtime.Immutable
import kotlin.math.floor

/*
 * The alphabet fast scroller's model ([AlphabetFastScroller]): which letter each row of a sorted list falls under,
 * where each letter starts, and the two mappings the scroller draws with — a finger's place on the track to a
 * letter, and the list's scroll position back to a place on the track. Pure Kotlin, unit-tested
 * (AlphabetIndexTest); the only Compose import is the `@Immutable` marker.
 *
 * ### Section space
 *
 * The track is divided into EQUAL slots, one per letter, in list order (Android's own section fast scroller does
 * the same): a finger can hit "Q" with three rows as easily as "S" with three hundred. Inside a slot the place is
 * proportional to the row within that letter, so the thumb still moves smoothly as the list scrolls through a long
 * letter. Both directions go through the same slots ([sectionAt] / [AlphabetIndex.fractionOfRow]), so the thumb
 * and the list never disagree about which letter is showing.
 */

/** The section key of digits, symbols, an empty key and letters the scroller cannot letter; shown as "#". */
const val ALPHABET_OTHER_SECTION: String = "#"

/** Marks a section key that stands for a whole caseless script (see [alphabetSectionKey]). */
private const val SCRIPT_SECTION_PREFIX = "script:"

/**
 * The section a row's sort key falls under. The keys are the index's normalised sort strings (lower-case,
 * diacritics stripped after a Latin / Greek / Cyrillic letter, leading punctuation dropped), so only the first
 * character matters:
 *
 * - a letter of a CASED script — Latin, Greek, Cyrillic, Armenian, and Latin letters NFD cannot fold (ø, æ, ł) —
 *   is its own section, the upper-case letter ("b" → "B", "ж" → "Ж", "ø" → "Ø"); a Latin letter with no
 *   single-letter upper case ("ß") is its own section as it is;
 * - a letter of a CASELESS script (Han, kana, Hangul, Arabic, Hebrew, Devanagari, Thai…) groups with every other
 *   letter of that script — one section per script rather than one per character, which would turn a Japanese
 *   library into hundreds of slots; the section's LABEL is the first such character in the list
 *   ([alphabetIndexOf]);
 * - a digit, a symbol, punctuation or an empty key is [ALPHABET_OTHER_SECTION] ("#").
 */
fun alphabetSectionKey(sortKey: String): String {
    if (sortKey.isEmpty()) return ALPHABET_OTHER_SECTION
    val cp = sortKey.codePointAt(0)
    if (!Character.isLetter(cp)) return ALPHABET_OTHER_SECTION
    val upper = Character.toUpperCase(cp)
    val script = Character.UnicodeScript.of(cp)
    // A Latin letter with no single-letter upper case (ß) is still a letter of its own, not a script group.
    val cased = upper != cp || Character.toLowerCase(cp) != cp || script == Character.UnicodeScript.LATIN
    return if (cased) String(Character.toChars(upper)) else SCRIPT_SECTION_PREFIX + script.name
}

/** The label a section key shows: the key itself, except a script section, labelled by its first character. */
private fun sectionLabel(key: String, firstSortKey: String): String =
    if (key.startsWith(SCRIPT_SECTION_PREFIX)) {
        String(Character.toChars(Character.toUpperCase(firstSortKey.codePointAt(0))))
    } else {
        key
    }

/**
 * The sections of a list sorted by [sortKeys] (one key per row, in list order): each section once, in the order
 * its first row appears, with that first row. A section that comes back later in the list (a list not sorted by
 * these keys — playlists, or a provider's sort title that differs) keeps its FIRST run: the scroller jumps there.
 * Labels are what the bubble shows; [ALPHABET_OTHER_SECTION] stands for "#" (the UI maps it to its resource).
 */
fun alphabetIndexOf(sortKeys: List<String>): AlphabetIndex {
    if (sortKeys.isEmpty()) return AlphabetIndex.Empty
    val labels = ArrayList<String>()
    val starts = ArrayList<Int>()
    val seen = HashSet<String>()
    sortKeys.forEachIndexed { row, sortKey ->
        val key = alphabetSectionKey(sortKey)
        if (seen.add(key)) {
            labels += sectionLabel(key, sortKey)
            starts += row
        }
    }
    return AlphabetIndex(labels = labels, starts = starts, rowCount = sortKeys.size)
}

/**
 * A sorted list's sections ([alphabetIndexOf]): [labels] in list order, the first row of each ([starts], strictly
 * increasing, the first always 0), over [rowCount] rows. A data class, so a re-read that changes nothing is equal.
 */
@Immutable
data class AlphabetIndex(
    val labels  : List<String>,
    val starts  : List<Int>,
    val rowCount: Int,
) {
    val sectionCount: Int get() = labels.size

    /** The first row of [section] (clamped to a real section; 0 for an empty index). */
    fun firstRowOf(section: Int): Int =
        if (starts.isEmpty()) 0 else starts[section.coerceIn(0, starts.lastIndex)]

    /** The number of rows in [section]. */
    private fun sizeOf(section: Int): Int {
        val end = if (section + 1 < starts.size) starts[section + 1] else rowCount
        return (end - starts[section]).coerceAtLeast(1)
    }

    /** The section [row] is in (rows past either end clamp to the first / last section). */
    fun sectionOfRow(row: Int): Int {
        if (starts.isEmpty()) return 0
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= row) low = mid else high = mid - 1
        }
        return low
    }

    /** The section under a finger at [fraction] of the track (0 = top, 1 = bottom): equal slots per section. */
    fun sectionAt(fraction: Float): Int {
        if (starts.isEmpty()) return 0
        return floor(fraction.coerceIn(0f, 1f) * sectionCount).toInt().coerceIn(0, sectionCount - 1)
    }

    /**
     * Where a (fractional) [row] sits on the track, in section space: its section's slot, plus its place inside
     * that section. [row] runs from 0 (the top) to [rowCount] (scrolled to the very end), which maps to 1.
     */
    fun fractionOfRow(row: Float): Float {
        if (starts.isEmpty() || rowCount <= 0) return 0f
        val clamped = row.coerceIn(0f, rowCount.toFloat())
        val section = sectionOfRow(floor(clamped).toInt().coerceAtMost(rowCount - 1))
        val within = ((clamped - starts[section]) / sizeOf(section)).coerceIn(0f, 1f)
        return ((section + within) / sectionCount).coerceIn(0f, 1f)
    }

    companion object {
        /** No rows: no sections; the scroller does not show. */
        val Empty: AlphabetIndex = AlphabetIndex(labels = emptyList(), starts = emptyList(), rowCount = 0)
    }
}

/**
 * The list's scroll position as a fractional row on [0, rowCount], for [AlphabetIndex.fractionOfRow]: the first
 * visible row plus how far it has scrolled off ([offsetFraction], 0–1), stretched so that the top of the list is
 * row 0 and the furthest the list can scroll — [firstVisibleRow] = `rowCount - visibleRows` — is [rowCount].
 * Without the stretch the thumb could never reach the bottom: the last row is never the FIRST visible one.
 *
 * A negative [firstVisibleRow] (items above the rows — the Tracks carousel — still on screen) is the top.
 */
fun alphabetScrollRow(firstVisibleRow: Int, offsetFraction: Float, visibleRows: Int, rowCount: Int): Float {
    if (rowCount <= 0 || firstVisibleRow < 0) return 0f
    val maxFirst = rowCount - visibleRows.coerceAtLeast(1)
    if (maxFirst <= 0) return 0f
    val progress = ((firstVisibleRow + offsetFraction.coerceIn(0f, 1f)) / maxFirst).coerceIn(0f, 1f)
    return progress * rowCount
}

/** Lists shorter than this get no fast scroller (a flick already reaches every row). */
const val ALPHABET_SCROLLER_MIN_ROWS: Int = 40
