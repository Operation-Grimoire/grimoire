package io.grimoire.app.ui.screen.updates

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    viewModel: UpdateIssuesViewModel = hiltViewModel(),
) {
    val issues by viewModel.issues.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.update_issues_title)) },
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
                        onClick = { onOpenNovel(issue.sourcePackage, issue.novelUrl) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueRow(issue: UpdateIssueEntity, onClick: () -> Unit) {
    val isError = issue.severity == UpdateIssueSeverity.ERROR.ordinal
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
                localizedIssueMessage(issue.message),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Text(
                text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(issue.occurredAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
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
