package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.confirm

/** Wider than this the card stops growing, so an unfolded pane does not stretch two lines of text across it. */
private val EmptyStateMaxWidth = 480.dp

/**
 * What a library tab shows while it has nothing to list: a card with the tab's icon, a headline and one line.
 * With no source configured the line says how music gets here and [onAddSource] adds the "Add a Plex server"
 * button (it opens the Plex setup); with sources but nothing listed yet (a first sync still running, or a library
 * with no playlists) it is the line alone — no button that would set up a second server by mistake.
 *
 * A list item rather than a full-screen overlay, so the library bar still collapses and expands over it, and a
 * pull on it still refreshes.
 */
@Composable
internal fun LibraryEmptyState(
    icon       : ImageVector,
    title      : String,
    body       : String,
    onAddSource: (() -> Unit)?,
    modifier   : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
        Card(
            modifier = Modifier.widthIn(max = EmptyStateMaxWidth).fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = title,
                    style     = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.semantics { heading() },
                )
                Text(
                    text      = body,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (onAddSource != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick        = { haptics.confirm(); onAddSource() },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Add,
                            contentDescription = null,
                            modifier           = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.library_empty_action))
                    }
                }
            }
        }
    }
}
