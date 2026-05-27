package io.grimoire.app.ui

/**
 * A pre-filled "Add repository" request that arrived via a deep link
 * (e.g. https://grimoireapp.org/add-repo?name=…&url=… or grimoire://add-repo?…).
 *
 * Carried as `pendingAddRepo: StateFlow<PendingAddRepo?>` through
 * [AppNavigation] into `ExtensionsScreen`, mirroring how `pendingEpubUri`
 * routes an EPUB intent into `LibraryScreen`.
 */
data class PendingAddRepo(val name: String?, val url: String)

/**
 * Pure validation for an inbound add-repo deep link. Extracted so it's
 * testable on the JVM without an Android `Uri` instance.
 *
 * The dialog already validates `http(s)` + `.json`; we apply the same rule
 * here so a malformed link silently no-ops rather than opening a useless
 * dialog the user can't submit.
 */
internal fun parseAddRepoLink(
    scheme: String?,
    host: String?,
    path: String?,
    urlParam: String?,
    nameParam: String?,
): PendingAddRepo? {
    val matches = (scheme == "https" && host == "grimoireapp.org" && path == "/add-repo") ||
        (scheme == "grimoire" && host == "add-repo")
    if (!matches) return null
    val url = urlParam?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    if (!lower.endsWith(".json")) return null
    val name = nameParam?.trim()?.takeIf { it.isNotEmpty() }
    return PendingAddRepo(name = name, url = url)
}
