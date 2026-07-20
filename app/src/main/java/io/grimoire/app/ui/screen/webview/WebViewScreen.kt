package io.grimoire.app.ui.screen.webview

import io.grimoire.app.ui.icon.*
import android.annotation.SuppressLint
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import io.grimoire.app.R

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedUrlMessage = stringResource(R.string.webview_url_copied)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(currentUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (isLoading) {
                        PlainTooltipIconButton(onClick = {
                            webView?.stopLoading()
                            isLoading = false
                        }, tooltip = stringResource(R.string.action_stop_loading)) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_stop_loading))
                        }
                    } else {
                        PlainTooltipIconButton(onClick = { webView?.reload() }, tooltip = stringResource(R.string.action_refresh)) {
                            Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                    Box {
                        PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = stringResource(R.string.action_more_actions)) {
                            Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.action_more_actions))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_back)) },
                                enabled = canGoBack,
                                leadingIcon = { Icon(AppIcons.ArrowBack, null) },
                                onClick = { menuExpanded = false; webView?.goBack() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_forward)) },
                                enabled = canGoForward,
                                leadingIcon = { Icon(AppIcons.ArrowForward, null) },
                                onClick = { menuExpanded = false; webView?.goForward() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy_url)) },
                                leadingIcon = { Icon(AppIcons.ContentCopy, null) },
                                onClick = {
                                    menuExpanded = false
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                    scope.launch { snackbarHostState.showSnackbar(copiedUrlMessage) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(AppIcons.Share, null) },
                                onClick = {
                                    menuExpanded = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_in_browser)) },
                                leadingIcon = { Icon(AppIcons.OpenInBrowser, null) },
                                onClick = {
                                    menuExpanded = false
                                    val view = Intent(Intent.ACTION_VIEW, currentUrl.toUri()).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(view)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                currentUrl = url
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                currentUrl = url
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                isLoading = false
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress < 100
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
