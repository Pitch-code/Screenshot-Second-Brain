package com.shelfie.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.shelfie.feature.onboarding.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Onboarding: three screens, no account, no paywall, about fifteen seconds.
 *
 * Login walls were the single largest complaint category across the app-review
 * research, so there is deliberately nothing to sign up for here.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Read the outcome from the OS rather than the callback map: on Android
        // 14+ the user may have granted partial access, which comes back as a
        // different permission than the one requested.
        viewModel.onPermissionResult()
        onFinished()
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesPicked(uris)
            onFinished()
        }
    }

    val scheme = MaterialTheme.colorScheme

    /*
     * A different colour per page.
     *
     * Three identically dark pages made onboarding feel like one long page that
     * would not end, with no sense of progress — the only thing that changed was the
     * words. Giving each step its own hue makes moving forward visible before any of
     * the text is read.
     *
     * Taken from the theme's own container roles rather than hardcoded, so the three
     * are guaranteed to be in the same family as the rest of the app and to stay
     * correct under a light scheme or dynamic colour.
     */
    val tint = when (state.step) {
        OnboardingStep.PROBLEM -> scheme.tertiaryContainer
        OnboardingStep.TRUST -> scheme.secondaryContainer
        OnboardingStep.PERMISSION -> scheme.primaryContainer
    }

    // Animated so the change reads as one page becoming the next, rather than a
    // flash between two unrelated screens.
    val animatedTint by animateColorAsState(
        targetValue = tint,
        animationSpec = tween(durationMillis = 550),
        label = "onboarding-tint",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        // Blended rather than used at full strength: these container
                        // colours are meant to sit behind a label, and a whole screen
                        // of one is loud enough to fight the text on top of it.
                        lerp(scheme.background, animatedTint, 0.45f),
                    ),
                ),
            ),
    ) {
        // A Box, unlike Surface, sets no ambient content colour, which would leave it
        // at the root default of black on these dark backgrounds.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
        AnimatedContent(targetState = state.step, label = "onboarding-step") { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Spacer(Modifier.weight(1f))

                when (step) {
                    OnboardingStep.PROBLEM -> ProblemStep(onNext = viewModel::onNext)

                    OnboardingStep.TRUST -> TrustStep(onNext = viewModel::onNext)

                    OnboardingStep.PERMISSION -> PermissionStep(
                        isImporting = state.isImporting,
                        onAllow = {
                            permissionLauncher.launch(MediaPermission.requestedPermissions())
                        },
                        onPickManually = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }

                Spacer(Modifier.weight(1f))
            }
        }
        }
    }
}

@Composable
private fun ProblemStep(onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_problem_title),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.onboarding_problem_subtitle),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_problem_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_problem_cta))
        }
    }
}

@Composable
private fun TrustStep(onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_trust_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_trust_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_trust_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_trust_cta))
        }
    }
}

@Composable
private fun PermissionStep(
    isImporting: Boolean,
    onAllow: () -> Unit,
    onPickManually: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_permission_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_permission_body),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (isImporting) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.onboarding_importing), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_permission_allow))
            }
            // Limited Mode is offered up front, not hidden behind a denial. It is
            // required by Play policy, so it may as well be a real choice.
            TextButton(onClick = onPickManually, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_permission_pick))
            }
            Text(
                text = stringResource(R.string.onboarding_permission_pick_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Photo picker selection cap. Above this the picker itself gets unwieldy. */
private const val MAX_PICK = 100
