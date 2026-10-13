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
    /** Checking a stored account token (a second server, or one added again, needs no new sign-in). */
    ACCOUNT,
    /**
     * Sign in on plex.tv: one "Sign in with Plex" button that opens the auth page in a Custom Tab, then a still
     * "Waiting for plex.tv…" line while the PIN is polled. The PIN's code is NEVER shown (a strong PIN that the
     * auth URL carries; Cris, M1 fix round 1).
     */
    SIGN_IN,
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

/** Where the sign-in step stands. */
enum class PlexSignIn {
    /** Nothing started: the headline and "Sign in with Plex". Every way into the step lands here. */
    IDLE,
    /** "Sign in with Plex" was tapped: a PIN is being requested (the button holds, disabled). */
    STARTING,
    /** The auth page is open (or was): plex.tv is polled until it hands over the account token. */
    WAITING,
}

/**
 * Everything the setup screen draws. [busy] = the step's work is in flight; [error] = it failed (with Retry).
 * [authUrl] is the current PIN's auth page (no code is kept here: nothing may show it); [openAuth] is a one-shot
 * request for the screen to open [authUrl] in a Custom Tab, cleared by `PlexSetupViewModel.authOpened` — state,
 * not an event, so a rotation between the request and its handling opens the page exactly once.
 */
@Immutable
data class PlexSetupUiState(
    val step      : PlexSetupStep = PlexSetupStep.ACCOUNT,
    val busy      : Boolean = true,
    val error     : ProviderError? = null,
    val signIn    : PlexSignIn = PlexSignIn.IDLE,
    val authUrl   : String? = null,
    val openAuth  : Boolean = false,
    val servers   : List<PlexServerCandidate> = emptyList(),
    val server    : PlexServerCandidate? = null,
    val permission: String? = null,
    val libraries : List<PlexLibrary> = emptyList(),
    val selected  : Set<String> = emptySet(),
)

/** What a tap on "Sign in with Plex" does. */
enum class PlexSignInTap {
    /** Request a PIN, then open its auth page. */
    START,
    /** Open the current PIN's auth page again (the user closed the tab, or the PIN was silently renewed). */
    REOPEN,
    /** Nothing: a PIN is already being requested. */
    IGNORE,
}

/** The tap's meaning in [phase]: idle starts; waiting reopens the live page (starts again if there is none). */
fun plexSignInTap(phase: PlexSignIn, authUrl: String?): PlexSignInTap = when (phase) {
    PlexSignIn.IDLE     -> PlexSignInTap.START
    PlexSignIn.STARTING -> PlexSignInTap.IGNORE
    PlexSignIn.WAITING  -> if (authUrl != null) PlexSignInTap.REOPEN else PlexSignInTap.START
}

/** What the sign-in step draws besides its headline. */
@Immutable
data class PlexSignInView(
    /** "Sign in with Plex" is shown. */
    val button       : Boolean,
    /** …and can be tapped. */
    val buttonEnabled: Boolean,
    /** The still "Waiting for plex.tv…" line is shown. */
    val waiting      : Boolean,
)

/**
 * The sign-in step's content: a failure shows only its error line + Retry (the screen's shared error block);
 * otherwise the button — held while the PIN is requested — and, once the page is open, the waiting line under it
 * (the button stays, so a closed tab or a silently renewed PIN is one tap away).
 */
fun plexSignInView(phase: PlexSignIn, error: ProviderError?): PlexSignInView = when {
    error != null                -> PlexSignInView(button = false, buttonEnabled = false, waiting = false)
    phase == PlexSignIn.IDLE     -> PlexSignInView(button = true, buttonEnabled = true, waiting = false)
    phase == PlexSignIn.STARTING -> PlexSignInView(button = true, buttonEnabled = false, waiting = false)
    else                         -> PlexSignInView(button = true, buttonEnabled = true, waiting = true)
}

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
    PlexSetupStep.SIGN_IN,
    PlexSetupStep.SERVERS,
    PlexSetupStep.DONE      -> PlexSetupBack.LEAVE
}

/** The step after picking a server: the permission step when a permission must be asked first, else libraries. */
fun stepAfterServer(permission: String?): PlexSetupStep =
    if (permission != null) PlexSetupStep.LOCAL_NETWORK else PlexSetupStep.LIBRARIES

/**
 * Whether a failed PIN wait means "renew the PIN silently": AUTH is how the facade reports an expired PIN (any
 * other failure is transient and the facade keeps polling through it).
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
