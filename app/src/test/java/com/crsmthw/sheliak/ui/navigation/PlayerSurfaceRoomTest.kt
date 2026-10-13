package com.crsmthw.sheliak.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSurfaceRoomTest {

    private val inset = 88.dp

    @Test
    fun `the library and the details keep the mini bar's room`() {
        listOf(Library, AlbumDetail("p", "1"), ArtistDetail("p", "2"), PlaylistDetail(3L)).forEach { key ->
            assertEquals(inset, playerSurfaceRoomFor(key, inset), "$key")
        }
    }

    @Test
    fun `screens without the surface keep none, whatever inset the host holds`() {
        listOf(Intro, Settings, Sources, PlexSetup, Player, Queue).forEach { key ->
            assertEquals(0.dp, playerSurfaceRoomFor(key, inset), "$key")
        }
    }

    @Test
    fun `no mini bar means no room anywhere`() {
        AllNavKeys.forEach { key -> assertEquals(0.dp, playerSurfaceRoomFor(key, 0.dp), "$key") }
    }

    @Test
    fun `no entry keeps no room`() {
        assertEquals(0.dp, playerSurfaceRoomFor(null, inset))
    }

    @Test
    fun `the room follows the player surface rule for every key`() {
        AllNavKeys.forEach { key ->
            val expected = if (playerSurfaceAllowed(key)) inset else 0.dp
            assertEquals(expected, playerSurfaceRoomFor(key, inset), "$key")
        }
    }
}
