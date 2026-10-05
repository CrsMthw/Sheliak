package com.crsmthw.sheliak.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * The colour scheme shown when Material You is off: ONE accent seed → every [ColorScheme] role, generated with
 * the algorithm Android's Material You runs on the wallpaper colour (HCT colour space + the Tonal Spot
 * variant). A hand-written static scheme could not follow the accent picker, and anything it left out — the
 * `surfaceContainer*` and `*Fixed` roles above all — would silently stay at M3's baseline purple.
 *
 * Every role is passed to the full [ColorScheme] constructor explicitly — it has no defaults to fall back on.
 * When Material 3 last added roles (the `*Fixed` ones) it deprecated the old constructor, so a future role shows
 * up as a deprecation on this call and as a failure of SeedColorSchemeTest's role-coverage test rather than as a
 * silently baseline-coloured role. Pure Kotlin (no `android.*`), unit-tested in `SeedColorSchemeTest`.
 */
object SeedColorScheme {

    /** Standard contrast (0.0): the level Material You generates by default, so both colour sources match. */
    private const val STANDARD_CONTRAST = 0.0

    /** [seedArgb] is an sRGB ARGB int (the stored `accent_color`); alpha is ignored by the algorithm. */
    fun from(seedArgb: Int, dark: Boolean): ColorScheme {
        val s = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seedArgb),
            isDark         = dark,
            contrastLevel  = STANDARD_CONTRAST,
            // Pinned, not left to the library default, so a dependency bump cannot silently restyle the app.
            // 2021 is the spec Compose's own baseline tokens follow (the default purple seed's dark `surface`
            // is exactly M3's baseline 0xFF141218); 2025 darkens the dark surfaces further and merges
            // `surfaceVariant` into `surfaceContainerHighest` — a different look, to be adopted deliberately.
            specVersion    = ColorSpec.SpecVersion.SPEC_2021,
            platform       = DynamicScheme.Platform.PHONE,
        )
        return ColorScheme(
            primary                 = Color(s.primary),
            onPrimary               = Color(s.onPrimary),
            primaryContainer        = Color(s.primaryContainer),
            onPrimaryContainer      = Color(s.onPrimaryContainer),
            inversePrimary          = Color(s.inversePrimary),
            secondary               = Color(s.secondary),
            onSecondary             = Color(s.onSecondary),
            secondaryContainer      = Color(s.secondaryContainer),
            onSecondaryContainer    = Color(s.onSecondaryContainer),
            tertiary                = Color(s.tertiary),
            onTertiary              = Color(s.onTertiary),
            tertiaryContainer       = Color(s.tertiaryContainer),
            onTertiaryContainer     = Color(s.onTertiaryContainer),
            background              = Color(s.background),
            onBackground            = Color(s.onBackground),
            surface                 = Color(s.surface),
            onSurface               = Color(s.onSurface),
            surfaceVariant          = Color(s.surfaceVariant),
            onSurfaceVariant        = Color(s.onSurfaceVariant),
            surfaceTint             = Color(s.surfaceTint),
            inverseSurface          = Color(s.inverseSurface),
            inverseOnSurface        = Color(s.inverseOnSurface),
            error                   = Color(s.error),
            onError                 = Color(s.onError),
            errorContainer          = Color(s.errorContainer),
            onErrorContainer        = Color(s.onErrorContainer),
            outline                 = Color(s.outline),
            outlineVariant          = Color(s.outlineVariant),
            scrim                   = Color(s.scrim),
            surfaceBright           = Color(s.surfaceBright),
            surfaceContainer        = Color(s.surfaceContainer),
            surfaceContainerHigh    = Color(s.surfaceContainerHigh),
            surfaceContainerHighest = Color(s.surfaceContainerHighest),
            surfaceContainerLow     = Color(s.surfaceContainerLow),
            surfaceContainerLowest  = Color(s.surfaceContainerLowest),
            surfaceDim              = Color(s.surfaceDim),
            primaryFixed            = Color(s.primaryFixed),
            primaryFixedDim         = Color(s.primaryFixedDim),
            onPrimaryFixed          = Color(s.onPrimaryFixed),
            onPrimaryFixedVariant   = Color(s.onPrimaryFixedVariant),
            secondaryFixed          = Color(s.secondaryFixed),
            secondaryFixedDim       = Color(s.secondaryFixedDim),
            onSecondaryFixed        = Color(s.onSecondaryFixed),
            onSecondaryFixedVariant = Color(s.onSecondaryFixedVariant),
            tertiaryFixed           = Color(s.tertiaryFixed),
            tertiaryFixedDim        = Color(s.tertiaryFixedDim),
            onTertiaryFixed         = Color(s.onTertiaryFixed),
            onTertiaryFixedVariant  = Color(s.onTertiaryFixedVariant),
        )
    }
}
