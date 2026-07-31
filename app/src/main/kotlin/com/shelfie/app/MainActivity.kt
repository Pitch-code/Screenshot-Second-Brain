package com.shelfie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shelfie.core.designsystem.theme.ShelfieTheme
import com.shelfie.core.media.ScreenshotContentObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

        setContent {
            ShelfieTheme {
                ShelfieApp()
            }
        }
    }
}
