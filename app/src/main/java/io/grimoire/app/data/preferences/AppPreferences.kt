package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(store: PreferenceStore) {
    val lastSeenVersionCode = store.getInt("last_seen_version_code", 0)
}
