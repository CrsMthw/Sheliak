package com.crsmthw.sheliak.ui.screens.settings

import com.crsmthw.sheliak.data.local.SheliakDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscodeBitrateTest {

    @Test
    fun `the four rates are offered lowest first`() {
        assertEquals(listOf(128, 192, 256, 320), TranscodeBitrateChoicesKbps)
    }

    @Test
    fun `the stored default is one of the choices`() {
        assertTrue(SheliakDataStore.TRANSCODE_BITRATE_DEFAULT_KBPS in TranscodeBitrateChoicesKbps)
    }

    @Test
    fun `an offered rate selects itself`() {
        TranscodeBitrateChoicesKbps.forEach { assertEquals(it, transcodeBitrateChoiceFor(it)) }
    }

    @Test
    fun `any other rate selects the nearest, the lower on a tie`() {
        assertEquals(128, transcodeBitrateChoiceFor(64))
        assertEquals(192, transcodeBitrateChoiceFor(200))
        assertEquals(128, transcodeBitrateChoiceFor(160))
        assertEquals(320, transcodeBitrateChoiceFor(1_411))
    }
}
