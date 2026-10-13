package com.crsmthw.sheliak.ui.screens.sources

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.plex.PlexServerCandidate
import com.crsmthw.sheliak.data.provider.plex.PlexSetup
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Plex setup flow's state machine ([PlexSetupStep]) over ONE [PlexSetup] facade held for the whole flow (the
 * facade remembers which connection answered while listing libraries, which `complete` then stores):
 *
 * 1. ACCOUNT — a stored account token that plex.tv still accepts (or cannot be asked about) skips the sign-in.
 *    The token outlives the servers added with it, so adding a server again skips the sign-in too.
 * 2. SIGN_IN — lands IDLE (the button) from every way in; only [signIn] (the user's tap) requests a strong PIN,
 *    after which the screen opens its auth URL in a Custom Tab ([PlexSetupUiState.openAuth]) and
 *    [PlexSetup.awaitPin] polls until the account token arrives. An expired PIN ([pinExpired]) is renewed
 *    silently (a tap then reopens the new page); a failed PIN request shows its error with Retry. No code is
 *    ever shown.
 * 3. SERVERS — the account's servers, owned first; a refused token goes back to SIGN_IN (idle).
 * 4. LOCAL_NETWORK — only when [PlexSetup.localNetworkPermission] names one; granted or denied, the flow goes on.
 * 5. LIBRARIES — the server's music libraries, all picked to start.
 * 6. FINISHING → DONE — `complete`, then `SourcesRepository.add` (which schedules the periodic sync and runs one).
 *
 * Every failure parks the step with its [ProviderError] and [retry] re-runs that step. Work runs in
 * [viewModelScope], one job at a time: a new step cancels the old one's work.
 */
class PlexSetupViewModel(
    application: Application,
    container  : AppContainer,
) : ViewModel() {

    private val setup = PlexSetup(application, container.credentialStore, container.httpClient, container.json)
    private val sources: SourcesRepository = container.sourcesRepository

    private val _state = MutableStateFlow(PlexSetupUiState())
    val state: StateFlow<PlexSetupUiState> = _state.asStateFlow()

    private var accountToken: String? = null
    private var work: Job? = null

    init {
        checkAccount()
    }

    /** Re-runs the step that failed. */
    fun retry() {
        when (_state.value.step) {
            PlexSetupStep.ACCOUNT       -> checkAccount()
            PlexSetupStep.SIGN_IN       -> startSignIn()
            PlexSetupStep.SERVERS       -> loadServers()
            PlexSetupStep.LOCAL_NETWORK -> Unit
            PlexSetupStep.LIBRARIES     -> loadLibraries()
            PlexSetupStep.FINISHING     -> finish()
            PlexSetupStep.DONE          -> Unit
        }
    }

    /**
     * "Sign in with Plex" was tapped ([plexSignInTap]): request a PIN and open its page, or reopen the live page
     * while plex.tv is being waited on.
     */
    fun signIn() {
        val current = _state.value
        when (plexSignInTap(current.signIn, current.authUrl)) {
            PlexSignInTap.START  -> startSignIn()
            PlexSignInTap.REOPEN -> _state.update { it.copy(openAuth = true) }
            PlexSignInTap.IGNORE -> Unit
        }
    }

    /** The screen handled [PlexSetupUiState.openAuth] (opened the page, or found no browser). */
    fun authOpened() {
        _state.update { it.copy(openAuth = false) }
    }

    /** A server was picked: ask for the local-network permission first when it is needed, else list libraries. */
    fun pickServer(server: PlexServerCandidate) {
        val permission = setup.localNetworkPermission(server)
        _state.update { it.copy(server = server, permission = permission, error = null) }
        when (stepAfterServer(permission)) {
            PlexSetupStep.LOCAL_NETWORK -> _state.update { it.copy(step = PlexSetupStep.LOCAL_NETWORK, busy = false) }
            else                        -> loadLibraries()
        }
    }

    /** The permission dialog answered — granted or denied alike, the flow goes on (a denial only skips LAN). */
    fun onLocalNetworkAnswered() = loadLibraries()

    fun toggleLibrary(key: String) {
        _state.update { it.copy(selected = toggledLibrary(it.selected, key)) }
    }

    /** In-flow back ([plexSetupBack]); returns false when back should leave the flow instead. */
    fun back(): Boolean = when (plexSetupBack(_state.value.step)) {
        PlexSetupBack.LEAVE      -> false
        PlexSetupBack.IGNORE     -> true
        PlexSetupBack.TO_SERVERS -> {
            work?.cancel()
            _state.update { it.copy(step = PlexSetupStep.SERVERS, busy = false, error = null) }
            true
        }
    }

    /** Stores the tokens and adds the source with the picked libraries. */
    fun finish() {
        val current = _state.value
        val server = current.server ?: return
        val token = accountToken ?: return showSignIn()
        val libraries = chosenLibraries(current.libraries, current.selected)
        if (libraries.isEmpty()) return
        launchStep(PlexSetupStep.FINISHING) {
            val instance = setup.complete(token, server, libraries).getOrElse { fail(it); return@launchStep }
            sources.add(instance)
                .onSuccess { _state.update { it.copy(step = PlexSetupStep.DONE, busy = false) } }
                .onFailure(::fail)
        }
    }

    private fun checkAccount() {
        launchStep(PlexSetupStep.ACCOUNT) {
            val stored = setup.storedAccountToken()
            // Refused (false) → sign in again; valid, or plex.tv could not be asked → keep the stored token.
            if (stored != null && setup.checkAccountToken(stored).getOrNull() != false) {
                accountToken = stored
                listServers(stored)
            } else {
                idleSignIn()
            }
        }
    }

    /** Into SIGN_IN from outside a step's work: stop that work and land idle on the button. */
    private fun showSignIn() {
        work?.cancel()
        idleSignIn()
    }

    /** SIGN_IN, idle: the headline and "Sign in with Plex"; nothing is requested until the user taps. */
    private fun idleSignIn() {
        _state.update {
            it.copy(
                step     = PlexSetupStep.SIGN_IN,
                busy     = false,
                error    = null,
                signIn   = PlexSignIn.IDLE,
                authUrl  = null,
                openAuth = false,
            )
        }
    }

    private fun startSignIn() {
        launchStep(PlexSetupStep.SIGN_IN) { runSignIn() }
    }

    /**
     * Requests a PIN and asks the screen to open its page, then waits; an expired PIN is renewed silently (the
     * page is NOT reopened — the button does that); a failed PIN request parks the step on its error.
     */
    private suspend fun runSignIn() {
        var open = true
        while (true) {
            _state.update {
                it.copy(
                    step   = PlexSetupStep.SIGN_IN,
                    busy   = true,
                    error  = null,
                    signIn = if (open) PlexSignIn.STARTING else PlexSignIn.WAITING,
                )
            }
            val session = setup.startPin().getOrElse { failure ->
                _state.update { it.copy(signIn = PlexSignIn.IDLE, authUrl = null, openAuth = false) }
                fail(failure)
                return
            }
            val openNow = open
            _state.update {
                it.copy(
                    busy     = false,
                    signIn   = PlexSignIn.WAITING,
                    authUrl  = session.authUrl,
                    openAuth = it.openAuth || openNow,
                )
            }
            open = false
            val claimed = setup.awaitPin(session)
            val token = claimed.getOrNull()
            if (token != null) {
                accountToken = token
                listServers(token)
                return
            }
            val error = claimed.exceptionOrNull()?.let { ProviderError.of(it) } ?: ProviderError.UNKNOWN
            if (!pinExpired(error)) {
                _state.update { it.copy(signIn = PlexSignIn.IDLE, authUrl = null, openAuth = false) }
                fail(error)
                return
            }
        }
    }

    private fun loadServers() {
        val token = accountToken ?: return showSignIn()
        launchStep(PlexSetupStep.SERVERS) { listServers(token) }
    }

    private suspend fun listServers(token: String) {
        _state.update { it.copy(step = PlexSetupStep.SERVERS, busy = true, error = null) }
        setup.listServers(token)
            .onSuccess { servers -> _state.update { it.copy(servers = servers, busy = false) } }
            .onFailure { failure ->
                if (ProviderError.of(failure) == ProviderError.AUTH) {
                    // The stored token was revoked: sign in again (the button; nothing starts by itself).
                    accountToken = null
                    idleSignIn()
                } else {
                    fail(failure)
                }
            }
    }

    private fun loadLibraries() {
        val server = _state.value.server ?: return
        launchStep(PlexSetupStep.LIBRARIES) {
            _state.update { it.copy(libraries = emptyList(), selected = emptySet()) }
            setup.listMusicLibraries(server)
                .onSuccess { libraries ->
                    _state.update { it.copy(libraries = libraries, selected = allLibraryKeys(libraries), busy = false) }
                }
                .onFailure(::fail)
        }
    }

    /** Cancels the previous step's work and runs [block] as [step], busy until it says otherwise. */
    private fun launchStep(step: PlexSetupStep, block: suspend () -> Unit) {
        work?.cancel()
        _state.update { it.copy(step = step, busy = true, error = null) }
        work = viewModelScope.launch { block() }
    }

    private fun fail(failure: Throwable) = fail(ProviderError.of(failure))

    private fun fail(error: ProviderError) {
        _state.update { it.copy(busy = false, error = error) }
    }
}

/**
 * Builds [PlexSetupViewModel] from the app container and the Application the creation extras carry
 * (`APPLICATION_KEY` — the container keeps its own context private). SettingsViewModelFactory's shape, on the
 * extras-taking `create`.
 */
class PlexSetupViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(PlexSetupViewModel::class.java)) {
            "PlexSetupViewModelFactory cannot create ${modelClass.name}"
        }
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
            "PlexSetupViewModelFactory needs the Application in its creation extras"
        }
        return checkNotNull(modelClass.cast(PlexSetupViewModel(application, container)))
    }
}
