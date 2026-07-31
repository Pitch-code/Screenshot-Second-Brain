package com.shelfie.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.billing.BillingState
import com.shelfie.core.billing.PurchaseResult
import com.shelfie.core.billing.ShelfieBilling
import com.shelfie.core.classify.UserRule
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.media.IndexScheduler
import com.shelfie.core.media.IndexingQuota
import com.shelfie.core.media.QuotaState
import com.shelfie.core.media.ScreenshotDeleter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.MediaAccess
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
        preferences.useDynamicColor,
        combine(message, failureText) { m, f -> m to f },
    ) { rules, quotaState, billingState, dynamicColor, (msg, failure) ->
        SettingsUiState(
            rules = rules,
            quota = quotaState,
            billing = billingState,
            useDynamicColor = dynamicColor,
            access = repository.currentAccess(),
            message = msg,
            failureText = failure,
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

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun onDeleteRule(id: Long) {
        viewModelScope.launch { repository.removeRule(id) }
    }

    fun onAddRule(keyword: String, category: com.shelfie.core.model.ScreenshotCategory) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.addRule(keyword, category)
            message.value = UiMessage.Text(R.string.settings_rule_saved)
        }
    }

    /** Plain-text export of the index, written by the caller to a chosen file. */
    suspend fun buildExport(): String = repository.exportIndex()

    fun onMessageShown() {
        message.update { null }
        failureText.update { null }
    }
}

data class SettingsUiState(
    val rules: List<UserRule> = emptyList(),
    val quota: QuotaState = QuotaState(),
    val billing: BillingState = BillingState.Connecting,
    val useDynamicColor: Boolean = true,
    val access: MediaAccess = MediaAccess.DENIED,
    val message: UiMessage? = null,
    val failureText: String? = null,
)
