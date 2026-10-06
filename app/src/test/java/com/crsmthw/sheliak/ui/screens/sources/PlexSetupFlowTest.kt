package com.crsmthw.sheliak.ui.screens.sources

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.plex.PlexLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlexSetupFlowTest {

    private val music = PlexLibrary(key = "3", title = "Music")
    private val classical = PlexLibrary(key = "7", title = "Classical")
    private val live = PlexLibrary(key = "9", title = "Live")

    @Test
    fun `the steps run in order`() {
        assertEquals(
            listOf("ACCOUNT", "PIN", "SERVERS", "LOCAL_NETWORK", "LIBRARIES", "FINISHING", "DONE"),
            PlexSetupStep.entries.map { it.name },
        )
    }

    @Test
    fun `a needed permission comes before the libraries`() {
        assertEquals(PlexSetupStep.LOCAL_NETWORK, stepAfterServer("android.permission.ACCESS_LOCAL_NETWORK"))
        assertEquals(PlexSetupStep.LIBRARIES, stepAfterServer(null))
    }

    @Test
    fun `back from the libraries or the permission returns to the servers`() {
        assertEquals(PlexSetupBack.TO_SERVERS, plexSetupBack(PlexSetupStep.LIBRARIES))
        assertEquals(PlexSetupBack.TO_SERVERS, plexSetupBack(PlexSetupStep.LOCAL_NETWORK))
    }

    @Test
    fun `back while adding does nothing`() {
        assertEquals(PlexSetupBack.IGNORE, plexSetupBack(PlexSetupStep.FINISHING))
    }

    @Test
    fun `back from the first steps leaves the flow`() {
        listOf(PlexSetupStep.ACCOUNT, PlexSetupStep.PIN, PlexSetupStep.SERVERS, PlexSetupStep.DONE).forEach { step ->
            assertEquals(PlexSetupBack.LEAVE, plexSetupBack(step), "$step")
        }
    }

    @Test
    fun `only AUTH means the PIN expired`() {
        assertTrue(pinExpired(ProviderError.AUTH))
        ProviderError.entries.filter { it != ProviderError.AUTH }.forEach { assertFalse(pinExpired(it), "$it") }
    }

    @Test
    fun `every library starts picked`() {
        assertEquals(setOf("3", "7", "9"), allLibraryKeys(listOf(music, classical, live)))
        assertEquals(emptySet(), allLibraryKeys(emptyList()))
    }

    @Test
    fun `toggling flips one library`() {
        val all = setOf("3", "7")
        assertEquals(setOf("7"), toggledLibrary(all, "3"))
        assertEquals(setOf("7", "3"), toggledLibrary(setOf("7"), "3"))
    }

    @Test
    fun `the chosen libraries keep the server's order`() {
        assertEquals(listOf(music, live), chosenLibraries(listOf(music, classical, live), setOf("9", "3")))
    }

    @Test
    fun `a stale pick of a library the server no longer lists is dropped`() {
        assertEquals(listOf(music), chosenLibraries(listOf(music), setOf("3", "42")))
    }

    @Test
    fun `adding needs at least one library`() {
        assertTrue(canFinish(listOf(music, classical), setOf("7")))
        assertFalse(canFinish(listOf(music, classical), emptySet()))
        assertFalse(canFinish(listOf(music), setOf("42")))
    }

    @Test
    fun `a fresh setup starts busy on the account check`() {
        val state = PlexSetupUiState()
        assertEquals(PlexSetupStep.ACCOUNT, state.step)
        assertTrue(state.busy)
    }
}
