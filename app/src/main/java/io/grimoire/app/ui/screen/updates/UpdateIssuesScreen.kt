package io.grimoire.app.ui.screen.updates

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.AutoRetryOnReturn
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import io.grimoire.app.data.local.entity.UpdateIssueSeverity
import java.text.DateFormat
import java.util.Date
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateIssuesScreen(
    onNavigateBack: () -> Unit,
    onOpenNovel: (pkg: String, novelUrl: String) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenWebView: (url: String) -> Unit,
    viewModel: UpdateIssuesViewModel = hiltViewModel(),
) {
    val issues by viewModel.issues.collectAsState()
    val retrying by viewModel.retrying.collectAsState()

    // Coming back from the WebView after a Cloudflare block retries those
    // novels without waiting for the user to press Retry.
    AutoRetryOnReturn(blocked = { viewModel.hasCloudflareIssues() }) {
        viewModel.retryCloudflareIssues()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.update_issues_title)) },
                actions = {
                    if (issues.isNotEmpty()) {
                        PlainTooltipIconButton(
                            onClick = viewModel::retryAll,
                            tooltip = stringResource(R.string.update_issues_retry_all),
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.update_issues_retry_all))
                        }
                        PlainTooltipIconButton(
                            onClick = viewModel::dismissAll,
                            tooltip = stringResource(R.string.update_issues_dismiss_all),
                        ) {
                            Icon(AppIcons.DoneAll, contentDescription = stringResource(R.string.update_issues_dismiss_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (issues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.update_issues_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(issues.size) { index ->
                    val issue = issues[index]
                    IssueRow(
                        issue = issue,
                        isRetrying = issue.novelId in retrying,
                        onClick = { onOpenNovel(issue.sourcePackage, issue.novelUrl) },
                        onRetry = { viewModel.retry(issue) },
                        onInstallSource = onOpenExtensions,
                        onOpenWebView = { onOpenWebView(viewModel.webUrlFor(issue)) },
                        onDismiss = { viewModel.dismiss(issue) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueRow(
    issue: UpdateIssueEntity,
    isRetrying: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onInstallSource: () -> Unit,
    onOpenWebView: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isError = issue.severity == UpdateIssueSeverity.ERROR.ordinal
    val kind = issueKindFor(issue.message)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (isError) {
                Icon(
                    AppIcons.ErrorOutline,
                    contentDescription = stringResource(R.string.status_error),
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                Icon(
                    AppIcons.WarningAmber,
                    contentDescription = stringResource(R.string.status_warning),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        headlineContent = { Text(issue.novelTitle) },
        supportingContent = {
            Text(
                "${localizedIssueMessage(issue.message)} · " +
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(issue.occurredAt)),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The fix, per issue kind: install the missing source, solve
                // the challenge in the WebView, or just retry the refresh.
                when {
                    isRetrying -> Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    kind == UpdateIssueKind.SOURCE_MISSING -> PlainTooltipIconButton(
                        onClick = onInstallSource,
                        tooltip = stringResource(R.string.update_issue_install_source),
                    ) {
                        Icon(AppIcons.Extension, contentDescription = stringResource(R.string.update_issue_install_source))
                    }
                    kind == UpdateIssueKind.CLOUDFLARE -> PlainTooltipIconButton(
                        onClick = onOpenWebView,
                        tooltip = stringResource(R.string.action_open_in_webview),
                    ) {
                        Icon(AppIcons.Language, contentDescription = stringResource(R.string.action_open_in_webview))
                    }
                    else -> PlainTooltipIconButton(
                        onClick = onRetry,
                        tooltip = stringResource(R.string.action_retry),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.action_more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_retry)) },
                            leadingIcon = { Icon(AppIcons.Refresh, null) },
                            enabled = !isRetrying,
                            onClick = { menuOpen = false; onRetry() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_in_webview)) },
                            leadingIcon = { Icon(AppIcons.Language, null) },
                            onClick = { menuOpen = false; onOpenWebView() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_issue_dismiss)) },
                            leadingIcon = { Icon(AppIcons.Close, null) },
                            onClick = { menuOpen = false; onDismiss() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun localizedIssueMessage(message: String): String = when (message) {
    "Source not installed — skipped" -> stringResource(R.string.update_issue_source_missing)
    "Source returned no chapters — kept the existing list" ->
        stringResource(R.string.update_issue_no_chapters)
    "Source returned incomplete data — kept the previous title/cover" ->
        stringResource(R.string.update_issue_incomplete_data)
    else -> message
}
