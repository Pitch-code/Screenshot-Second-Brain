package com.shelfie.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Surface(modifier = modifier.fillMaxSize()) {
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

@Composable
private fun ProblemStep(onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "You have hundreds of screenshots.",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "You can't find any of them.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Shelfie reads the text inside every screenshot so you can just " +
                "search for what you remember.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Show me")
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
            text = "Everything happens on your phone.",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Shelfie reads the text in your screenshots so you can search them. " +
                "That reading happens here, on this device.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Shelfie has no internet permission. Android will not let it open a " +
                "network connection, so it physically cannot upload anything. There is " +
                "no account and no sign-up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
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
            text = "Shelfie needs to see your screenshots",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "That's the whole job. With access, your newest screenshots become " +
                "searchable in about ten seconds.",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (isImporting) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text("Reading your selection…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text("Allow access")
            }
            // Limited Mode is offered up front, not hidden behind a denial. It is
            // required by Play policy, so it may as well be a real choice.
            TextButton(onClick = onPickManually, modifier = Modifier.fillMaxWidth()) {
                Text("Pick screenshots manually instead")
            }
            Text(
                text = "Picking manually works too — search, categories and actions all " +
                    "behave the same. You just choose which screenshots Shelfie sees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Photo picker selection cap. Above this the picker itself gets unwieldy. */
private const val MAX_PICK = 100
