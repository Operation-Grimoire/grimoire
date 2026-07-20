package io.grimoire.app.ui.screen.webview

import io.grimoire.app.ui.icon.*
import android.annotation.SuppressLint
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.R

private const val TAG = "SourceLogin"

/**
 * Hosts a WebView so the user can sign in to a source (including social-login
 * providers, which need OAuth redirects). Unlike the plain browser
 * [WebViewScreen] this explicitly accepts first- and third-party cookies and
 * flushes them to disk, so the session survives app restarts and is picked up
 * by the source's OkHttp client. Login is considered finished once the WebView
 * reaches the source's `loginSuccessUrl`, or when the user taps "Done".
 *
 * "Sign in with Google" (and other providers) open the account chooser in a
 * popup via `window.open`. That needs [WebView.getSettings].setSupportMultiple
 * Windows + a [WebChromeClient.onCreateWindow] that actually displays the child
 * WebView — otherwise the popup is created but never shown, leaving a blank
 * white screen and no way to finish the sign-in.
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
    var popupView by remember { mutableStateOf<WebView?>(null) }
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

    // True once we've navigated away from the login page, so a success-prefix
    // broad enough to also match the login page itself doesn't close instantly.
    fun isSuccessUrl(url: String): Boolean {
        val success = viewModel.loginSuccessUrl ?: return false
        if (loginUrl == null) return false
        val onLoginPage = url.trimEnd('/').substringBefore('?') ==
            loginUrl.trimEnd('/').substringBefore('?')
        return url.startsWith(success) && !onLoginPage
    }

    fun WebView.applyLoginSettings(uaFrom: WebView? = null) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // Social sign-in redirects through provider domains, so third-party
        // cookies must be allowed too.
        cookieManager.setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Required for window.open popups (e.g. the Google account chooser).
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = (uaFrom?.settings?.userAgentString ?: settings.userAgentString)
            // Google (and some other providers) block OAuth in Android WebViews,
            // which they detect from the "; wv" token in the default user-agent.
            // Present a plain Chrome user-agent so social sign-in works.
            .replace("; wv)", ")")
            .replace(Regex("""Version/\d+\.\d+\s*"""), "")
    }

    BackHandler(enabled = popupView != null) {
        popupView?.destroy()
        popupView = null
    }
    BackHandler(enabled = popupView == null && canGoBack) { webView?.goBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = { finish() }, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.source_login_title, viewModel.sourceName)) },
                actions = {
                    TextButton(onClick = { finish() }) { Text(stringResource(R.string.action_done)) }
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
                    stringResource(R.string.source_login_unsupported),
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
                            applyLoginSettings()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    isLoading = true
                                    if (BuildConfig.DEBUG) Log.d(TAG, "started: $url")
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                    if (BuildConfig.DEBUG) Log.d(TAG, "finished: $url")
                                    if (isSuccessUrl(url)) finish()
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (BuildConfig.DEBUG) {
                                        Log.w(
                                            TAG,
                                            "error ${error.errorCode} ${error.description} " +
                                                "for ${request.url} (mainFrame=${request.isForMainFrame})",
                                        )
                                    }
                                }
                            }
                            webChromeClient = loginChromeClient(
                                onProgress = { p -> loadProgress = p; isLoading = p < 100 },
                                onOpenPopup = { popup -> popupView = popup },
                                onClosePopup = { popupView = null },
                                buildPopup = { mainView ->
                                    WebView(mainView.context).apply {
                                        applyLoginSettings(uaFrom = mainView)
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String) {
                                                if (BuildConfig.DEBUG) Log.d(TAG, "popup: $url")
                                                if (isSuccessUrl(url)) finish()
                                            }
                                        }
                                    }
                                },
                            )
                            loadUrl(loginUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Overlay the popup (account chooser) on top of the main WebView.
                popupView?.let { popup ->
                    AndroidView(
                        factory = { ctx -> FrameLayout(ctx) },
                        update = { container ->
                            (popup.parent as? ViewGroup)?.removeView(popup)
                            container.removeAllViews()
                            container.addView(
                                popup,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * [WebChromeClient] that wires up `window.open` popups: [onCreateWindow] builds
 * a child WebView via [buildPopup], hands it to the transport so the engine
 * loads the popup URL into it, and surfaces it through [onOpenPopup] so the
 * caller can display it. The chromium [onCloseWindow] (or `window.close()`)
 * routes back through [onClosePopup].
 */
private fun loginChromeClient(
    onProgress: (Int) -> Unit,
    onOpenPopup: (WebView) -> Unit,
    onClosePopup: () -> Unit,
    buildPopup: (mainView: WebView) -> WebView,
): WebChromeClient = object : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val popup = buildPopup(view)
        popup.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) {
                window.destroy()
                onClosePopup()
            }
        }
        onOpenPopup(popup)
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
