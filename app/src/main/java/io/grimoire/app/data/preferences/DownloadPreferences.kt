package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreferences @Inject constructor(store: PreferenceStore) {
    val concurrency = store.getInt("download_concurrency", 1)
}
