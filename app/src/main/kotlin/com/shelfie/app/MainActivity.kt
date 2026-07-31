package com.shelfie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shelfie.core.designsystem.theme.ShelfieTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
