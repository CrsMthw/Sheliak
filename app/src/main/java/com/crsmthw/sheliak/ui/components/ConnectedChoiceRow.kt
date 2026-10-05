package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crsmthw.sheliak.util.press

/**
 * Horizontal padding inside each segment, halved from M3's default.
 *
 * `ToggleButtonDefaults.contentPaddingFor(ToggleButtonSize.Small)` is `start = end = 16.dp`
 * (`ButtonSmallTokens.LeadingSpace`/`TrailingSpace`), i.e. **32dp of a segment is padding**. In a
 * narrow pane (folded landscape, a two-pane card) a segment can be only ~70dp wide, so a long label
 * has ~38dp to live in and cannot fit at any font size. Halving it buys the label back 16dp.
 * Because the label is centred, this is invisible wherever the segment has room — the visible gap
 * is `(segmentWidth - labelWidth) / 2`, not the padding. 8dp is the floor worth taking: the
 * segments are `CornerFull` pills, whose edge has already curved in to ~3dp by the top of the
 * glyphs, so less than this reads as the text touching the pill. Vertical matches the M3 default
 * for a touch pointer (`smallVerticalPadding`), so the 40dp `defaultMinSize` height is unchanged.
 */
private val SegmentContentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

/**
 * Last-resort shrink for a single-line segment label that still does not fit after the segment's
 * horizontal padding has been trimmed.
 *
 * `StepBased` treats "fits" as "is **not** ellipsized", so paired with `maxLines = 1` +
 * [TextOverflow.Ellipsis] it picks the largest size at which the whole word renders. Capped at the
 * size the segment's own type already uses (`ButtonSmallTokens` label, 14sp), so nothing ever
 * grows; floored at labelSmall so it stays legible. On every pane with room all segments sit at the
 * cap and look identical. (If M3 ever changes that token the cap simply stops matching — it cannot
 * crash, which a `LocalTextStyle`-derived max could if the ambient size were ever `Unspecified`.)
 * Sizes are in `sp`, so the user's font-scale setting still applies across the whole range.
 *
 * The known cost: on a genuinely cramped pane the longest label renders a step or two smaller than
 * its neighbours.
 */
private val CrampedLabelAutoSize = TextAutoSize.StepBased(
    minFontSize = 11.sp,
    maxFontSize = 14.sp,
    stepSize    = 0.5.sp,
)

/**
 * Connected single-choice picker (the Theme sheet's Mode, and every later segmented setting) built
 * on the M3 Expressive [ButtonGroup].
 *
 * ### Custom segments — a provisional workaround with a one-step retest
 *
 * Segments are hand-rolled `customItem`s instead of the stock `toggleableItem`, because on
 * material3 1.5.0-alpha22 through alpha27 `toggleableItem` rendered its label off-centre once the
 * group was stretched wide. **Retest on every material3 bump:** build the Theme sheet's Mode picker
 * with stock `toggleableItem(weight = 1f)` on the unfolded inner screen; if all three labels sit
 * centred in their segments, drop the custom segments (and with them [SegmentContentPadding] /
 * [CrampedLabelAutoSize]) in favour of the stock item.
 *
 * Everything else `toggleableItem` provides is kept: `weight(1f)` segments, the inter-button
 * press-squeeze (`animateWidth`, default compression limit — resolved from
 * `ButtonDefaults.ContentPadding`, so [SegmentContentPadding] does not change it), and a real
 * overflow indicator (an empty overflow mis-measures in tight layouts).
 *
 * ### Why the label is laid out the way it is
 *
 * Centring the label with `softWrap = false` + `overflow = Visible` + `fillMaxWidth()` +
 * `textAlign = Center` is self-defeating: `finalMaxWidth()` in foundation's `LayoutUtils.kt`
 * computes `widthMatters = softWrap || overflow.isEllipsis`, and when that is false it lays the
 * paragraph out at **`maxIntrinsicWidth`** rather than the incoming constraint. Once the label is
 * wider than the segment's content box the paragraph is exactly as wide as the text, so
 * `TextAlign.Center` has nothing to centre within, the node is then constrained back down to the
 * segment width, and the glyphs start at the content box's leading edge and run out under
 * `Surface`'s `clip(shape)` — a start-aligned, clipped label. M3's own `Text` KDoc warns: "If
 * softWrap is false, overflow and TextAlign may have unexpected effects."
 *
 * It is **not** a checked-state bug, however it presents: `ToggleButton` resolves the same
 * `contentPadding`, text style and `Row(horizontalArrangement = Center)` for both states, and
 * `ButtonGroup`'s measure policy skips the `animateWidth` growth entirely for any segment whose
 * press `Animatable` is at 0, so a resting checked segment is measured exactly like its neighbours.
 * Keeping `softWrap` on (the default) with a single line and ellipsis is the actual fix; the padding
 * and auto-size above only decide how much of the label survives on a cramped pane.
 *
 * The group is also width-capped at [maxWidth] and centred — segments on a wide unfolded pane
 * otherwise stretch to ~3× their folded width. (`widthIn` must precede `fillMaxWidth` in the
 * chain: the reverse order fixes the width first, making the cap a no-op.)
 *
 * Picking fires a `press` haptic; [onSelect] only fires on a genuine selection change.
 *
 * @param options each choice's value and its (already localised) label, in display order.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceRow(
    options : List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 420.dp,
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ButtonGroup(
            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
            modifier          = Modifier.widthIn(max = maxWidth).fillMaxWidth().selectableGroup(),
        ) {
            options.forEach { (value, label) ->
                val checked = selected == value
                customItem(
                    buttonGroupContent = {
                        val interaction = remember { MutableInteractionSource() }
                        ToggleButton(
                            checked           = checked,
                            onCheckedChange   = { isChecked ->
                                if (isChecked && !checked) { haptics.press(); onSelect(value) }
                            },
                            modifier          = Modifier.weight(1f).animateWidth(interaction),
                            contentPadding    = SegmentContentPadding,
                            interactionSource = interaction,
                        ) {
                            Text(
                                text      = label,
                                autoSize  = CrampedLabelAutoSize,
                                maxLines  = 1,
                                overflow  = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    menuContent = { menuState ->
                        DropdownMenuItem(
                            text    = { Text(label) },
                            onClick = {
                                menuState.dismiss()
                                if (!checked) { haptics.press(); onSelect(value) }
                            },
                        )
                    },
                )
            }
        }
    }
}
