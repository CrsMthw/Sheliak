package com.crsmthw.sheliak.ui.player

/*
 * The `"album-art"` key's generation — Lyra's LocalPlayerArtKey workaround (DESIGN §5.1), as pure rules.
 * No Compose runtime — unit-tested in ArtKeyGenerationTest; PlayerSurfaceHost holds one ArtKeyGeneration and
 * provides albumArtKey(generation) as LocalPlayerArtKey.
 *
 * The defect: a predictive back released before it commits strands the shared element's per-key state (its
 * state machine, the bounds the next morph starts from), and the NEXT gesture then loses the art — it simply
 * disappears (Cris, M1 items 53 and 59; Lyra device reports 44 / 48). A fresh key is a fresh SharedElement at
 * "no match found", exactly the state before the first gesture, which always works.
 *
 * Why only after an ABANDONED seek: re-keying a participant that sits alone at rest updates its node in place,
 * which resets its "placed" flag with nothing to lay it out again, so the next morph has no start bounds and the
 * art appears at its destination without flying. After a cancel the abandoned participant is still composed for
 * a moment, the pair re-registers under the new key together and a match forms at rest, placing both.
 */

/** The key every `"album-art"` participant registers under for [generation]. */
fun albumArtKey(generation: Int): String = "$ALBUM_ART_SHARED_KEY#$generation"

/**
 * Whether the art's morph is QUIET — the only moment the key may change: the surface transition is settled
 * (no seek frame and no running slide satisfies this), no panel back gesture and no route back gesture is in
 * progress (a gesture that seeks nothing must not re-key mid-drag), and the navigation scope reports no active
 * shared transition (the full player's art hangs off the NAVIGATION transition, which can still be unwinding
 * when the surface has settled; re-keying then would drop the rest of that flight).
 */
fun morphQuiet(
    surfaceSettled        : Boolean,
    panelGestureIdle      : Boolean,
    routeGestureIdle      : Boolean,
    sharedTransitionActive: Boolean,
): Boolean = surfaceSettled && panelGestureIdle && routeGestureIdle && !sharedTransitionActive

/**
 * The generation counter. [onSeekAbandoned] is called from the ONE abandoned-seek path (the host's unwind of a
 * released-but-uncommitted seek, panel or route); [onQuiet] from every quiescent check. The request says
 * WHETHER the key changes, [morphQuiet] says WHEN; every other settle — a committed open or close, a push or pop,
 * a fold, a fold swap — keeps the key it has.
 *
 * Not snapshot state: written and read from effects only; the host copies [generation] into its own state when
 * [onQuiet] reports a change.
 */
class ArtKeyGeneration {

    /** The current generation; 0 until the first abandoned seek is honoured. */
    var generation: Int = 0
        private set

    /** A seek was abandoned and the key owes itself a fresh identity at the next quiet moment. */
    var pending: Boolean = false
        private set

    /** Records an abandoned seek (a released predictive back that did not commit). */
    fun onSeekAbandoned() {
        pending = true
    }

    /** Advances the generation when [quiet] and a request is pending; true when it did. At most one per request. */
    fun onQuiet(quiet: Boolean): Boolean {
        if (!quiet || !pending) return false
        pending = false
        generation++
        return true
    }
}
