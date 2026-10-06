package com.crsmthw.sheliak.player

import androidx.media3.common.Player
import com.crsmthw.sheliak.domain.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatModeMappingTest {

    @Test
    fun `our repeat modes round-trip through Media3's`() {
        RepeatMode.entries.forEach { assertEquals(it, repeatModeOf(it.toPlayerRepeatMode()), it.name) }
    }

    @Test
    fun `each mode maps to its Media3 constant`() {
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.OFF.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode())
        assertEquals(RepeatMode.OFF, repeatModeOf(42))
    }
}
