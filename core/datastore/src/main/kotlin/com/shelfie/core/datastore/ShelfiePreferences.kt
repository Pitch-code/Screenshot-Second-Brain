package com.shelfie.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shelfie.core.model.ShelfSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, non-syncing preference storage.
 *
 * The MediaStore watermark lives here rather than in memory. A ContentObserver
 * is only ever a hint — it is unreliable across OEM skins and is not delivered
 * while the process is dead — so the watermark is the actual source of truth
 * for "what have I already seen", and reconciliation is self-healing.
 */
@Singleton
class ShelfiePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val watermark: Flow<Long> = dataStore.data.map { it[KEY_WATERMARK] ?: 0L }

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    /** Entitlement is cached locally so the app works with no network at all. */
    val isFullVersion: Flow<Boolean> =
        dataStore.data.map { it[KEY_FULL_VERSION] ?: false }

    val lastReconcileAt: Flow<Long> = dataStore.data.map { it[KEY_LAST_RECONCILE] ?: 0L }

    /** Dynamic colour follows the system wallpaper by default on Android 12+. */
    val useDynamicColor: Flow<Boolean> =
        dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }

    /**
     * Grid ordering. Stored by enum name, and an unrecognised value falls back to
     * the default rather than throwing, so a downgrade cannot crash the shelf.
     */
    val shelfSortOrder: Flow<ShelfSortOrder> =
        dataStore.data.map { ShelfSortOrder.fromNameOrDefault(it[KEY_SHELF_SORT]) }

    /**
     * Folders the user has opted into beyond the automatic screenshot detection,
     * stored as lower-cased folder names.
     *
     * Empty by default, which means "only what looks like a screenshot". Because the
     * discovery filter treats this set as additive, an empty set reproduces the
     * original behaviour exactly.
     */
    val extraFolders: Flow<Set<String>> =
        dataStore.data.map { it[KEY_EXTRA_FOLDERS] ?: emptySet() }

    /** Advances the watermark; never moves backwards. */
    suspend fun advanceWatermark(dateAddedSeconds: Long) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_WATERMARK] ?: 0L
            if (dateAddedSeconds > current) {
                prefs[KEY_WATERMARK] = dateAddedSeconds
            }
        }
    }

    /** Used by the reconcile path to force a full re-scan. */
    suspend fun resetWatermark() {
        dataStore.edit { it[KEY_WATERMARK] = 0L }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setFullVersion(purchased: Boolean) {
        dataStore.edit { it[KEY_FULL_VERSION] = purchased }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShelfSortOrder(order: ShelfSortOrder) {
        dataStore.edit { it[KEY_SHELF_SORT] = order.name }
    }

    suspend fun setExtraFolders(folderKeys: Set<String>) {
        dataStore.edit { it[KEY_EXTRA_FOLDERS] = folderKeys }
    }

    suspend fun setLastReconcileAt(epochSeconds: Long) {
        dataStore.edit { it[KEY_LAST_RECONCILE] = epochSeconds }
    }

    private companion object {
        val KEY_WATERMARK = longPreferencesKey("mediastore_watermark_seconds")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_FULL_VERSION = booleanPreferencesKey("full_version_purchased")
        val KEY_LAST_RECONCILE = longPreferencesKey("last_reconcile_seconds")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val KEY_SHELF_SORT = stringPreferencesKey("shelf_sort_order")
        val KEY_EXTRA_FOLDERS = stringSetPreferencesKey("extra_indexed_folders")
    }
}
