package com.shelfie.core.designsystem.action

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.getSystemService
import com.shelfie.core.model.DateTextParser
import com.shelfie.core.model.ScreenshotAction

/**
 * Turns a [ScreenshotAction] into a real system intent.
 *
 * Every launch is guarded: a device with no dialler, no browser, or no calendar
 * must produce a graceful message rather than a crash. [ActionResult] lets the
 * caller show that message without this class needing UI.
 */
class ScreenshotActionLauncher(private val context: Context) {

    fun launch(
        action: ScreenshotAction,
        primaryValue: String?,
        fullText: String?,
        firstUrl: String? = null,
        firstPhone: String? = null,
        firstDate: String? = null,
        firstTime: String? = null,
    ): ActionResult = when (action) {
        ScreenshotAction.OPEN_LINK -> openLink(firstUrl ?: primaryValue)
        ScreenshotAction.COPY_CODE -> copy(primaryValue, label = "Code")
        ScreenshotAction.COPY_TEXT -> copy(fullText ?: primaryValue, label = "Text")
        ScreenshotAction.DIAL_NUMBER -> dial(firstPhone ?: primaryValue)
        ScreenshotAction.SHARE -> share(fullText ?: primaryValue)
        ScreenshotAction.ADD_TO_CALENDAR -> addToCalendar(
            title = primaryValue ?: "Screenshot reminder",
            dateText = firstDate,
            timeText = firstTime,
        )
    }

    fun copy(value: String?, label: String = "Text"): ActionResult {
        if (value.isNullOrBlank()) return ActionResult.NothingToDo

        val clipboard = context.getSystemService<ClipboardManager>()
            ?: return ActionResult.Failed("Clipboard unavailable")

        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        // Android 13+ shows its own copy confirmation, so suppressing ours
        // avoids a duplicate toast.
        return ActionResult.Copied(
            showConfirmation = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU,
        )
    }

    private fun openLink(url: String?): ActionResult {
        if (url.isNullOrBlank()) return ActionResult.NothingToDo

        val normalised = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(normalised)), "No browser found")
    }

    private fun dial(number: String?): ActionResult {
        if (number.isNullOrBlank()) return ActionResult.NothingToDo

        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return ActionResult.NothingToDo

        // ACTION_DIAL rather than ACTION_CALL: it opens the dialler pre-filled
        // and needs no CALL_PHONE permission, keeping the permission list short.
        return start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")), "No dialler found")
    }

    private fun share(text: String?): ActionResult {
        if (text.isNullOrBlank()) return ActionResult.NothingToDo

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return start(Intent.createChooser(intent, null), "Nothing to share with")
    }

    private fun addToCalendar(title: String, dateText: String?, timeText: String?): ActionResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)

            // If the date parses, pre-fill it. If not, the calendar still opens
            // with the title and the user picks a date — better than guessing.
            DateTextParser.parseEpochMillis(dateText, timeText)?.let { millis ->
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis + ONE_HOUR_MILLIS)
            }
        }
        return start(intent, "No calendar app found")
    }

    private fun start(intent: Intent, failureMessage: String): ActionResult = try {
        // Launched from a non-activity context in some call paths.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Launched
    } catch (_: ActivityNotFoundException) {
        ActionResult.Failed(failureMessage)
    } catch (_: SecurityException) {
        ActionResult.Failed(failureMessage)
    }

    private companion object {
        const val ONE_HOUR_MILLIS = 60L * 60 * 1000
    }
}

/** Outcome of an action, so the caller can show feedback. */
sealed interface ActionResult {
    data object Launched : ActionResult
    data class Copied(val showConfirmation: Boolean) : ActionResult

    /** The screenshot had no value for this action. */
    data object NothingToDo : ActionResult

    data class Failed(val message: String) : ActionResult
}
