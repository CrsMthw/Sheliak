package com.crsmthw.sheliak

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.ui.navigation.SheliakNavGraph
import com.crsmthw.sheliak.ui.theme.ACCENT_DEFAULT_ARGB
import com.crsmthw.sheliak.ui.theme.SheliakTheme
import com.crsmthw.sheliak.ui.theme.ThemeMode
import com.crsmthw.sheliak.ui.theme.isDark
import com.crsmthw.sheliak.util.HapticsConfig

/**
 * The single Activity; everything else is a Compose destination.
 *
 * [installSplashScreen] runs before `super.onCreate` because the manifest launches this Activity in the splash
 * theme, and only that call swaps in `postSplashScreenTheme` once the first frame is ready. The splash stays up
 * until the stored settings have been read: the theme and the start destination both depend on them, and
 * drawing defaults first would flash the wrong theme or the wrong first screen.
 *
 * The Activity is never recreated for a theme change (`uiMode` is in the manifest's `configChanges`): the
 * theme is observed here and recomposes in place.
 */
class MainActivity : ComponentActivity() {

    /** Read by the splash's pre-draw check, so a plain field; written once, from composition. */
    @Volatile private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()

        val container = (application as SheliakApplication).container
        val settings = container.settingsRepository

        setContent {
            // Nullable until DataStore's first emission — null means "not read yet", never a setting value.
            val themeMode      by settings.themeMode.collectAsStateWithLifecycle<ThemeMode?>(initialValue = null)
            val amoledBlack    by settings.amoledBlack.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val dynamicColor   by settings.dynamicColor.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val accentColor    by settings.accentColor.collectAsStateWithLifecycle<Int?>(initialValue = null)
            val introDone      by settings.introDone.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val hapticsEnabled by settings.hapticsEnabled.collectAsStateWithLifecycle(initialValue = true)

            // The process-wide gate every haptic helper reads (util/Haptics.kt).
            LaunchedEffect(hapticsEnabled) { HapticsConfig.enabled = hapticsEnabled }

            val loaded = themeMode != null && amoledBlack != null && dynamicColor != null &&
                accentColor != null && introDone != null
            SideEffect { if (loaded) settingsLoaded = true }

            val mode = themeMode ?: ThemeMode.SYSTEM
            val dark = mode.isDark(isSystemInDarkTheme())
            // Transparent bars, re-applied whenever the IN-APP dark flag flips, so the status and navigation
            // bar icons follow the app's theme rather than the system's.
            SideEffect {
                val barStyle = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            SheliakTheme(
                themeMode    = mode,
                amoledBlack  = amoledBlack ?: false,
                dynamicColor = dynamicColor ?: true,
                accentArgb   = accentColor ?: ACCENT_DEFAULT_ARGB,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The back stack takes its start destination once, so the shell waits for the flag.
                    introDone?.let { done -> SheliakNavGraph(container = container, introDone = done) }
                }
            }
        }
    }
}
