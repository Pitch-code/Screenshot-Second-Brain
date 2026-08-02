package com.shelfie.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.StarBorder
import com.shelfie.core.model.IndexState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.shelfie.feature.settings.R
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.billing.BillingState
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
import com.shelfie.core.media.IndexingQuota
import com.shelfie.core.designsystem.component.resolve
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
    val exportFilename = stringResource(R.string.settings_export_filename)
    val exportSaved = stringResource(R.string.settings_export_saved)
    val exportFailed = stringResource(R.string.settings_export_failed)
    val clipboard = LocalClipboardManager.current
    val diagCopied = stringResource(R.string.settings_diag_copied)
    var copyRequested by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(copyRequested) {
        if (copyRequested) {
            snackbarHostState.showSnackbar(diagCopied)
            copyRequested = false
        }
    }

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
            snackbarHostState.showSnackbar(if (ok) exportSaved else exportFailed)
        }
    }

    val resolvedMessage = state.message?.resolve() ?: state.failureText
    LaunchedEffect(resolvedMessage) {
        resolvedMessage?.let {
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
            item { SectionHeader(stringResource(R.string.settings_section_full)) }
            item { PurchaseCard(state = state, onPurchase = viewModel::onPurchase) }

            item { SectionHeader(stringResource(R.string.settings_section_access)) }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            when (state.access) {
                                MediaAccess.FULL -> stringResource(R.string.settings_access_full)
                                MediaAccess.PARTIAL -> stringResource(R.string.settings_access_partial)
                                MediaAccess.DENIED -> stringResource(R.string.settings_access_denied)
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (state.access == MediaAccess.FULL) {
                                    R.string.settings_access_full_detail
                                } else {
                                    R.string.settings_access_limited_detail
                                },
                            ),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { context.openAppSettings() }) {
                            Text(stringResource(R.string.settings_change))
                        }
                    },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_detail)) },
                    trailingContent = {
                        Switch(
                            checked = state.useDynamicColor,
                            onCheckedChange = viewModel::onDynamicColorChanged,
                        )
                    },
                )
            }

            item {
                SectionHeader(
                    stringResource(com.shelfie.core.designsystem.R.string.folder_section),
                )
            }
            if (state.folders.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            com.shelfie.core.designsystem.R.string.folder_section_empty,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(items = state.folders, key = { it.folder.id }) { entry ->
                    ListItem(
                        leadingContent = {
                            Icon(entry.folder.icon.icon, contentDescription = null)
                        },
                        headlineContent = { Text(entry.folder.name) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    com.shelfie.core.designsystem.R.plurals.folder_item_count,
                                    entry.count,
                                    entry.count,
                                ),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onDeleteFolder(entry.folder.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        com.shelfie.core.designsystem.R.string.folder_delete,
                                    ),
                                )
                            }
                        },
                    )
                }
                item {
                    // Spelled out because "delete folder" reads as though the
                    // screenshots inside go with it.
                    Text(
                        text = stringResource(
                            com.shelfie.core.designsystem.R.string.folder_delete_explainer,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(stringResource(R.string.settings_section_rules)) }
            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_rules_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(items = state.rules, key = { it.id }) { rule ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_rule_keyword, rule.keyword)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.settings_rule_target,
                                    stringResource(rule.category.labelRes),
                                ),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onDeleteRule(rule.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.settings_rule_delete),
                                )
                            }
                        },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(stringResource(R.string.settings_section_data)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_export_detail))
                    },
                    trailingContent = {
                        TextButton(onClick = { exportLauncher.launch(exportFilename) }) {
                            Text(stringResource(R.string.settings_export_cta))
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.settings_privacy_detail))
                            Text(
                                text = stringResource(R.string.settings_privacy_no_network),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { context.openPrivacyPolicy() }) {
                            Text(stringResource(R.string.settings_privacy_cta))
                        }
                    },
                )
            }

            // Restoration already happens automatically on every launch; this is the
            // recovery path for a failed check, a signed-out Play Store, or the
            // common case of having bought on a different Google account.
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_restore_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_restore_detail)) },
                    trailingContent = {
                        TextButton(onClick = viewModel::onRestorePurchase) {
                            Text(stringResource(R.string.settings_restore_cta))
                        }
                    },
                )
            }

            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Outlined.StarBorder, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_rate_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_rate_detail)) },
                    trailingContent = {
                        TextButton(onClick = { context.openPlayStoreReviews() }) {
                            Text(stringResource(R.string.settings_rate_cta))
                        }
                    },
                )
            }

            /*
             * Collapsed by default, and phrased as a question rather than as
             * "Diagnostics / Last error".
             *
             * The old version put raw state names and a stack-trace-ish error string
             * on screen permanently. That was invaluable while chasing a silent OCR
             * failure — the app carries no crash reporting by design, so an
             * unreported failure is an invisible one — but for someone whose app is
             * working it reads as "something is broken and you cannot tell what".
             * The information is kept; it just no longer greets everybody.
             */
            item {
                ListItem(
                    modifier = Modifier.clickable { diagnosticsExpanded = !diagnosticsExpanded },
                    headlineContent = {
                        Text(stringResource(R.string.settings_diag_section))
                    },
                    supportingContent = { Text(state.healthSummary()) },
                    trailingContent = {
                        Icon(
                            imageVector = if (diagnosticsExpanded) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            contentDescription = null,
                        )
                    },
                )
            }

            if (diagnosticsExpanded) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_diag_technical))
                        },
                        supportingContent = {
                            Column {
                                Text(state.stateCounts.joinToString("  ") { "${it.state.name}=${it.count}" })
                                state.lastError?.let { error ->
                                    Text(
                                        text = stringResource(
                                            R.string.settings_diag_last_error_value,
                                            error,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
                item {
                    Row {
                        TextButton(onClick = viewModel::onRetryFailed) {
                            Text(stringResource(R.string.settings_diag_retry))
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(viewModel.diagnosticsText()))
                                copyRequested = true
                            },
                        ) {
                            Text(stringResource(R.string.settings_diag_copy))
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_detail)) },
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
                    Text(stringResource(R.string.settings_unlocked_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.settings_unlocked_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.settings_purchase_title),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // The prompt only appears when there is something concrete to
                    // gain, rather than nagging on a timer.
                    Text(
                        text = if (state.quota.hasHeldBackItems) {
                            pluralStringResource(
                                R.plurals.settings_purchase_held_back,
                                state.quota.heldBack,
                                state.quota.heldBack,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.settings_purchase_free_tier,
                                IndexingQuota.FREE_INDEX_LIMIT,
                                IndexingQuota.FREE_INDEX_LIMIT,
                            )
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
                            Text(stringResource(R.string.settings_purchase_cta, billing.formattedPrice))
                        }

                        BillingState.Connecting -> Text(
                            text = stringResource(R.string.settings_purchase_checking),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        is BillingState.Unavailable -> Text(
                            text = stringResource(R.string.settings_purchase_unavailable),
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
            Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Opens the Play Store listing on its reviews section.
 *
 * Two-step by necessity. `market://` hands straight to the installed Play app,
 * which is the only way to reach the in-app rating UI; if Play is absent (an
 * emulator, a de-Googled ROM, a sideloaded build) that intent throws, so it falls
 * back to the web listing in a browser.
 *
 * `showAllReviews=true` is what lands on the ratings section rather than the top of
 * the listing.
 *
 * Note this always targets [PLAY_PACKAGE_NAME], never the running package: debug
 * builds are `com.shelfie.app.debug`, which is not on Play, so using
 * `packageName` here would reliably open a "not found" page during development.
 *
 * Shelfie still holds no INTERNET permission. Launching an intent is not
 * networking — whichever app handles it does its own.
 */
private fun android.content.Context.openPlayStoreReviews() {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$PLAY_PACKAGE_NAME&showAllReviews=true".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE_NAME&showAllReviews=true"
            .toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { startActivity(marketIntent) }
        .recoverCatching { startActivity(webIntent) }
}

/** Replace with the hosted policy URL before release. */
/**
 * The hosted privacy policy.
 *
 * Served by GitHub Pages from `docs/privacy.html` in this repository, so the policy
 * lives alongside the code it describes — a claim about what the app does and the
 * code that does it cannot then drift apart unnoticed.
 *
 * Play will not review a listing without a reachable policy URL.
 */
private const val PRIVACY_POLICY_URL =
    "https://pitch-code.github.io/Screenshot-Second-Brain/privacy.html"

/**
 * The release application id, which is what Play knows about.
 *
 * Deliberately a constant rather than `context.packageName`, so debug builds link
 * to the real listing instead of to a package Play has never heard of.
 */
private const val PLAY_PACKAGE_NAME = "com.shelfie.app"


/**
 * Plain-language health line, shown collapsed above the technical details.
 *
 * Deliberately says what the numbers *mean*. "QUOTA_HELD: 6" is accurate and
 * useless to the person reading it; "6 held back by the free limit" tells them both
 * what happened and what would change it. Terminal states are only mentioned when
 * they are non-zero, so a healthy app reads as one short reassuring sentence.
 */
@Composable
private fun SettingsUiState.healthSummary(): String {
    fun countOf(vararg states: IndexState) =
        stateCounts.filter { it.state in states }.sumOf { it.count }

    val searchable = countOf(IndexState.INDEXED)
    val working = countOf(IndexState.PENDING, IndexState.IN_PROGRESS, IndexState.FAILED)
    val held = countOf(IndexState.QUOTA_HELD)
    val unreadable = countOf(IndexState.SKIPPED)

    if (searchable == 0 && working == 0 && held == 0 && unreadable == 0) {
        return stringResource(R.string.settings_health_nothing)
    }

    val parts = buildList {
        add(pluralStringResource(R.plurals.settings_health_searchable, searchable, searchable))
        if (working > 0) {
            add(pluralStringResource(R.plurals.settings_health_reading, working, working))
        }
        if (held > 0) {
            add(pluralStringResource(R.plurals.settings_health_held, held, held))
        }
        if (unreadable > 0) {
            add(pluralStringResource(R.plurals.settings_health_unreadable, unreadable, unreadable))
        }
    }

    return parts.joinToString(" · ")
}
