package com.shelfie.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.datastore.ShelfiePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides whether to show onboarding or the main shell.
 *
 * Onboarding completion is stored locally rather than inferred from permission
 * state, because Limited Mode is a legitimate finished outcome: a user who chose
 * to hand-pick screenshots has completed onboarding even though broad access was
 * never granted.
 */
@HiltViewModel
class ShelfieAppViewModel @Inject constructor(
    private val preferences: ShelfiePreferences,
) : ViewModel() {

    private val overrideComplete = MutableStateFlow(false)

    val startState: StateFlow<ShelfieStartState> = combine(
        preferences.onboardingComplete,
        overrideComplete,
    ) { stored, override ->
        if (stored || override) ShelfieStartState.Ready else ShelfieStartState.Onboarding
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // Loading, not Onboarding: defaulting to onboarding would flash it for a
        // frame on every cold start before the preference read completes.
        initialValue = ShelfieStartState.Loading,
    )

    fun onOnboardingFinished() {
        overrideComplete.value = true
        viewModelScope.launch { preferences.setOnboardingComplete(true) }
    }
}

sealed interface ShelfieStartState {
    data object Loading : ShelfieStartState
    data object Onboarding : ShelfieStartState
    data object Ready : ShelfieStartState
}
