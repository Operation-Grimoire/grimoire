package io.grimoire.app.ui.screen.crash

import io.grimoire.app.ui.icon.*
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.crash.buildCrashIssueUrl
import io.grimoire.app.ui.component.PlainTooltipIconButton
import kotlinx.coroutines.launch
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CrashReportViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.crash_no_browser)
    val copiedMessage = stringResource(R.string.crash_details_copied)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.crash_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (report.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.crash_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                stringResource(R.string.crash_description),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = report,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        // Open the prefilled issue in the user's browser; their
                        // GitHub session lives there, not in an in-app WebView.
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, buildCrashIssueUrl(report).toUri()),
                            )
                        }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar(noBrowserMessage)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(AppIcons.BugReport, contentDescription = null)
                    Text(stringResource(R.string.crash_report_github))
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = stringResource(R.string.crash_copy_details))
                }
            }

            TextButton(
                onClick = {
                    viewModel.dismiss()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    }
}
