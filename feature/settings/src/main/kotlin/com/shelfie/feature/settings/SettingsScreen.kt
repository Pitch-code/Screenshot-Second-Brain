package com.shelfie.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.billing.BillingState
import com.shelfie.core.designsystem.category.label
import com.shelfie.core.media.IndexingQuota
import com.shelfie.core.model.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Export via the system document picker: the user chooses where the file
    // lands, so no storage permission is needed.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = viewModel.buildExport()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray())
                    }
                }.isSuccess
            }
            snackbarHostState.showSnackbar(if (ok) "Export saved" else "Couldn't save the export")
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader("Shelfie Full") }
            item { PurchaseCard(state = state, onPurchase = viewModel::onPurchase) }

            item { SectionHeader("Access") }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            when (state.access) {
                                MediaAccess.FULL -> "All screenshots"
                                MediaAccess.PARTIAL -> "Only screenshots you selected"
                                MediaAccess.DENIED -> "Only screenshots you pick manually"
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            if (state.access == MediaAccess.FULL) {
                                "Shelfie indexes new screenshots automatically."
                            } else {
                                "Limited Mode. Everything works, on a smaller set."
                            },
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { context.openAppSettings() }) { Text("Change") }
                    },
                )
            }

            item { SectionHeader("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Match my wallpaper colours") },
                    supportingContent = { Text("Uses Android's dynamic colour on Android 12 and newer.") },
                    trailingContent = {
                        Switch(
                            checked = state.useDynamicColor,
                            onCheckedChange = viewModel::onDynamicColorChanged,
                        )
                    },
                )
            }

            item { SectionHeader("My sorting rules") }
            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = "No rules yet. When Shelfie files something in the wrong " +
                            "place, open it and tap Change — the rule is created there.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(items = state.rules, key = { it.id }) { rule ->
                    ListItem(
                        headlineContent = { Text("\"${rule.keyword}\"") },
                        supportingContent = { Text("goes to ${rule.category.label}") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onDeleteRule(rule.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete rule")
                            }
                        },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader("Your data") }
            item {
                ListItem(
                    headlineContent = { Text("Export my data") },
                    supportingContent = {
                        Text("Saves everything Shelfie has extracted as a text file.")
                    },
                    trailingContent = {
                        TextButton(onClick = { exportLauncher.launch("shelfie-export.txt") }) {
                            Text("Export")
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Privacy policy") },
                    supportingContent = {
                        Text(
                            "Shelfie has no internet permission. It cannot upload your " +
                                "screenshots.",
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { context.openPrivacyPolicy() }) { Text("Read") }
                    },
                )
            }

            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Shelfie") },
                    supportingContent = { Text("Screenshot finder. Works offline. One payment.") },
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun PurchaseCard(state: SettingsUiState, onPurchase: (android.app.Activity) -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.quota.isUnlimited || state.billing is BillingState.Owned -> {
                    Text("Unlocked", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Unlimited screenshots, forever. Thank you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text("One payment. No subscription.", style = MaterialTheme.typography.titleMedium)

                    // The prompt only appears when there is something concrete to
                    // gain, rather than nagging on a timer.
                    Text(
                        text = if (state.quota.hasHeldBackItems) {
                            "${state.quota.heldBack} older screenshots aren't searchable " +
                                "yet. Unlocking indexes all of them."
                        } else {
                            "Free covers your newest ${IndexingQuota.FREE_INDEX_LIMIT} " +
                                "screenshots. Unlocking covers everything."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when (val billing = state.billing) {
                        is BillingState.Available -> Button(
                            onClick = {
                                (context as? android.app.Activity)?.let(onPurchase)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Unlock for ${billing.formattedPrice}")
                        }

                        BillingState.Connecting -> Text(
                            text = "Checking with Google Play…",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        is BillingState.Unavailable -> Text(
                            text = "Purchases aren't available on this device right now. " +
                                "Everything else keeps working.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        BillingState.Owned -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

private fun android.content.Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

private fun android.content.Context.openPrivacyPolicy() {
    // Opens the browser, which does the networking. Shelfie itself still has no
    // network permission.
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Replace with the hosted policy URL before release. */
private const val PRIVACY_POLICY_URL = "https://shelfie.app/privacy"
