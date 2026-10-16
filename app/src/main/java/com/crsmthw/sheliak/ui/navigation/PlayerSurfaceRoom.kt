package com.crsmthw.sheliak.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey

/**
 * The bottom room an entry's lists keep for the floating player surface (`LocalBottomFadeExtra`, provided per
 * entry by `SheliakNavGraph`, read by `BottomFadeSpacer` and the library's fast scroller): [surfaceInset] — the
 * host's `LocalPlayerSurfaceInset`, the mini bar's height while it shows — on an entry the surface shows over
 * ([playerSurfaceAllowed]), and 0dp on every other one. The bottom fade itself never reads it: it stays at the
 * content's edge and the mini bar floats over it (fix round 2).
 *
 * Asked per ENTRY, never of the back stack's top: the host HOLDS its inset while Settings, Sources, the Plex
 * setup, the full player or the queue is on top (so the library sliding out under a push keeps its layout), and
 * those entries' lists must not keep room for a mini bar that is not there.
 */
fun playerSurfaceRoomFor(key: NavKey?, surfaceInset: Dp): Dp =
    if (playerSurfaceAllowed(key)) surfaceInset else 0.dp
