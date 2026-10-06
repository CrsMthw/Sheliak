package com.crsmthw.sheliak.ui.screens.sources

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.provider.plex.PlexServerCandidate
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.fadeThrough
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.toggle

/** Wider than this the steps stop growing, so an unfolded screen keeps a readable column. */
private val SetupMaxWidth = 560.dp

/**
 * Add a Plex server — ONE navigation entry whose steps ([PlexSetupStep]) cross-fade in place with M3 fade through:
 * sign in (the PIN, large, and "Open plex.tv" in a Custom Tab; skipped when a stored account token still works),
 * pick a server, allow the local network when Android asks for it, pick the music libraries, add. Every failure
 * shows its words with Retry. Back steps inside the flow first (libraries → servers); from the first steps it
 * leaves, and while the source is being added it holds. When the source is added, [onFinished] pops the entry —
 * back to Sources, or to the library when the flow started from its empty state or from Intro.
 */
@Composable
fun PlexSetupScreen(
    viewModel : PlexSetupViewModel,
    onBack    : () -> Unit,
    onFinished: () -> Unit,
    modifier  : Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val barState = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background

    LaunchedEffect(state.step) {
        if (state.step == PlexSetupStep.DONE) onFinished()
    }

    // In-flow back only while this entry is RESUMED (on top and settled), as the library's tab back does, so it
    // never outranks the navigation host's own back during a pop onto this screen.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inFlowBack = plexSetupBack(state.step) != PlexSetupBack.LEAVE
    BackHandler(enabled = inFlowBack && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) { viewModel.back() }

    Scaffold(
        modifier            = modifier,
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            RootTopBar(
                title          = stringResource(R.string.plex_setup_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { haptics.confirm(); if (!viewModel.back()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                containerColor = paneColor,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState    = state.step,
                    modifier       = Modifier.fillMaxSize(),
                    transitionSpec = { fadeThrough() },
                    label          = "plex-setup-step",
                ) { step ->
                    StepColumn(scrollBehavior.nestedScrollConnection) {
                        when (step) {
                            PlexSetupStep.ACCOUNT       -> BusyLine(R.string.plex_checking_account)
                            PlexSetupStep.PIN           -> PinStep(state)
                            PlexSetupStep.SERVERS       -> ServersStep(state, onPick = viewModel::pickServer)
                            PlexSetupStep.LOCAL_NETWORK -> LocalNetworkStep(state, onAnswered = viewModel::onLocalNetworkAnswered)
                            PlexSetupStep.LIBRARIES     -> LibrariesStep(
                                state    = state,
                                onToggle = viewModel::toggleLibrary,
                                onFinish = viewModel::finish,
                            )
                            PlexSetupStep.FINISHING,
                            PlexSetupStep.DONE          -> BusyLine(R.string.plex_finishing)
                        }
                        state.error?.takeIf { step == state.step }?.let { error ->
                            ErrorBlock(message = stringResource(providerErrorMessage(error)), onRetry = viewModel::retry)
                        }
                    }
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
            }
        }
    }
}

/** One step's scrolling column: the bar's connection OUTSIDE the scroller (Settings' load-bearing order). */
@Composable
private fun StepColumn(barScroll: NestedScrollConnection, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier            = Modifier
                .widthIn(max = SetupMaxWidth)
                .fillMaxSize()
                .nestedScroll(barScroll)
                .verticalScroll(rememberScrollState())
                .padding(top = BarContentGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            BottomFadeSpacer()
        }
    }
}

/** A step's heading. */
@Composable
private fun StepTitle(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
    )
}

/** A step's explanatory line. */
@Composable
private fun StepBody(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.bodyMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/**
 * Work in flight, as one still line — never an endless spinner (the app's finite-motion rule); the step's
 * content replaces it the moment the work lands.
 */
@Composable
private fun BusyLine(@StringRes textRes: Int) {
    StepBody(stringResource(textRes))
}

/** A failed step: its words in the error colour and Retry. */
@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { haptics.press(); onRetry() }) {
            Text(stringResource(R.string.plex_retry))
        }
    }
}

/**
 * Sign in: the PIN code, large, and "Open plex.tv" (a Custom Tab on the PIN's auth URL; plex.tv fills the code in
 * itself, and asks for it only on a device-link page). While the code is being requested, one line says so.
 */
@Composable
private fun PinStep(state: PlexSetupUiState) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var noBrowser by rememberSaveable { mutableStateOf(false) }
    StepTitle(stringResource(R.string.plex_pin_title))
    val code = state.pinCode
    val url = state.authUrl
    if (code == null || url == null) {
        if (state.error == null) BusyLine(R.string.plex_pin_requesting)
        return
    }
    StepBody(stringResource(R.string.plex_pin_body))
    Text(
        text          = code,
        style         = MaterialTheme.typography.displayMedium,
        fontFamily    = FontFamily.Monospace,
        letterSpacing = 6.sp,
        textAlign     = TextAlign.Center,
        modifier      = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick        = {
                haptics.confirm()
                noBrowser = try {
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
                    false
                } catch (_: ActivityNotFoundException) {
                    true
                }
            },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier           = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.plex_pin_open))
        }
    }
    if (noBrowser) {
        Text(
            text     = stringResource(R.string.plex_pin_no_browser),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
    StepBody(stringResource(R.string.plex_pin_waiting))
}

/** Pick a server: owned first (the facade's order); each with whose it is and an online dot. */
@Composable
private fun ServersStep(state: PlexSetupUiState, onPick: (PlexServerCandidate) -> Unit) {
    val haptics = LocalHapticFeedback.current
    StepTitle(stringResource(R.string.plex_servers_title))
    when {
        state.busy              -> BusyLine(R.string.plex_servers_loading)
        state.error != null     -> Unit
        state.servers.isEmpty() -> StepBody(stringResource(R.string.plex_servers_empty))
        else                    -> state.servers.forEach { server ->
            ListItem(
                leadingContent    = {
                    Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                content           = { Text(server.name) },
                supportingContent = { Text(serverOwnerLine(server), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent   = { OnlineDot(server.online) },
                modifier          = Modifier.clickable(role = Role.Button) { haptics.confirm(); onPick(server) },
            )
        }
    }
}

/** "Your server" for an owned one, "Shared by <owner>" (or just "Shared with you") otherwise. */
@Composable
private fun serverOwnerLine(server: PlexServerCandidate): String = when {
    server.owned             -> stringResource(R.string.plex_server_owned)
    server.ownerName != null -> stringResource(R.string.plex_server_shared_by, server.ownerName)
    else                     -> stringResource(R.string.plex_server_shared)
}

/** plex.tv's last word on whether the server is online: a filled dot, a hollow one, or nothing when unknown. */
@Composable
private fun OnlineDot(online: Boolean?) {
    if (online == null) return
    val description = stringResource(if (online) R.string.plex_server_online else R.string.plex_server_offline)
    Box(
        modifier = Modifier
            .size(10.dp)
            .semantics { contentDescription = description }
            .background(
                color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}

/**
 * The local-network step (Android 17): one line on why, and Allow through the system dialog. Granted or denied,
 * [onAnswered] carries on — a denial only means LAN addresses are skipped (the server is still reached through its
 * other connections).
 */
@Composable
private fun LocalNetworkStep(state: PlexSetupUiState, onAnswered: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswered() }
    Icon(
        imageVector        = Icons.Outlined.Lan,
        contentDescription = null,
        tint               = MaterialTheme.colorScheme.primary,
        modifier           = Modifier.padding(horizontal = 24.dp).size(40.dp),
    )
    StepTitle(stringResource(R.string.plex_local_title))
    StepBody(stringResource(R.string.plex_local_body, state.server?.name.orEmpty()))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Button(
            onClick = {
                haptics.confirm()
                val permission = state.permission
                if (permission != null) launcher.launch(permission) else onAnswered()
            },
        ) {
            Text(stringResource(R.string.plex_local_allow))
        }
        TextButton(onClick = { haptics.press(); onAnswered() }) {
            Text(stringResource(R.string.plex_local_skip))
        }
    }
}

/** Pick the server's music libraries — all on to start — and add the server. */
@Composable
private fun LibrariesStep(state: PlexSetupUiState, onToggle: (String) -> Unit, onFinish: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    StepTitle(stringResource(R.string.plex_libraries_title))
    when {
        state.busy                -> BusyLine(R.string.plex_libraries_loading)
        state.error != null       -> Unit
        state.libraries.isEmpty() -> StepBody(stringResource(R.string.plex_libraries_empty))
        else                      -> {
            state.libraries.forEach { library ->
                val checked = library.key in state.selected
                ListItem(
                    leadingContent  = { Checkbox(checked = checked, onCheckedChange = null) },
                    content         = { Text(library.title) },
                    modifier        = Modifier.toggleable(
                        value         = checked,
                        role          = Role.Checkbox,
                        onValueChange = { haptics.toggle(it); onToggle(library.key) },
                    ),
                )
            }
            Button(
                onClick  = { haptics.confirm(); onFinish() },
                enabled  = canFinish(state.libraries, state.selected),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(stringResource(R.string.plex_libraries_add))
            }
        }
    }
}
