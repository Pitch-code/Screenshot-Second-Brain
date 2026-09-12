package com.shelfie.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shelfie.app.MainActivity
import com.shelfie.app.R

/**
 * Quick Settings tile: "Search Shelfie".
 *
 * The cheapest possible re-entry point — two swipes from anywhere, including the
 * lock screen shade, with no notification spent and no home-screen space used.
 */
class ShelfieTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            // The same resource the manifest declares for this service, rather than a
            // second copy of the text as a Kotlin literal — which is how the tile
            // ended up being the one string that could not be translated even though
            // `tile_label` already existed.
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SEARCH, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires a PendingIntent; the legacy call is blocked.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            // No PendingIntent overload exists below API 34, so the deprecated
            // call is the only option there.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
