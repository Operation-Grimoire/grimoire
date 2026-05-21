package io.grimoire.app.ui.screen.webview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.source.WebViewLoginSource
import io.grimoire.app.extension.ExtensionManager
import javax.inject.Inject

/** Key set on the caller's back-stack entry when a login WebView session ends. */
const val SOURCE_LOGIN_RESULT_KEY = "source_login_result"

@HiltViewModel
class SourceLoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    extensionManager: ExtensionManager,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }

    val sourceName: String = loaded?.info?.label ?: pkg

    private val loginSource = loaded?.source as? WebViewLoginSource

    /** Page to load in the WebView; null when the source does not support login. */
    val loginUrl: String? = loginSource?.loginUrl

    /** Reaching a URL containing this means login finished; null = close manually. */
    val loginSuccessUrl: String? = loginSource?.loginSuccessUrl
}
