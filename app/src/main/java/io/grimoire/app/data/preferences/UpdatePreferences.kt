package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateChannel { STABLE, BETA }

@Singleton
class UpdatePreferences @Inject constructor(store: PreferenceStore) {
    val channel = store.getEnum("update_channel", UpdateChannel.STABLE)

    // Whether the update-available dialog is shown automatically on app launch.
    val autoPopupEnabled = store.getBoolean("update_auto_popup_enabled", true)

    // Whether the "what's new" dialog is shown on first launch after an update.
    // Independent of autoPopupEnabled, which controls the *update available*
    // dialog (before install) instead.
    val autoChangelogEnabled = store.getBoolean("update_auto_changelog_enabled", true)

    // Release tag the user chose to skip; the auto popup stays hidden while the
    // latest release still matches this value.
    val skippedVersion = store.getString("update_skipped_version", "")
}
