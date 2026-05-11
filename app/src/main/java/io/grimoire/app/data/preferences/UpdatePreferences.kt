package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateChannel { STABLE, BETA }

@Singleton
class UpdatePreferences @Inject constructor(store: PreferenceStore) {
    val channel = store.getEnum("update_channel", UpdateChannel.STABLE)
}
