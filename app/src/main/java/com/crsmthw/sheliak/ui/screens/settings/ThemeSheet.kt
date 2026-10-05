package com.crsmthw.sheliak.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.AccentSliderRow
import com.crsmthw.sheliak.ui.components.CappedModalBottomSheet
import com.crsmthw.sheliak.ui.components.ConnectedChoiceRow
import com.crsmthw.sheliak.ui.components.RevealSection
import com.crsmthw.sheliak.ui.components.SettingsSectionLabel
import com.crsmthw.sheliak.ui.components.SettingsToggleItem
import com.crsmthw.sheliak.ui.components.SwatchCircle
import com.crsmthw.sheliak.ui.components.sheetTopGap
import com.crsmthw.sheliak.ui.theme.ACCENT_HUE_MAX
import com.crsmthw.sheliak.ui.theme.ACCENT_HUE_MIN
import com.crsmthw.sheliak.ui.theme.ACCENT_SAT_MAX
import com.crsmthw.sheliak.ui.theme.ACCENT_SAT_MIN
import com.crsmthw.sheliak.ui.theme.AccentOwnWrites
import com.crsmthw.sheliak.ui.theme.AccentPresets
import com.crsmthw.sheliak.ui.theme.ThemeMode
import com.crsmthw.sheliak.ui.theme.accentHueSat
import com.crsmthw.sheliak.ui.theme.accentSeedColor
import com.crsmthw.sheliak.util.tick

/**
 * Every display-theme control in one sheet: the Mode picker (System / Light / Dark), the AMOLED and Material
 * You switches, and — while Material You is off — the **Accent** picker the whole colour scheme is generated
 * from: ten preset swatches, then Hue and Saturation sliders.
 *
 * The sliders keep their in-drag values here so the colour dot follows the finger, and persist on release
 * only (one write per drag, not one per frame). They are re-seeded from the stored accent ONLY on an outside
 * change — never by this sheet's own write coming back ([AccentOwnWrites]): a Hue released at exactly 360
 * reads back as 0, and re-seeding from the echo would snap the thumb across the track. A swatch tap re-seeds
 * the sliders itself, at the tap.
 *
 * The slider state is hoisted to the sheet, outside the [RevealSection], because a hidden section leaves
 * composition: switching Material You on and off again must not lose an unsaved drag position or the queue of
 * writes still in flight.
 */
@Composable
internal fun ThemeSheet(
    themeMode   : ThemeMode,
    amoledBlack : Boolean,
    dynamicColor: Boolean,
    accentColor : Int,
    onThemeMode : (ThemeMode) -> Unit,
    onAmoled    : (Boolean) -> Unit,
    onDynamic   : (Boolean) -> Unit,
    onAccent    : (Int) -> Unit,
    onDismiss   : () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    val ownWrites = remember { AccentOwnWrites() }
    var hue by remember { mutableFloatStateOf(accentHueSat(accentColor).hue) }
    var sat by remember { mutableFloatStateOf(accentHueSat(accentColor).saturation) }
    // Until a slider moves, the dot shows the STORED colour exactly (a preset's own ARGB, not its re-derived
    // hsl(h, s, 0.56)); after a drag it shows the sliders.
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(accentColor) {
        if (!ownWrites.isEcho(accentColor)) {
            val stored = accentHueSat(accentColor)
            hue     = stored.hue
            sat     = stored.saturation
            dragged = false
        }
    }
    val dotColor = if (dragged) accentSeedColor(hue, sat) else Color(accentColor)
    val persistSliders = {
        val argb = accentSeedColor(hue, sat).toArgb()
        ownWrites.record(argb)
        onAccent(argb)
    }
    val pickPreset = { argb: Int ->
        val picked = accentHueSat(argb)
        hue     = picked.hue
        sat     = picked.saturation
        dragged = false
        ownWrites.record(argb)
        onAccent(argb)
    }

    CappedModalBottomSheet(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight - sheetTopGap())
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text     = stringResource(R.string.settings_theme),
                        style    = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                // ── Mode ──
                SettingsSectionLabel(
                    text     = stringResource(R.string.theme_mode),
                    modifier = Modifier.padding(top = 8.dp),
                )
                ConnectedChoiceRow(
                    options  = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_mode_system),
                        ThemeMode.LIGHT  to stringResource(R.string.theme_mode_light),
                        ThemeMode.DARK   to stringResource(R.string.theme_mode_dark),
                    ),
                    selected = themeMode,
                    onSelect = onThemeMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsToggleItem(
                    icon            = Icons.Outlined.DarkMode,
                    title           = stringResource(R.string.theme_amoled),
                    subtitle        = stringResource(R.string.theme_amoled_desc),
                    checked         = amoledBlack,
                    onCheckedChange = onAmoled,
                )
                SettingsToggleItem(
                    icon            = Icons.Outlined.ColorLens,
                    title           = stringResource(R.string.theme_dynamic_color),
                    subtitle        = stringResource(R.string.theme_dynamic_color_desc),
                    checked         = dynamicColor,
                    onCheckedChange = onDynamic,
                )

                // ── Accent (only while Material You is off: with it on, the wallpaper is the accent) ──
                RevealSection(visible = !dynamicColor) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSectionLabel(
                            text     = stringResource(R.string.theme_accent),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // A radio group of 48dp targets around 36dp circles; the 10dp padding is the 16dp
                        // content edge less the 6dp margin a target leaves around its circle. A tap persists
                        // at once.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            AccentPresets.forEach { preset ->
                                SwatchCircle(
                                    color    = Color(preset.argb),
                                    name     = stringResource(preset.nameRes),
                                    selected = preset.argb == accentColor,
                                    onClick  = { haptics.tick(); pickPreset(preset.argb) },
                                )
                            }
                        }
                        AccentSliderRow(
                            label                 = stringResource(R.string.theme_accent_hue),
                            dot                   = dotColor,
                            value                 = hue,
                            valueRange            = ACCENT_HUE_MIN..ACCENT_HUE_MAX,
                            onValueChange         = { hue = it; dragged = true },
                            onValueChangeFinished = persistSliders,
                        )
                        Spacer(Modifier.height(4.dp))
                        AccentSliderRow(
                            label                 = stringResource(R.string.theme_accent_saturation),
                            dot                   = dotColor,
                            value                 = sat,
                            valueRange            = ACCENT_SAT_MIN..ACCENT_SAT_MAX,
                            onValueChange         = { sat = it; dragged = true },
                            onValueChangeFinished = persistSliders,
                        )
                    }
                }

                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
