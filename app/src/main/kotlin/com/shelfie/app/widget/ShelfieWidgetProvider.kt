package com.shelfie.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shelfie.app.MainActivity
import com.shelfie.app.R
import com.shelfie.core.database.dao.ScreenshotDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: a search entry point plus the indexed count.
 *
 * This is a habit hook. The product only becomes daily-use if the phone brings
 * the user back rather than the user remembering to open an app — and a widget
 * does that without spending a notification.
 *
 * Built with RemoteViews rather than Glance deliberately: it adds no dependency,
 * and the widget is a static two-line layout that gains nothing from Compose.
 */
class ShelfieWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun screenshotDao(): ScreenshotDao
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Render immediately with no count, then fill it in. The widget must never
        // appear blank while a database read completes.
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id, count = null) }

        scope.launch {
            val dao = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .screenshotDao()

            val count = runCatching { dao.observeIndexedCount().first() }.getOrNull()
            appWidgetIds.forEach { id -> render(context, appWidgetManager, id, count) }
        }
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        count: Int?,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_shelfie).apply {
            setTextViewText(
                R.id.widget_subtitle,
                when {
                    count == null -> context.getString(R.string.widget_subtitle_loading)
                    count == 0 -> context.getString(R.string.widget_subtitle_empty)
                    else -> context.resources.getQuantityString(
                        R.plurals.widget_subtitle_count,
                        count,
                        count,
                    )
                },
            )
            setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
        }
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_OPEN_SEARCH, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            /* requestCode = */ 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Asks the system to refresh every placed widget. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ShelfieWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, ShelfieWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
