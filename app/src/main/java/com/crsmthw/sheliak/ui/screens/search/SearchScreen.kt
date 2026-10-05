package com.crsmthw.sheliak.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.navigation.searchBarSharedBounds
import com.crsmthw.sheliak.util.SearchBarHeight
import com.crsmthw.sheliak.util.SearchBarSideMargin
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press
import kotlinx.coroutines.flow.first

/** The floating bar's gap below the status bar. */
private val SearchBarTopMargin = 8.dp

/** Bar top margin + bar + the gap under it: where content below the floating bar starts. */
private val SearchBarBlockHeight = SearchBarTopMargin + SearchBarHeight + 12.dp

/**
 * Search: a floating Material 3 search field over the page — no app bar — whose back arrow is its own leading
 * icon, so the whole control is one box. On compact widths that box is the far end of the search FAB's
 * container transform ([searchBarSharedBounds]); on rail widths Search arrives with the ordinary push. The
 * screen itself is identical at every width.
 *
 * M0 has no index to search: the field works, and the area below it shows the (empty) recent searches.
 *
 * The field is focused once per visit, after this destination is RESUMED. Navigation 3 resumes an entry only
 * when it is on top AND its transition has settled, so the keyboard rises after the FAB → bar morph has landed
 * and never during it, and a cancelled predictive back onto this screen (which never resumes it) cannot raise
 * it. A re-entry over a typed query does not re-focus: the user came back to read, not to type.
 */
@Composable
fun SearchScreen(
    onBack  : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val queryState = rememberTextFieldState()
    // Derived, so only a blank ↔ non-blank flip recomposes the screen — not every keystroke, cursor move or
    // IME composing update that `TextFieldState.text` carries.
    val queryBlank by remember(queryState) { derivedStateOf { queryState.text.isBlank() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalSystemBarsPadding(),
    ) {
        // Below the bar: the recent searches while the field is blank. None are stored before the library
        // exists, so this is their empty state.
        if (queryBlank) {
            Text(
                text      = stringResource(R.string.search_recent_empty),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(top = SearchBarBlockHeight + 16.dp, start = 24.dp, end = 24.dp),
            )
        }

        SearchInputBar(
            queryState     = queryState,
            focusRequester = focusRequester,
            onBack         = { keyboard?.hide(); haptics.confirm(); onBack() },
            // Clearing starts the next query, so the field is left focused with the keyboard up from either
            // state the ✕ can be tapped in (unfocused, or focused after the keyboard was swiped away).
            onClear        = {
                haptics.press()
                queryState.clearText()
                focusRequester.requestFocus()
                keyboard?.show()
            },
            onSearch       = { keyboard?.hide() },
            modifier       = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = SearchBarSideMargin, end = SearchBarSideMargin, top = SearchBarTopMargin)
                .searchBarSharedBounds(),
        )
    }

    // `rememberSaveable` so the flag survives the save / restore a pop performs: popping back here from a
    // later destination composes Search afresh, and must not throw the keyboard up over a typed query.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        val reEntry = autoFocused
        if (reEntry && queryState.text.isNotBlank()) return@LaunchedEffect
        autoFocused = true
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        focusRequester.requestFocus()
    }
}

/**
 * The floating field: a `SearchBarDefaults.InputField` inside a stadium [Surface] in `surfaceContainerHigh`
 * with a small shadow, laid out at exactly [SearchBarHeight] — the morph's geometry assumes that height.
 *
 * The non-deprecated `InputField` needs a `SearchBarState`; it is created already EXPANDED and left alone,
 * because nothing here ever expands into a full-screen search. Starting it collapsed would run an expand
 * animation on focus (recomposing the field per frame), keep a text collector alive just to trigger that
 * expansion, and arm a clear-focus-on-collapse effect — all for a state nothing renders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputBar(
    queryState    : TextFieldState,
    focusRequester: FocusRequester,
    onBack        : () -> Unit,
    onClear       : () -> Unit,
    onSearch      : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    Surface(
        modifier        = modifier.fillMaxWidth().height(SearchBarHeight),
        shape           = CircleShape,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = queryState,
            searchBarState = searchBarState,
            onSearch       = { onSearch() },
            modifier       = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder    = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon    = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                    )
                }
            },
            trailingIcon   = if (queryState.text.isNotBlank()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}
