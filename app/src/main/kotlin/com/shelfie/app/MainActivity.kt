package com.shelfie.app

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.designsystem.theme.ShelfieBackground
import com.shelfie.core.designsystem.theme.ShelfieTheme
import com.shelfie.core.media.ScreenshotContentObserver
import com.shelfie.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
// FragmentActivity rather than ComponentActivity because BiometricPrompt requires one.
// FragmentActivity extends ComponentActivity, so nothing else changes.
class MainActivity : FragmentActivity() {

    /**
     * Watches MediaStore while the app is in the foreground. Only a hint — the
     * watermark reconcile is what guarantees nothing is missed — so it is safe
     * to register and unregister with the visible lifecycle.
     */
    @Inject
    lateinit var screenshotObserver: ScreenshotContentObserver

    override fun onStart() {
        super.onStart()
        screenshotObserver.start()
    }

    override fun onStop() {
        screenshotObserver.stop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Edge-to-edge is enforced for apps targeting API 35+.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val openSearch = intent?.getBooleanExtra(EXTRA_OPEN_SEARCH, false) == true

        setContent {
            val appViewModel: ShelfieAppViewModel = hiltViewModel()
            val useDynamicColor by appViewModel.useDynamicColor.collectAsStateWithLifecycle()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            /*
             * Status and navigation bar icons have to be told which way to go.
             *
             * The app is drawn edge to edge, so the system bars sit over app content
             * and their icons are not restyled by the theme. Without this, choosing
             * light mode leaves white icons on a white background — the clock and
             * battery simply disappear.
             */
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                val window = (view.context as android.app.Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            val appLockEnabled by appViewModel.appLockEnabled.collectAsStateWithLifecycle()

            /*
             * Keeps the app out of screenshots and the recents preview while locked.
             *
             * Without this the task switcher holds a live thumbnail of whatever was on
             * screen, so anyone can read the shelf without ever unlocking — which
             * would make the lock decorative. Tied to the setting rather than always
             * on, because FLAG_SECURE also blocks the user's own screenshots, and
             * silently breaking that in a screenshot app would be its own bug.
             */
            LaunchedEffect(appLockEnabled) {
                if (appLockEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            ShelfieTheme(darkTheme = darkTheme, dynamicColor = useDynamicColor) {
                // Wraps everything, so the gradient is behind every screen rather
                // than being repeated per-screen and drifting out of sync.
                ShelfieBackground {
                    // Outside the app, so nothing at all is composed while locked.
                    AppLockGate(enabled = appLockEnabled) {
                        ShelfieApp(startOnSearch = openSearch, viewModel = appViewModel)
                    }
                }
            }
        }
    }

    companion object {
        /** Set by the widget and the Quick Settings tile to land on Search. */
        const val EXTRA_OPEN_SEARCH = "com.shelfie.app.OPEN_SEARCH"
    }
}
