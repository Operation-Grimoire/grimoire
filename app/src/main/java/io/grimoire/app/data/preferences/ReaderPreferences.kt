package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferences @Inject constructor(store: PreferenceStore) {
    val markAsReadThreshold = store.getInt("reader_mark_as_read_threshold", 85)
}
