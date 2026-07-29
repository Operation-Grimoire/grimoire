package io.grimoire.app.data.analytics

import android.content.Context
import com.aptabase.Aptabase
import io.grimoire.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, opt-in wrapper over Aptabase. Events are dropped unless the user has
 * enabled usage analytics **and** the build carries an Aptabase app key. Keep
 * events anonymous — names + [String]/[Int] props only, never titles/URLs/PII.
 */
@Singleton
class Analytics @Inject constructor() {

    @Volatile private var enabled = false
    @Volatile private var initialized = false

    private val hasKey = BuildConfig.APTABASE_APP_KEY.isNotBlank()

    /** Turn tracking on/off (driven by the user's preference). Initialises the SDK lazily. */
    fun setEnabled(context: Context, on: Boolean) {
        if (on && hasKey && !initialized) {
            runCatching { Aptabase.instance.initialize(context.applicationContext, BuildConfig.APTABASE_APP_KEY) }
                .onSuccess { initialized = true }
        }
        enabled = on
    }

    fun track(event: String, props: Map<String, Any> = emptyMap()) {
        if (enabled && initialized) {
            runCatching { Aptabase.instance.trackEvent(event, props) }
        }
    }

    /**
     * Tags an event with the source it happened on. [sourceId] is the stable
     * package-derived id (survives a rename); [sourceName] is the human label
     * for the dashboard. The source is the *extension*, not the novel — no PII.
     */
    fun trackSource(event: String, sourceId: Long, sourceName: String) =
        track(event, mapOf("source_id" to sourceId.toString(), "source" to sourceName))
}

/** Anonymous event names — no per-novel identifiers. */
object AnalyticsEvent {
    const val APP_OPENED = "app_opened"
    const val APP_BACKGROUNDED = "app_backgrounded"
    const val EXTENSION_INSTALLED = "extension_installed"
    const val EXTENSION_UNINSTALLED = "extension_uninstalled"
    const val NOVEL_ADDED = "novel_added"
    const val READER_OPENED = "reader_opened"
}
