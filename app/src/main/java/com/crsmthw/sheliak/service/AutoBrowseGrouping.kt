package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.util.AlphabetSections

/**
 * One letter folder of a long Android Auto list ([AutoBrowseGrouping]): its section [key] (what the folder's
 * [BrowseNode.Section] id carries — [AlphabetSections.keyOf]), the [label] it is titled with ([AlphabetSections.OTHER]
 * stands for "#", which the session maps to its string resource) and the rows it lists, as [members]: indices into
 * the list, in list order.
 */
data class AutoSection(val key: String, val label: String, val members: List<Int>)

/**
 * How Android Auto shows the four top-level lists (Tracks, Albums, Artists, Playlists). Auto does not page — Media3's
 * legacy stub asks for page 0 of `Int.MAX_VALUE` items — and a car lists only so many rows of one level, so a long
 * list becomes LETTER FOLDERS, the standard car pattern: one per alphabet section, in the order the app's own list
 * shows them, each listing every row of its section. A list of [AUTO_GROUP_MIN] items or fewer stays flat.
 *
 * The sections are exactly the in-app fast scroller's ([AlphabetSections], from the same sort keys the app's lists
 * are ordered by), so Auto's "S" folder holds what the app files under S. Pure Kotlin; unit-tested in
 * AutoBrowseGroupingTest.
 */
object AutoBrowseGrouping {

    /** A list of at most this many items is listed flat; a longer one as letter folders. */
    const val AUTO_GROUP_MIN: Int = 100

    /** Whether a list of [size] items is shown as letter folders ([sectionsOf]) rather than flat. */
    fun isGrouped(size: Int): Boolean = size > AUTO_GROUP_MIN

    /**
     * Every section of a list whose rows have [sortKeys] (one per row, in list order): each section once, in the
     * order of its FIRST row, labelled from that row ([AlphabetSections.labelOf]), with ALL of its rows — not just
     * its first run, so a list that is not sorted by these keys (playlists: Liked Songs first) loses no row. Every
     * row is in exactly one section.
     */
    fun sectionsOf(sortKeys: List<String>): List<AutoSection> {
        val members = LinkedHashMap<String, MutableList<Int>>()
        val labels = HashMap<String, String>()
        sortKeys.forEachIndexed { row, sortKey ->
            val key = AlphabetSections.keyOf(sortKey)
            members.getOrPut(key) {
                labels[key] = AlphabetSections.labelOf(key, sortKey)
                ArrayList()
            } += row
        }
        return members.map { (key, rows) -> AutoSection(key = key, label = labels.getValue(key), members = rows) }
    }

    /** The rows of section [key] in a list whose rows have [sortKeys], in list order; empty when none falls under it. */
    fun membersOf(sortKeys: List<String>, key: String): List<Int> =
        sortKeys.indices.filter { AlphabetSections.keyOf(sortKeys[it]) == key }
}
