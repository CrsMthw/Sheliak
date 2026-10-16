package com.crsmthw.sheliak.util

/**
 * Which alphabet section a row of a sorted list falls under — the ONE rule behind both the in-app alphabet fast
 * scroller (`ui/components/AlphabetIndex.kt`) and Android Auto's letter folders (`service/AutoBrowseGrouping.kt`),
 * so a title the app files under "S" is in Auto's "S" folder too. It lives here, not in the UI, because the
 * playback service must not depend on `ui/`. Pure Kotlin; unit-tested in AlphabetSectionsTest.
 *
 * The input is always a row's sort key — the index's normalised sort string (lower-case, diacritics stripped after
 * a Latin / Greek / Cyrillic letter, leading punctuation dropped), or the provider's own sort title when it sent one
 * ("The Beatles" may sort as "beatles", and then files under B) — never the display title.
 */
object AlphabetSections {

    /**
     * The section key of digits, symbols, an empty key and letters no section letters. Shown as "#", but through a
     * string resource (`library_scroller_other`), never as this literal.
     */
    const val OTHER: String = "#"

    /** Marks a section key that stands for a whole caseless script (see [keyOf]). */
    private const val SCRIPT_PREFIX = "script:"

    /**
     * The section [sortKey] falls under. Only its first character matters:
     *
     * - a letter of a CASED script — Latin, Greek, Cyrillic, Armenian, and Latin letters NFD cannot fold (ø, æ, ł) —
     *   is its own section, the upper-case letter ("b" → "B", "ж" → "Ж", "ø" → "Ø"); a Latin letter with no
     *   single-letter upper case ("ß") is its own section as it is;
     * - a letter of a CASELESS script (Han, kana, Hangul, Arabic, Hebrew, Devanagari, Thai…) groups with every other
     *   letter of that script — one section per script rather than one per character, which would turn a Japanese
     *   library into hundreds of sections; such a key is not a label ([labelOf] gives that);
     * - a digit, a symbol, punctuation or an empty key is [OTHER].
     */
    fun keyOf(sortKey: String): String {
        if (sortKey.isEmpty()) return OTHER
        val cp = sortKey.codePointAt(0)
        if (!Character.isLetter(cp)) return OTHER
        val upper = Character.toUpperCase(cp)
        val script = Character.UnicodeScript.of(cp)
        // A Latin letter with no single-letter upper case (ß) is still a letter of its own, not a script group.
        val cased = upper != cp || Character.toLowerCase(cp) != cp || script == Character.UnicodeScript.LATIN
        return if (cased) String(Character.toChars(upper)) else SCRIPT_PREFIX + script.name
    }

    /**
     * The label section [key] shows, given [firstSortKey], the sort key of the section's FIRST row in the list: the
     * key itself, except a script section, which is labelled by that first row's first character (a Japanese
     * library's kana section reads "さ" when its first title starts with さ). [OTHER] stays [OTHER]; the caller maps
     * it to its string resource.
     */
    fun labelOf(key: String, firstSortKey: String): String =
        if (key.startsWith(SCRIPT_PREFIX) && firstSortKey.isNotEmpty()) {
            String(Character.toChars(Character.toUpperCase(firstSortKey.codePointAt(0))))
        } else {
            key
        }
}
