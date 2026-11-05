package io.grimoire.app.ui.screen.webview

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

internal class LifecycleWebView(context: Context, owner: LifecycleOwner) : WebView(context) {
    private val binding = WebViewLifecycleBinding(owner.lifecycle,
        resume = { onResume() }, pause = { onPause() }, destroy = { destroyReleasedView() })

    override fun destroy() = binding.release()

    private fun destroyReleasedView() {
        stopLoading()
        (parent as? ViewGroup)?.removeView(this)
        super.destroy()
    }
}

internal class WebViewLifecycleBinding(
    private val lifecycle: Lifecycle,
    private val resume: () -> Unit,
    private val pause: () -> Unit,
    private val destroy: () -> Unit,
) : DefaultLifecycleObserver {
    private var released = false

    init {
        lifecycle.addObserver(this)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) pause()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!released) resume()
    }

    override fun onPause(owner: LifecycleOwner) {
        if (!released) pause()
    }

    fun release() {
        if (released) return
        released = true
        lifecycle.removeObserver(this)
        try {
            pause()
        } finally {
            destroy()
        }
    }
}
