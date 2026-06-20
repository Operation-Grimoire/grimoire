package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(store: PreferenceStore) {
    val lastSeenVersionCode = store.getInt("last_seen_version_code", 0)
    val lastSeenVersionName = store.getString("last_seen_version_name", "")

    // Whether the bundled default extension repo has been seeded. Guards a
    // one-time insert so a user who removes the default repo doesn't get it
    // re-added on the next launch.
    val defaultReposSeeded = store.getBoolean("default_repos_seeded", false)

    // Highest onboarding-tour version the user has completed/skipped. The tour
    // auto-runs while this is below CURRENT_TOUR_VERSION; bumping that constant
    // re-shows an updated tour. 0 = never seen.
    val tourCompletedVersion = store.getInt("tour_completed_version", 0)
}
