package io.grimoire.app.ui.screen.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Hosts a WebView so the user can sign in to a source (including social-login
 * providers, which need OAuth redirects). Unlike the plain browser
 * [WebViewScreen] this explicitly accepts first- and third-party cookies and
 * flushes them to disk, so the session survives app restarts and is picked up
 * by the source's OkHttp client. Login is considered finished once the WebView
 * reaches the source's `loginSuccessUrl`, or when the user taps "Done".
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLoginScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceLoginViewModel = hiltViewModel(),
) {
    val loginUrl = viewModel.loginUrl

    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        onNavigateBack()
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Log in to ${viewModel.sourceName}") },
                actions = {
                    TextButton(onClick = { finish() }) { Text("Done") }
                },
            )
        },
    ) { padding ->
        if (loginUrl == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "This source does not support signing in.",
                    style = MaterialTheme.typography.bodyLarge,
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
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            // Social sign-in redirects through provider domains,
                            // so third-party cookies must be allowed too.
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Google (and some other providers) block OAuth in
                            // Android WebViews, which they detect from the
                            // "; wv" token in the default user-agent. Present a
                            // plain Chrome user-agent so social sign-in works.
                            settings.userAgentString = settings.userAgentString
                                .replace("; wv)", ")")
                                .replace(Regex("""Version/\d+\.\d+\s*"""), "")
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                    val success = viewModel.loginSuccessUrl
                                    if (success != null && url.startsWith(success)) {
                                        finish()
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }
                            }
                            loadUrl(loginUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
