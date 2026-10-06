package com.crsmthw.sheliak.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A value-driven `Slider` over the `SliderState` overload — the shape every slider in the app uses
 * (seek bars, volume, the accent Hue row): a hoisted `Float` in, `onValueChange` per
 * drag frame, `onValueChangeFinished` on release (where the value is persisted or the seek issued).
 *
 * Material3 deprecated the value-driven overload in favour of `SliderState`; this wrapper does
 * exactly what that overload did internally (`remember(steps, valueRange) { SliderState(…) }`, then
 * `state.value = value`), so callers keep the hoisted-value pattern and the migration lives in ONE
 * place. The state is remembered per ([steps], [valueRange]) and RE-SYNCED from [value] on every
 * composition; writing `state.value` during composition is the library's own idiom here (a snapshot
 * write of an unchanged value does not invalidate). A slider that genuinely wants to be state-first
 * should use `rememberSliderState` at its call site instead.
 */
@Composable
fun ValueSlider(
    value                 : Float,
    onValueChange         : (Float) -> Unit,
    modifier              : Modifier = Modifier,
    enabled               : Boolean = true,
    valueRange            : ClosedFloatingPointRange<Float> = 0f..1f,
    steps                 : Int = 0,
    onValueChangeFinished : (() -> Unit)? = null,
    colors                : SliderColors = SliderDefaults.colors(),
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    Slider(
        state                 = state,
        modifier              = modifier,
        enabled               = enabled,
        onValueChange         = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors                = colors,
    )
}
