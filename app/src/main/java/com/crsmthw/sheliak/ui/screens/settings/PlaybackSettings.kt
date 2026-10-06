package com.crsmthw.sheliak.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.ConnectedChoiceRow
import kotlin.math.abs

/** The transcode bit rates offered in Settings → Playback, in kbps, lowest first. */
val TranscodeBitrateChoicesKbps: List<Int> = listOf(128, 192, 256, 320)

/**
 * The offered choice a stored bit rate shows as selected: itself when it is one, else the nearest (the lower on a
 * tie) — so a value written by an older or newer version still selects a segment. Pure; tested.
 */
fun transcodeBitrateChoiceFor(storedKbps: Int): Int =
    TranscodeBitrateChoicesKbps.minWith(compareBy<Int> { abs(it - storedKbps) }.thenBy { it })

/**
 * Playback → Transcode bit rate: what a lossy transcode is asked for when a track has to be converted for this
 * phone (a codec it cannot decode, or a relay connection). A title row over a [ConnectedChoiceRow] of the four
 * rates; picking one writes it at once.
 */
@Composable
internal fun TranscodeBitrateSetting(
    selectedKbps: Int,
    onSelect    : (Int) -> Unit,
    modifier    : Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingContent    = {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            content           = { Text(stringResource(R.string.settings_transcode_bitrate)) },
            supportingContent = {
                Text(stringResource(R.string.settings_transcode_bitrate_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
        ConnectedChoiceRow(
            options  = TranscodeBitrateChoicesKbps.map { kbps -> kbps to stringResource(R.string.settings_bitrate_kbps, kbps) },
            selected = transcodeBitrateChoiceFor(selectedKbps),
            onSelect = onSelect,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}
