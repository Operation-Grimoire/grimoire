package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-tour completion markers. Each tour stores the highest version the user
 * has finished/skipped under its own key, so tours version and replay
 * independently. 0 = never finished.
 */
@Singleton
class TourPreferences @Inject constructor(private val store: PreferenceStore) {
    fun completedVersion(tourKey: String): Preference<Int> =
        store.getInt("tour_completed_$tourKey", 0)
}
