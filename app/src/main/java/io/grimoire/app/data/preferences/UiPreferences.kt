package io.grimoire.app.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorTheme(val displayName: String) {
    DEFAULT("Default"),
    GRIMOIRE("Grimoire"),
    OCEAN("Ocean"),
    SUNSET("Sunset"),
    FOREST("Forest"),
    ROSE("Rose"),
    MIDNIGHT("Midnight"),
}

/** Everything the activity needs before it can draw the first themed frame. */
data class UiThemeState(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val colorTheme: ColorTheme,
    val hapticsEnabled: Boolean,
    val renderSynopsisLinks: Boolean,
)

@Singleton
class UiPreferences @Inject constructor(store: PreferenceStore) {
    val themeMode = store.getEnum("theme_mode", ThemeMode.SYSTEM)
    val useDynamicColor = store.getBoolean("use_dynamic_color", true)
    val colorTheme = store.getEnum("color_theme", ColorTheme.DEFAULT)
    val hapticsEnabled = store.getBoolean("haptics_enabled", true)

    /** Render embedded links in synopses/descriptions as tappable links. */
    val renderSynopsisLinks = store.getBoolean("render_synopsis_links", true)

    /**
     * Single combined flow so the activity can await one first emission of the
     * persisted values (no default-theme flash) and recompose once per change
     * instead of once per preference.
     */
    fun themeState(): Flow<UiThemeState> = combine(
        themeMode.changes(),
        useDynamicColor.changes(),
        colorTheme.changes(),
        hapticsEnabled.changes(),
        renderSynopsisLinks.changes(),
    ) { mode, dynamic, color, haptics, synopsisLinks ->
        UiThemeState(mode, dynamic, color, haptics, synopsisLinks)
    }
}
