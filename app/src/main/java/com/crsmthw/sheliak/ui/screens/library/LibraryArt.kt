package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.crsmthw.sheliak.ui.navigation.LocalNavSharedTransitionScope
import com.crsmthw.sheliak.util.artBoundsTransform
import com.crsmthw.sheliak.util.screenTransitionSpec

/**
 * The library's container transform: a card's art ([libraryArtKey] / [playlistArtKey]) and the pushed detail
 * screen's hero art share bounds across the navigation, in the shell's shared-transition scope with the entry's
 * own visibility scope, on the ONE finite spec ([artBoundsTransform]) — and so do the cross-fades of the two
 * contents, because `sharedBounds`' default fades are springs that would outlast the slide (docs/MOTION.md).
 *
 * Applied only where a pick PUSHES a detail entry (below 600dp, search, an artist's albums); a null [key] — the
 * two-pane library, where both ends would be on screen in one entry at once — is a no-op, and so is a surface
 * outside the shell.
 */
@Composable
fun Modifier.libraryArtSharedBounds(key: String?): Modifier {
    if (key == null) return this
    val sharedScope = LocalNavSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        this@libraryArtSharedBounds.sharedBounds(
            sharedContentState      = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            enter                   = fadeIn(screenTransitionSpec()),
            exit                    = fadeOut(screenTransitionSpec()),
            boundsTransform         = artBoundsTransform(),
        )
    }
}
