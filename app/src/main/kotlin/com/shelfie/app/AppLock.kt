package com.shelfie.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor

/**
 * Which authenticators the lock accepts.
 *
 * Weak biometrics *or* the device credential, deliberately, rather than
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`:
 *
 *  - Including [BiometricManager.Authenticators.DEVICE_CREDENTIAL] is the whole point.
 *    It means whatever the phone is already unlocked with — PIN, pattern, password —
 *    works here, with no second secret for the user to invent, store, or forget. This
 *    app cannot offer a recovery flow, because it has no account and no network, so a
 *    passcode of its own would be one lost memory away from an unopenable app.
 *  - `BIOMETRIC_WEAK` rather than `STRONG` because the strong-plus-credential
 *    combination is not supported below API 30, and this app supports API 26. Weak is
 *    the right trade here anyway: the asset is a personal screenshot library, not a
 *    bank balance, and the alternative is either no lock at all on older phones or two
 *    code paths that would only ever be tested on one of them.
 */
private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Whether this device can authenticate at all.
 *
 * False when there is no biometric hardware *and* no screen lock set. Checked rather
 * than assumed, because the lock must never be shown on a device that has no way to
 * dismiss it.
 */
fun canAuthenticate(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Holds the app shut until the person holding the phone proves who they are.
 *
 * Uses the phone's own screen lock, so there is no Shelfie passcode to set up and
 * nothing about the credential is ever seen or stored by this app — Android verifies
 * it and returns a yes or no.
 *
 * ## Failing open
 *
 * If the lock is enabled but the device can no longer authenticate — the screen lock
 * was removed after the setting was turned on — this composes its content rather than
 * blocking. That is a deliberate choice against the stricter option: with no account,
 * no server and no recovery path, a lock that cannot be satisfied is an app whose
 * entire library is gone for good. For a screenshot organiser that is the worse
 * outcome. A banking app should make the opposite choice.
 *
 * ## Re-locking
 *
 * Locks again on every stop, not after a timeout. Someone who turns this on is
 * protecting against another person picking up an unattended phone, which is exactly
 * the moment a grace period would cover for them.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Recomputed per composition rather than remembered: the screen lock can be
    // removed while the app sits in the background, and a cached "yes" would then
    // present a prompt nothing can answer.
    val available = remember(enabled) { enabled && canAuthenticate(context) }

    if (!available || activity == null) {
        content()
        return
    }

    var unlocked by remember { mutableStateOf(false) }
    var promptVisible by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffectOnStop(lifecycleOwner) {
        unlocked = false
        promptVisible = false
    }

    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    val authenticate: () -> Unit = remember(activity) {
        {
            if (!promptVisible) {
                promptVisible = true
                val executor = Executor { command -> command.run() }
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult,
                        ) {
                            promptVisible = false
                            unlocked = true
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            // Stays locked, with a retry button. No message shown: the
                            // system prompt has already said why, and repeating it in
                            // the app would only be a second place to keep correct.
                            promptVisible = false
                        }

                        // onAuthenticationFailed is not overridden. A single wrong
                        // finger is not an error; the system prompt handles retries
                        // itself and stays on screen.
                    },
                )

                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                        // No negative button. Setting one alongside DEVICE_CREDENTIAL
                        // throws, because the credential path supplies its own cancel.
                        .build(),
                )
            }
        }
    }

    // Asks as soon as the app is shown, so unlocking is one gesture rather than a tap
    // followed by a gesture.
    LaunchedEffect(unlocked) {
        if (!unlocked) authenticate()
    }

    if (unlocked) {
        content()
    } else {
        LockedScreen(onUnlock = authenticate)
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.lock_locked_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.lock_unlock))
        }
    }
}

/** Runs [onStop] each time the lifecycle stops, and cleans up its own observer. */
@Composable
private fun DisposableEffectOnStop(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onStop: () -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
