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
}

/** Anonymous event names — no per-novel identifiers. */
object AnalyticsEvent {
    const val APP_OPENED = "app_opened"
    const val EXTENSION_INSTALLED = "extension_installed"
    const val CHAPTER_READ = "chapter_read"
    const val BACKUP_CREATED = "backup_created"
}
