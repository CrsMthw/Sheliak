package com.crsmthw.sheliak.ui.player

import com.crsmthw.sheliak.domain.Track

/*
 * The queue screen's pure model: stable row keys, local edits, and the rule that decides when the screen
 * stops showing its own edit and follows the player again. No Compose runtime — unit-tested in QueueEditsTest.
 */

/** One row of the queue screen: [key] is unique within the list even when the same track is queued twice. */
data class QueueRow(val key: String, val track: Track)

/**
 * The rows of [queue] (play order). A track can be queued more than once (a playlist that repeats it, "play
 * next" of a queued track), and a lazy list crashes on duplicate keys, so each row's key is the track's media
 * id plus its occurrence number: `pid|iid#0`, `pid|iid#1`, …
 */
fun queueRows(queue: List<Track>): List<QueueRow> {
    val seen = HashMap<String, Int>()
    return queue.map { track ->
        val id = track.key.mediaId
        val n = seen[id] ?: 0
        seen[id] = n + 1
        QueueRow(key = "$id#$n", track = track)
    }
}

/**
 * This list with the item at [from] moved to [to] — removed, then inserted at [to] in the shortened list,
 * which is exactly Media3's `moveMediaItem(from, to)`, so the local order and the player's agree. Out-of-range
 * indices return the list unchanged.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/** This list without the item at [index]; unchanged when [index] is out of range. */
fun <T> List<T>.removedAt(index: Int): List<T> {
    if (index !in indices) return this
    return toMutableList().also { it.removeAt(index) }
}

/**
 * A local edit of the queue (a drag-reorder or a swipe-remove), shown until the player's queue catches up.
 *
 * The player answers asynchronously: a move with shuffle on is a round trip to the service, and right after a
 * removal with shuffle on the controller first shows an UNSHUFFLED masking timeline before the real one
 * arrives. Following the player blindly would make the list jump through those states; the edit is shown
 * instead, and [reconcile] decides — from the player's next queue, never from a timer — when to let go.
 *
 * @param base the player's queue (media ids, play order) the edit was made on.
 * @param rows what the screen shows meanwhile.
 * @param shuffle the shuffle mode the edit was made under.
 * @param maskingAllowance how many reordered-but-otherwise-identical queues to sit through: 1 for a removal
 *   with shuffle on (the masking timeline), else 0.
 */
data class QueueEdit(
    val base            : List<String>,
    val rows            : List<QueueRow>,
    val shuffle         : Boolean,
    val maskingAllowance: Int,
) {
    /** The player's queue this edit expects, as media ids. */
    val expected: List<String> get() = rows.map { it.track.key.mediaId }

    /**
     * The edit after the player reported [queue] (media ids, play order) with [shuffle]: still pending, or null
     * once the screen should follow the player again.
     * - unchanged from [base] → the player has not applied it yet: keep showing the edit;
     * - equal to [expected] → applied: follow the player;
     * - the expected tracks in another order, with masking allowance left → the masking timeline: keep;
     * - anything else (a shuffle toggle, a queue replaced from the notification or Android Auto) → the player
     *   moved on: follow it.
     */
    fun reconcile(queue: List<String>, shuffle: Boolean): QueueEdit? = when {
        shuffle != this.shuffle                                        -> null
        queue == base                                                  -> this
        queue == expected                                              -> null
        maskingAllowance > 0 && sameItems(queue, expected)             -> copy(maskingAllowance = maskingAllowance - 1)
        else                                                           -> null
    }

    private fun sameItems(a: List<String>, b: List<String>): Boolean =
        a.size == b.size && a.groupingBy { it }.eachCount() == b.groupingBy { it }.eachCount()
}
