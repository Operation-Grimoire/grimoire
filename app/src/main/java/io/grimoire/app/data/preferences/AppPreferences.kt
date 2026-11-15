package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(store: PreferenceStore) {
    // Whether the bundled default extension repo has been seeded. Guards a
    // one-time insert so a user who removes the default repo doesn't get it
    // re-added on the next launch.
    val defaultReposSeeded = store.getBoolean("default_repos_seeded", false)
}
