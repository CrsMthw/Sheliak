package com.crsmthw.sheliak.ui.screens.sources

import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.plex.PlexLibrary
import com.crsmthw.sheliak.data.provider.plex.PlexServerCandidate

/*
 * The Plex setup flow's steps and its pure rules (unit-tested in PlexSetupFlowTest). The flow is ONE navigation
 * entry; these steps are its internal state, driven by PlexSetupViewModel through the PlexSetup facade.
 */

/** The setup's steps, in order. */
enum class PlexSetupStep {
    /** Checking a stored account token (a second server needs no new sign-in). */
    ACCOUNT,
    /** Sign in on plex.tv: the PIN code, "Open plex.tv", polling until the account token arrives. */
    PIN,
    /** Pick one of the account's servers (owned first). */
    SERVERS,
    /** Android 17's local-network permission, asked before the server is probed — only when it is needed. */
    LOCAL_NETWORK,
    /** Pick the server's music libraries (all on to start). */
    LIBRARIES,
    /** Storing the tokens and adding the source. */
    FINISHING,
    /** Added: the screen leaves the flow. */
    DONE,
}

/** Everything the setup screen draws. [busy] = the step's work is in flight; [error] = it failed (with Retry). */
@Immutable
data class PlexSetupUiState(
    val step      : PlexSetupStep = PlexSetupStep.ACCOUNT,
    val busy      : Boolean = true,
    val error     : ProviderError? = null,
    val pinCode   : String? = null,
    val authUrl   : String? = null,
    val servers   : List<PlexServerCandidate> = emptyList(),
    val server    : PlexServerCandidate? = null,
    val permission: String? = null,
    val libraries : List<PlexLibrary> = emptyList(),
    val selected  : Set<String> = emptySet(),
)

/** What system back does inside the flow. */
enum class PlexSetupBack {
    /** Leave the flow (pop the entry). */
    LEAVE,
    /** Return to the server list. */
    TO_SERVERS,
    /** Nothing: the source is being added, and leaving now would abandon it half-way. */
    IGNORE,
}

/** Back from [step]: the library and permission steps return to the servers; finishing holds; else leave. */
fun plexSetupBack(step: PlexSetupStep): PlexSetupBack = when (step) {
    PlexSetupStep.LOCAL_NETWORK,
    PlexSetupStep.LIBRARIES -> PlexSetupBack.TO_SERVERS
    PlexSetupStep.FINISHING -> PlexSetupBack.IGNORE
    PlexSetupStep.ACCOUNT,
    PlexSetupStep.PIN,
    PlexSetupStep.SERVERS,
    PlexSetupStep.DONE      -> PlexSetupBack.LEAVE
}

/** The step after picking a server: the permission step when a permission must be asked first, else libraries. */
fun stepAfterServer(permission: String?): PlexSetupStep =
    if (permission != null) PlexSetupStep.LOCAL_NETWORK else PlexSetupStep.LIBRARIES

/**
 * Whether a failed PIN wait means "start a new PIN": AUTH is how the facade reports an expired PIN (any other
 * failure is transient and the facade keeps polling through it).
 */
fun pinExpired(error: ProviderError): Boolean = error == ProviderError.AUTH

/** Every library picked: the starting selection. */
fun allLibraryKeys(libraries: List<PlexLibrary>): Set<String> = libraries.mapTo(LinkedHashSet()) { it.key }

/** [selected] with [key] flipped. */
fun toggledLibrary(selected: Set<String>, key: String): Set<String> =
    if (key in selected) selected - key else selected + key

/** The picked libraries, in the server's own order. */
fun chosenLibraries(libraries: List<PlexLibrary>, selected: Set<String>): List<PlexLibrary> =
    libraries.filter { it.key in selected }

/** "Add" is offered only with at least one library picked. */
fun canFinish(libraries: List<PlexLibrary>, selected: Set<String>): Boolean =
    chosenLibraries(libraries, selected).isNotEmpty()
