package com.shelfie.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.shelfie.core.media.PickerImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Share-sheet target.
 *
 * Sharing an image into Shelfie indexes it, which matters for two reasons: it is
 * another re-entry point that costs no notification, and in Limited Mode it is a
 * way to add screenshots without opening the picker.
 *
 * Invisible: it imports and finishes, so the share feels instant.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var pickerImporter: PickerImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            toast(getString(R.string.share_nothing_to_add))
            finish()
            return
        }

        toast(getString(R.string.share_adding))

        lifecycleScope.launch {
            val imported = runCatching { pickerImporter.import(uris) }.getOrDefault(0)
            toast(
                if (imported > 0) {
                    resources.getQuantityString(R.plurals.share_added, imported, imported)
                } else {
                    getString(R.string.share_failed)
                },
            )
            finish()
        }
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        intent ?: return emptyList()

        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(intent.getParcelableExtraCompat(Intent.EXTRA_STREAM))

            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM)

            else -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name) as? Uri
        }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableArrayListExtraCompat(name: String): List<Uri> =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(name, Uri::class.java).orEmpty()
        } else {
            (getParcelableArrayListExtra<Uri>(name) ?: arrayListOf()).filterNotNull()
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
