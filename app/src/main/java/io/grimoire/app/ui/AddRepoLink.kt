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
 * True iff the URI's scheme/host/path matches one of the add-repo deep-link
 * forms. Used to decide whether `MainActivity` should consume (and clear)
 * the URI even when the query params don't parse — otherwise a malformed
 * `grimoire://add-repo?…` would fall through to the EPUB import flow.
 */
internal fun isAddRepoLink(scheme: String?, host: String?, path: String?): Boolean =
    (scheme == "https" && host == "grimoireapp.org" && path == "/add-repo") ||
        (scheme == "grimoire" && host == "add-repo")

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
    if (!isAddRepoLink(scheme, host, path)) return null
    val url = urlParam?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    if (!lower.endsWith(".json")) return null
    val name = nameParam?.trim()?.takeIf { it.isNotEmpty() }
    return PendingAddRepo(name = name, url = url)
}
