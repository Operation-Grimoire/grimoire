package io.grimoire.app.ui.screen.settings.about

import io.grimoire.app.ui.icon.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.BuildConfig
import io.grimoire.app.R
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.data.preferences.UpdateChannel
import io.grimoire.app.ui.update.AppUpdateViewModel
import io.grimoire.app.ui.update.CheckState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity() as? ComponentActivity
            ?: error("AboutSettingsScreen must be hosted in a ComponentActivity")
    }
    val updateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("–")
    }
    val versionCode = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
    }
    val gitSha = remember { BuildConfig.GIT_SHA.take(7).ifBlank { "–" } }
    val buildSummary = stringResource(
        R.string.about_version_summary,
        version ?: "–",
        versionCode,
        gitSha,
        BuildConfig.EXTENSIONS_API_VERSION,
    )
    val buildCopiedMessage = stringResource(R.string.about_build_copied)
    val clipboard = LocalClipboardManager.current
    val checkState by updateViewModel.checkState.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val autoPopupEnabled by viewModel.autoPopupEnabled.collectAsState()
    val autoChangelogEnabled by viewModel.autoChangelogEnabled.collectAsState()
    val isLoadingChangelog by updateViewModel.isLoadingChangelog.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.about_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Grimoire", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text(version ?: "–") },
                )
            }

            item {
                val checking = checkState is CheckState.Checking
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_check_updates)) },
                    supportingContent = {
                        Text(
                            when (val s = checkState) {
                                is CheckState.Idle -> stringResource(R.string.about_check_updates_idle)
                                is CheckState.Checking -> stringResource(R.string.about_checking)
                                is CheckState.UpToDate -> stringResource(R.string.about_latest_version)
                                is CheckState.Error -> s.message.ifBlank {
                                    stringResource(R.string.update_unknown_error)
                                }
                            },
                            color = if (checkState is CheckState.Error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        when (checkState) {
                            is CheckState.Checking -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            is CheckState.UpToDate -> Icon(
                                AppIcons.CheckCircle,
                                contentDescription = stringResource(R.string.about_up_to_date),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            is CheckState.Error -> Icon(
                                AppIcons.Refresh,
                                contentDescription = stringResource(R.string.action_retry),
                            )
                            is CheckState.Idle -> Icon(
                                AppIcons.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = !checking) {
                        updateViewModel.checkForUpdates()
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_show_changelog)) },
                    supportingContent = {
                        Text(stringResource(R.string.about_show_changelog_summary, version ?: "–"))
                    },
                    trailingContent = {
                        if (isLoadingChangelog) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isLoadingChangelog) {
                        updateViewModel.showCurrentChangelog()
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                Text(
                    text = stringResource(R.string.about_updates_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(UpdateChannel.entries.toList()) { entry ->
                val selected = channel == entry
                ListItem(
                    leadingContent = {
                        RadioButton(selected = selected, onClick = {
                            viewModel.setChannel(entry)
                            updateViewModel.resetCheckState()
                        })
                    },
                    headlineContent = { Text(entry.localizedDisplayName()) },
                    supportingContent = { Text(entry.localizedDescription()) },
                    modifier = Modifier.clickable {
                        viewModel.setChannel(entry)
                        updateViewModel.resetCheckState()
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_update_popups)) },
                    supportingContent = {
                        Text(stringResource(R.string.about_update_popups_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = autoPopupEnabled,
                            onCheckedChange = { viewModel.setAutoPopupEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAutoPopupEnabled(!autoPopupEnabled)
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_changelog_after_update)) },
                    supportingContent = {
                        Text(stringResource(R.string.about_changelog_after_update_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = autoChangelogEnabled,
                            onCheckedChange = { viewModel.setAutoChangelogEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAutoChangelogEnabled(!autoChangelogEnabled)
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                Text(
                    text = stringResource(R.string.about_build_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_build_details)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.about_build_details_summary,
                                versionCode,
                                gitSha,
                                BuildConfig.EXTENSIONS_API_VERSION,
                            ),
                        )
                    },
                    trailingContent = {
                        Icon(AppIcons.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(buildSummary))
                        Toast.makeText(context, buildCopiedMessage, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun UpdateChannel.localizedDisplayName(): String = stringResource(
    when (this) {
        UpdateChannel.STABLE -> R.string.about_channel_stable
        UpdateChannel.BETA -> R.string.about_channel_beta
    },
)

@Composable
private fun UpdateChannel.localizedDescription(): String = stringResource(
    when (this) {
        UpdateChannel.STABLE -> R.string.about_channel_stable_summary
        UpdateChannel.BETA -> R.string.about_channel_beta_summary
    },
)
