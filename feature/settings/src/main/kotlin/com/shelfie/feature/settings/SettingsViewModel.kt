package com.shelfie.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.billing.BillingState
import com.shelfie.core.billing.PurchaseResult
import com.shelfie.core.billing.ShelfieBilling
import com.shelfie.core.classify.UserRule
import com.shelfie.core.database.dao.IndexStateCount
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.media.IndexScheduler
import com.shelfie.core.media.IndexingQuota
import com.shelfie.core.media.QuotaState
import com.shelfie.core.media.ScreenshotDeleter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.MediaFolder
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import com.shelfie.core.designsystem.component.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val preferences: ShelfiePreferences,
    private val billing: ShelfieBilling,
    private val quota: IndexingQuota,
    private val deleter: ScreenshotDeleter,
    private val scheduler: IndexScheduler,
) : ViewModel() {

    private val message = MutableStateFlow<UiMessage?>(null)

    /** Play's own error text, which arrives already localised. */
    private val failureText = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        repository.observeRules(),
        quota.state,
        billing.billingState,
        combine(preferences.useDynamicColor, preferences.themeMode) { dyn, mode -> dyn to mode },
        combine(message, failureText) { m, f -> m to f },
        combine(
            repository.observeStateCounts(),
            repository.observeLastError(),
        ) { counts, error -> counts to error },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val rules = values[0] as List<UserRule>
        val quotaState = values[1] as QuotaState
        val billingState = values[2] as BillingState
        val (dynamicColor, themeMode) = values[3] as Pair<Boolean, ThemeMode>
        val (msg, failure) = values[4] as Pair<UiMessage?, String?>
        val (counts, lastError) = values[5] as Pair<List<IndexStateCount>, String?>

        SettingsUiState(
            rules = rules,
            quota = quotaState,
            billing = billingState,
            useDynamicColor = dynamicColor,
            themeMode = themeMode,
            access = repository.currentAccess(),
            message = msg,
            failureText = failure,
            stateCounts = counts,
            lastError = lastError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        // Also refreshes entitlement from Play, which is the restore-purchases
        // path. There is nothing for the user to tap.
        viewModelScope.launch { runCatching { billing.initialise() } }
    }

    fun onPurchase(activity: Activity) {
        viewModelScope.launch {
            when (val result = billing.purchase(activity)) {
                PurchaseResult.Success, PurchaseResult.AlreadyOwned -> {
                    // Free every held-back screenshot and let the background
                    // tiers re-index them.
                    val released = quota.releaseAll()
                    scheduler.scheduleAll()
                    message.value = if (released > 0) {
                        UiMessage.Plural(
                            R.plurals.settings_purchase_reindexing,
                            released,
                            listOf(released),
                        )
                    } else {
                        UiMessage.Text(R.string.settings_purchase_unlocked_message)
                    }
                }

                PurchaseResult.Pending ->
                    message.value = UiMessage.Text(R.string.settings_purchase_pending)

                PurchaseResult.Cancelled -> Unit

                is PurchaseResult.Failed ->
                    // Play supplies this text already localised.
                    failureText.value = result.message
            }
        }
    }

    fun onThemeModeChanged(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun onDeleteRule(id: Long) {
        viewModelScope.launch { repository.removeRule(id) }
    }

    /**
     * Re-checks ownership with Play, and reports the outcome.
     *
     * Restoration already happens automatically on launch, so this is a recovery
     * path rather than the main one: it covers a failed query, a Play Store that was
     * signed out at start-up, and the common support case of someone having bought
     * on a different Google account. Cheap to provide, and Play reviewers expect a
     * one-time purchase to have a visible restore route.
     */
    fun onRestorePurchase() {
        viewModelScope.launch {
            runCatching { billing.initialise() }

            val owned = quota.isUnlimited()
            if (owned) {
                // Held-back rows must be released here too, or a restored user is
                // unlocked but still has an incomplete index.
                runCatching { quota.releaseAll() }
                scheduler.scheduleAll()
            }

            message.value = if (owned) {
                UiMessage.Text(R.string.settings_restore_success)
            } else {
                UiMessage.Text(R.string.settings_restore_none)
            }
        }
    }

    /**
     * Deletes a folder. Its screenshots return to their automatic category.
     *
     * Offered here because otherwise a mistyped folder name would be permanent —
     * the create flow lives in the detail sheet and has no edit path.
     */
    // ------------------------------------------------------- folder scanning

    private val availableFolders = MutableStateFlow<List<MediaFolder>>(emptyList())

    /**
     * Loaded on demand rather than kept in the UI state.
     *
     * Listing folders walks the entire image table, so doing it eagerly would cost
     * every user a full scan just for opening Settings.
     */
    fun onLoadFolders() {
        viewModelScope.launch {
            availableFolders.value = repository.availableFolders()
        }
    }

    fun onFoldersChosen(folderKeys: Set<String>) {
        viewModelScope.launch {
            repository.setChosenFolders(folderKeys)
            scheduler.scheduleAll()
            message.value = UiMessage.Text(R.string.settings_folders_saved)
        }
    }

    val folderPickerState: StateFlow<FolderPickerState> = combine(
        availableFolders,
        repository.observeChosenFolders(),
    ) { available, chosen ->
        FolderPickerState(available = available, chosen = chosen)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FolderPickerState(),
    )

    fun onAddRule(keyword: String, category: com.shelfie.core.model.ScreenshotCategory) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.addRule(keyword, category)
            message.value = UiMessage.Text(R.string.settings_rule_saved)
        }
    }

    /** Plain-text export of the index, written by the caller to a chosen file. */
    suspend fun buildExport(): String = repository.exportIndex()

    /** Requeues everything that failed, for after a fix or a permission change. */
    fun onRetryFailed() {
        viewModelScope.launch {
            val requeued = repository.requeueFailed()
            message.value = UiMessage.Text(R.string.settings_diag_retry_done, listOf(requeued))
        }
    }

    /** Plain-text diagnostics, for pasting into a bug report. */
    fun diagnosticsText(): String = with(state.value) {
        buildString {
            appendLine("Shelfie diagnostics")
            appendLine("access=$access")
            appendLine("indexed=${quota.indexed} heldBack=${quota.heldBack}")
            stateCounts.forEach { appendLine("${it.state}=${it.count}") }
            appendLine("lastError=${lastError ?: "none"}")
        }
    }

    fun onMessageShown() {
        message.update { null }
        failureText.update { null }
    }
}

data class FolderPickerState(
    val available: List<MediaFolder> = emptyList(),
    val chosen: Set<String> = emptySet(),
)

data class SettingsUiState(
    val rules: List<UserRule> = emptyList(),

    val quota: QuotaState = QuotaState(),
    val billing: BillingState = BillingState.Connecting,
    val useDynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.Default,
    val access: MediaAccess = MediaAccess.DENIED,
    val message: UiMessage? = null,
    val failureText: String? = null,
    val stateCounts: List<IndexStateCount> = emptyList(),
    val lastError: String? = null,
)
