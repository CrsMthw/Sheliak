package com.crsmthw.sheliak.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import com.crsmthw.sheliak.util.toggle

/*
 * The building blocks of the Settings screen and its sheets, in one file so every settings surface
 * shares one anatomy: section headers and labels, icon rows, switch rows, footnote tips, the accent
 * swatch, the labelled slider row and the reveal animation.
 *
 * Haptics are owned here where the row owns the gesture: SettingsItem fires `press()`, the switch
 * rows fire `toggle()`. SwatchCircle and AccentSliderRow fire nothing — the caller decides (the
 * swatch row fires `tick()` on a pick).
 */

/**
 * A Settings section title ("Sources", "About") — `labelMedium` in `primary`, marked as a heading so
 * TalkBack users can jump between sections.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * A Settings row: leading icon, title, optional subtitle. Tappable when [onClick] is non-null (it
 * fires a `press()` haptic first); a null [onClick] makes it an inert information row (the version
 * line). [tintError] paints the icon and title in `error` for a destructive action.
 */
@Composable
fun SettingsItem(
    icon      : ImageVector,
    title     : String,
    modifier  : Modifier = Modifier,
    subtitle  : String? = null,
    onClick   : (() -> Unit)? = null,
    tintError : Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val iconTint = if (tintError) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        leadingContent    = { Icon(icon, contentDescription = null, tint = iconTint) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        modifier          = if (onClick != null) {
            modifier.clickable { haptics.press(); onClick() }
        } else {
            modifier
        },
        content           = {
            Text(title, color = if (tintError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface)
        },
    )
}

/**
 * A Settings row with a trailing switch. The WHOLE row is one `toggleable(Role.Switch)` node and the
 * `Switch` inside is inert (`onCheckedChange = null`), so the row has one large touch target,
 * TalkBack reads one control with its state, and the switch cannot fire a second, competing toggle.
 * Fires a `toggle()` haptic with the new state.
 */
@Composable
fun SettingsToggleItem(
    icon           : ImageVector,
    title          : String,
    checked        : Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier       : Modifier = Modifier,
    subtitle       : String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val toggleWithHaptic: (Boolean) -> Unit = { enabled -> haptics.toggle(enabled); onCheckedChange(enabled) }
    ListItem(
        leadingContent    = { Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent   = {
            Switch(checked = checked, onCheckedChange = null)
        },
        modifier          = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = toggleWithHaptic),
        content           = { Text(title) },
    )
}

/**
 * A title + subtitle row with a trailing switch, as ONE `toggleable(Role.Switch)` node with an inert
 * `Switch` inside — [SettingsToggleItem] without the icon, for sheets whose section label already
 * says what the switch is about. Fires a `toggle()` haptic with the new state.
 */
@Composable
fun TwoLineToggleRow(
    title          : String,
    subtitle       : String,
    checked        : Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier       : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = { haptics.toggle(it); onCheckedChange(it) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A section label INSIDE a sheet ("Mode", "Accent") — one step larger than [SettingsSectionHeader]
 * (`labelLarge`) because a sheet has fewer, denser sections. A heading for TalkBack.
 */
@Composable
fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A short explanatory footnote under a setting — `bodySmall` in `onSurfaceVariant`. */
@Composable
fun SettingsTip(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.bodySmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    )
}

/**
 * Shows or hides a dependent block (the Accent section while Material You is off) with the Settings
 * list's own finite expand + fade — never a spring, because the content is entering and leaving, not
 * staying on screen. Hidden content LEAVES composition (`AnimatedVisibility` disposes it), so any state that
 * must survive a hide is hoisted above this call — the theme sheet keeps its slider state and write queue
 * outside its Accent section for exactly that reason.
 */
@Composable
fun RevealSection(
    visible : Boolean,
    modifier: Modifier = Modifier,
    content : @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible  = visible,
        modifier = modifier,
        enter    = fadeIn(screenTransitionSpec()) + expandVertically(screenTransitionSpec<IntSize>()),
        exit     = shrinkVertically(screenTransitionSpec<IntSize>()) + fadeOut(screenTransitionSpec()),
    ) { content() }
}

/**
 * A 36dp colour circle centred in a 48dp touch target (the app's minimum), for a preset swatch row;
 * the selected one is ringed in `onSurface` with a gap. The target carries the selectable
 * (`Role.RadioButton`) and the colour's [name], one focus node — so the row around it must be a
 * `selectableGroup()`. Fires no haptic: the caller does, on a pick.
 *
 * Laid out edge to edge, 48dp targets around 36dp circles leave a 12dp gap between circles, so the
 * row's horizontal padding should be its content edge less 6dp to keep the first circle on it.
 */
@Composable
fun SwatchCircle(
    color   : Color,
    name    : String,
    selected: Boolean,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier         = modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.border(2.dp, ring, CircleShape).padding(5.dp) else Modifier)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * A labelled accent slider (the Theme sheet's "Hue"): the [label] above, a dot in the current [dot]
 * colour at the start, then a [ValueSlider]. The dot shows the colour the slider is producing, so the
 * user sees the result without leaving the sheet.
 *
 * Drags report through [onValueChange] every frame (keep the value in local state); persist in
 * [onValueChangeFinished], once per release, so a drag is one write rather than one per frame. The
 * slider carries [label] as its content description, since the visible label is a separate node.
 */
@Composable
fun AccentSliderRow(
    label                : String,
    dot                  : Color,
    value                : Float,
    valueRange           : ClosedFloatingPointRange<Float>,
    onValueChange        : (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier             : Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(12.dp))
            ValueSlider(
                value                 = value,
                onValueChange         = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange            = valueRange,
                modifier              = Modifier.weight(1f).semantics { contentDescription = label },
            )
        }
    }
}
