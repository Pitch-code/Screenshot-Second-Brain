package com.shelfie.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.billing.ShelfieBilling
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.model.ThemeMode
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
    private val billing: ShelfieBilling,
) : ViewModel() {

    /** Theme preference, applied before the first frame of real UI. */
    /**
     * Read here rather than in a screen, because the theme wraps the whole activity
     * and has to be known before anything is composed.
     *
     * [SharingStarted.Eagerly] with the stored value unavailable on the first frame
     * would flash the wrong scheme, so the initial value matches the default the
     * preference itself falls back to.
     */
    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.Default,
    )

    /**
     * Whether to demand the screen lock before showing anything.
     *
     * Initial value false, and that direction matters: the alternative is a frame in
     * which the shelf is visible before the preference has loaded, which is exactly
     * the content the lock exists to hide. It is safe here because the gate re-locks
     * on every stop, so a true value arriving a frame later still locks — the app has
     * not been *unlocked*, it has simply not been locked yet, and nothing is
     * interactive in that frame.
     */
    val appLockEnabled: StateFlow<Boolean> = preferences.appLockEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    val useDynamicColor: StateFlow<Boolean> = preferences.useDynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )

    init {
        // Refreshes the cached entitlement from Play when it is reachable. This
        // is the restore-purchases path; there is nothing for the user to tap.
        viewModelScope.launch { runCatching { billing.initialise() } }
    }

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
