package com.crsmthw.sheliak.service

/** Index arithmetic for expanded requests and paged lists. Pure; tested in PlaylistExpansionTest. */
object PlaylistExpansion {

    /** Media3's `C.INDEX_UNSET`: "no start index, use the default". */
    const val INDEX_UNSET: Int = -1

    /**
     * The start index once each requested item became `groupSizes[i]` items: the first item of the requested
     * start's group, or of the next non-empty group when that one expanded to nothing (the last item when none
     * follows). [INDEX_UNSET] stays unset.
     */
    fun remapStartIndex(groupSizes: List<Int>, startIndex: Int): Int {
        if (startIndex == INDEX_UNSET) return INDEX_UNSET
        val total = groupSizes.sum()
        if (total == 0) return 0
        val before = groupSizes.take(startIndex.coerceIn(0, groupSizes.size)).sum()
        return before.coerceAtMost(total - 1)
    }

    /** The index range of [page] of [pageSize] in a list of [size], or null when the page is past the end. */
    fun pageRange(size: Int, page: Int, pageSize: Int): IntRange? {
        if (page < 0 || pageSize <= 0) return null
        val from = page.toLong() * pageSize
        if (from >= size) return null
        val to = minOf(size.toLong(), from + pageSize).toInt()
        return from.toInt() until to
    }
}
