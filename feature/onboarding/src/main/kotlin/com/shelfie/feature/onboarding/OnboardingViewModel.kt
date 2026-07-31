package com.shelfie.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.media.PickerImporter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.MediaAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val pickerImporter: PickerImporter,
    private val preferences: ShelfiePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    fun onNext() {
        _state.update { current ->
            current.copy(step = current.step.next())
        }
    }

    /**
     * Called after the system permission dialog closes.
     *
     * The result is read back from the OS rather than trusted from the dialog
     * callback, because on Android 14+ the user may have granted partial access,
     * which arrives as a different permission than the one requested.
     */
    fun onPermissionResult() {
        val access = repository.currentAccess()
        _state.update { it.copy(access = access) }

        if (access != MediaAccess.DENIED) {
            completeOnboarding()
        }
    }

    /** Limited Mode: index whatever the user hand-picked. */
    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return

        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val imported = pickerImporter.import(uris)
            _state.update {
                it.copy(isImporting = false, importedCount = it.importedCount + imported)
            }
            if (imported > 0) completeOnboarding()
        }
    }

    /** Chose Limited Mode without picking anything yet. */
    fun onSkipToLimitedMode() {
        _state.update { it.copy(access = MediaAccess.DENIED) }
        completeOnboarding()
    }

    private fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingComplete(true) }
    }
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PROBLEM,
    val access: MediaAccess = MediaAccess.DENIED,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
)

/**
 * Three steps, in this order for a reason.
 *
 * The trust step comes *before* the system dialog: telling someone what happens
 * to their screenshots after asking for access is far less persuasive than
 * telling them first. No account, no paywall, nothing else in the way.
 */
enum class OnboardingStep {
    /** Mirror the problem back to the user. */
    PROBLEM,

    /** Explain on-device processing before asking for anything. */
    TRUST,

    /** Rationale, then the system permission dialog. */
    PERMISSION,
    ;

    fun next(): OnboardingStep = when (this) {
        PROBLEM -> TRUST
        TRUST -> PERMISSION
        PERMISSION -> PERMISSION
    }
}
