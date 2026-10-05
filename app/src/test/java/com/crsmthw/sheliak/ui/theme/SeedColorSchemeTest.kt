package com.crsmthw.sheliak.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SeedColorSchemeTest {

    /** One ColorScheme role: its name, and how to read it from Compose's scheme and from the generator's. */
    private class Role(
        val name: String,
        val compose: (ColorScheme) -> Color,
        val generated: (DynamicScheme) -> Int,
    )

    /** Written out independently of SeedColorScheme, so a role wired to the wrong generator role fails here. */
    private val roles = listOf(
        Role("primary",                 { it.primary },                 { it.primary }),
        Role("onPrimary",               { it.onPrimary },               { it.onPrimary }),
        Role("primaryContainer",        { it.primaryContainer },        { it.primaryContainer }),
        Role("onPrimaryContainer",      { it.onPrimaryContainer },      { it.onPrimaryContainer }),
        Role("inversePrimary",          { it.inversePrimary },          { it.inversePrimary }),
        Role("secondary",               { it.secondary },               { it.secondary }),
        Role("onSecondary",             { it.onSecondary },             { it.onSecondary }),
        Role("secondaryContainer",      { it.secondaryContainer },      { it.secondaryContainer }),
        Role("onSecondaryContainer",    { it.onSecondaryContainer },    { it.onSecondaryContainer }),
        Role("tertiary",                { it.tertiary },                { it.tertiary }),
        Role("onTertiary",              { it.onTertiary },              { it.onTertiary }),
        Role("tertiaryContainer",       { it.tertiaryContainer },       { it.tertiaryContainer }),
        Role("onTertiaryContainer",     { it.onTertiaryContainer },     { it.onTertiaryContainer }),
        Role("background",              { it.background },              { it.background }),
        Role("onBackground",            { it.onBackground },            { it.onBackground }),
        Role("surface",                 { it.surface },                 { it.surface }),
        Role("onSurface",               { it.onSurface },               { it.onSurface }),
        Role("surfaceVariant",          { it.surfaceVariant },          { it.surfaceVariant }),
        Role("onSurfaceVariant",        { it.onSurfaceVariant },        { it.onSurfaceVariant }),
        Role("surfaceTint",             { it.surfaceTint },             { it.surfaceTint }),
        Role("inverseSurface",          { it.inverseSurface },          { it.inverseSurface }),
        Role("inverseOnSurface",        { it.inverseOnSurface },        { it.inverseOnSurface }),
        Role("error",                   { it.error },                   { it.error }),
        Role("onError",                 { it.onError },                 { it.onError }),
        Role("errorContainer",          { it.errorContainer },          { it.errorContainer }),
        Role("onErrorContainer",        { it.onErrorContainer },        { it.onErrorContainer }),
        Role("outline",                 { it.outline },                 { it.outline }),
        Role("outlineVariant",          { it.outlineVariant },          { it.outlineVariant }),
        Role("scrim",                   { it.scrim },                   { it.scrim }),
        Role("surfaceBright",           { it.surfaceBright },           { it.surfaceBright }),
        Role("surfaceContainer",        { it.surfaceContainer },        { it.surfaceContainer }),
        Role("surfaceContainerHigh",    { it.surfaceContainerHigh },    { it.surfaceContainerHigh }),
        Role("surfaceContainerHighest", { it.surfaceContainerHighest }, { it.surfaceContainerHighest }),
        Role("surfaceContainerLow",     { it.surfaceContainerLow },     { it.surfaceContainerLow }),
        Role("surfaceContainerLowest",  { it.surfaceContainerLowest },  { it.surfaceContainerLowest }),
        Role("surfaceDim",              { it.surfaceDim },              { it.surfaceDim }),
        Role("primaryFixed",            { it.primaryFixed },            { it.primaryFixed }),
        Role("primaryFixedDim",         { it.primaryFixedDim },         { it.primaryFixedDim }),
        Role("onPrimaryFixed",          { it.onPrimaryFixed },          { it.onPrimaryFixed }),
        Role("onPrimaryFixedVariant",   { it.onPrimaryFixedVariant },   { it.onPrimaryFixedVariant }),
        Role("secondaryFixed",          { it.secondaryFixed },          { it.secondaryFixed }),
        Role("secondaryFixedDim",       { it.secondaryFixedDim },       { it.secondaryFixedDim }),
        Role("onSecondaryFixed",        { it.onSecondaryFixed },        { it.onSecondaryFixed }),
        Role("onSecondaryFixedVariant", { it.onSecondaryFixedVariant }, { it.onSecondaryFixedVariant }),
        Role("tertiaryFixed",           { it.tertiaryFixed },           { it.tertiaryFixed }),
        Role("tertiaryFixedDim",        { it.tertiaryFixedDim },        { it.tertiaryFixedDim }),
        Role("onTertiaryFixed",         { it.onTertiaryFixed },         { it.onTertiaryFixed }),
        Role("onTertiaryFixedVariant",  { it.onTertiaryFixedVariant },  { it.onTertiaryFixedVariant }),
    )

    private val seeds = AccentPresets.map { it.argb } + listOf(0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())

    private val green = 0xFF43A047.toInt()
    private val pink  = 0xFFD81B60.toInt()

    private fun generated(seed: Int, dark: Boolean): DynamicScheme = SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(seed),
        isDark         = dark,
        contrastLevel  = 0.0,
        specVersion    = ColorSpec.SpecVersion.SPEC_2021,
        platform       = DynamicScheme.Platform.PHONE,
    )

    private fun ColorScheme.argbOf(role: Role): Int = role.compose(this).toArgb()

    // ── Mapping ──────────────────────────────────────────────────────────────

    @Test
    fun `the role list covers every colour role ColorScheme has`() {
        // Colour getters are the public no-argument methods returning the packed Color (a long on the JVM),
        // name-mangled as getPrimary-<hash> because Color is a value class.
        val colourGetters = ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
            .map { m -> m.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercaseChar() } }
            .toSet()
        assertEquals(
            colourGetters,
            roles.map { it.name }.toSet(),
            "ColorScheme gained or lost a role: map it in SeedColorScheme and list it here",
        )
        assertEquals(48, roles.size)
    }

    @Test
    fun `every role is the same-named Tonal Spot role`() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = SeedColorScheme.from(seed, dark)
            val expected = generated(seed, dark)
            for (role in roles) {
                assertEquals(
                    role.generated(expected),
                    scheme.argbOf(role),
                    "${role.name} for seed ${seed.toUInt().toString(16)} dark=$dark",
                )
            }
        }
    }

    @Test
    fun `the same seed and mode always give the same scheme`() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val a = SeedColorScheme.from(seed, dark)
            val b = SeedColorScheme.from(seed, dark)
            for (role in roles) assertEquals(a.argbOf(role), b.argbOf(role), role.name)
        }
    }

    @Test
    fun `no role is left unspecified and every role is opaque`() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = SeedColorScheme.from(seed, dark)
            for (role in roles) {
                val colour = role.compose(scheme)
                assertNotEquals(Color.Unspecified, colour, "${role.name} dark=$dark")
                assertEquals(1f, colour.alpha, "${role.name} dark=$dark")
            }
        }
    }

    @Test
    fun `light and dark differ in every role except the mode-independent ones`() {
        // The *Fixed roles are the same in both modes by definition, and the scrim is always black.
        val modeIndependent = setOf(
            "scrim",
            "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
            "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
            "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
        )
        for (seed in AccentPresets.map { it.argb }) {
            val light = SeedColorScheme.from(seed, dark = false)
            val dark = SeedColorScheme.from(seed, dark = true)
            for (role in roles) {
                if (role.name in modeIndependent) {
                    assertEquals(light.argbOf(role), dark.argbOf(role), role.name)
                } else {
                    assertNotEquals(light.argbOf(role), dark.argbOf(role), "${role.name} for ${seed.toUInt().toString(16)}")
                }
            }
        }
    }

    @Test
    fun `two distant seeds differ in every role except the seed-independent ones`() {
        // Error roles come from a fixed red palette and the scrim is always black. Dark mode only: in light mode
        // several on-colours and surfaceContainerLowest sit at tone 100, white for every seed.
        val seedIndependent = setOf("error", "onError", "errorContainer", "onErrorContainer", "scrim")
        val a = SeedColorScheme.from(green, dark = true)
        val b = SeedColorScheme.from(pink, dark = true)
        for (role in roles) {
            if (role.name in seedIndependent) {
                assertEquals(a.argbOf(role), b.argbOf(role), role.name)
            } else {
                assertNotEquals(a.argbOf(role), b.argbOf(role), role.name)
            }
        }
    }

    @Test
    fun `primary keeps the seed's hue`() {
        // Every chromatic preset (all ten): Tonal Spot keeps the seed's HCT hue and sets chroma and tone itself.
        for (seed in AccentPresets.map { it.argb }) for (dark in listOf(false, true)) {
            val seedHue = Hct.fromInt(seed).hue
            val primaryHue = Hct.fromInt(SeedColorScheme.from(seed, dark).primary.toArgb()).hue
            val d = abs(seedHue - primaryHue) % 360.0
            assertTrue(minOf(d, 360.0 - d) < 2.0, "seed ${seed.toUInt().toString(16)}: $seedHue vs $primaryHue")
        }
    }

    @Test
    fun `the default accent gives M3's baseline dark surface`() {
        // Pins the spec version: under the 2021 spec the default purple lands exactly on M3's baseline tokens.
        assertEquals(0xFF141218.toInt(), SeedColorScheme.from(ACCENT_DEFAULT_ARGB, dark = true).surface.toArgb())
    }

    // ── AMOLED overlay ───────────────────────────────────────────────────────

    @Test
    fun `the AMOLED overlay changes background, surface and surfaceVariant only`() {
        val overlaid = setOf("background", "surface", "surfaceVariant")
        for (seed in seeds) {
            val base = SeedColorScheme.from(seed, dark = true)
            val amoled = base.withAmoledOverlay(darkTheme = true, amoledBlack = true)
            assertEquals(Color.Black, amoled.background)
            assertEquals(Color.Black, amoled.surface)
            assertEquals(Color(0xFF0D0D0D), amoled.surfaceVariant)
            for (role in roles.filter { it.name !in overlaid }) {
                assertEquals(base.argbOf(role), amoled.argbOf(role), role.name)
            }
        }
    }

    @Test
    fun `the AMOLED overlay does nothing in light mode or with the switch off`() {
        val light = SeedColorScheme.from(ACCENT_DEFAULT_ARGB, dark = false)
        val dark = SeedColorScheme.from(ACCENT_DEFAULT_ARGB, dark = true)
        assertSame(light, light.withAmoledOverlay(darkTheme = false, amoledBlack = true))
        assertSame(light, light.withAmoledOverlay(darkTheme = false, amoledBlack = false))
        assertSame(dark, dark.withAmoledOverlay(darkTheme = true, amoledBlack = false))
    }

    // ── Mode ─────────────────────────────────────────────────────────────────

    @Test
    fun `only SYSTEM follows the system dark flag`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertTrue(!ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(!ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }
}
