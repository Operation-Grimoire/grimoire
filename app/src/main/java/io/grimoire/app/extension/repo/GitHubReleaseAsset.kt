package io.grimoire.app.extension.repo

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * `https://github.com/{owner}/{repo}/releases/download/{tag}/{name}` parsed out.
 *
 * GitHub's REST API serves private-repo release assets reliably via the
 * `/repos/{owner}/{repo}/releases/assets/{id}` endpoint with
 * `Accept: application/octet-stream`. The browser-style download URL accepts
 * Bearer auth for public repos but not consistently for private ones, so we
 * detect these URLs and route them through the API instead.
 */
internal data class GitHubReleaseAsset(
    val owner: String,
    val repo: String,
    val tag: String,
    val name: String,
) {
    fun releaseByTagUrl(): String =
        "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"

    fun assetDownloadUrl(assetId: Long): String =
        "https://api.github.com/repos/$owner/$repo/releases/assets/$assetId"

    companion object {
        fun parse(url: String): GitHubReleaseAsset? = parse(url.toHttpUrlOrNull())

        fun parse(url: HttpUrl?): GitHubReleaseAsset? {
            if (url == null || url.host != "github.com") return null
            val segs = url.pathSegments
            // /OWNER/REPO/releases/download/TAG/NAME → 6 segments
            if (segs.size < 6) return null
            if (segs[2] != "releases" || segs[3] != "download") return null
            val owner = segs[0]
            val repo = segs[1]
            val tag = segs[4]
            // Asset names can contain slashes if the publisher uploaded one
            // — re-join everything past the tag.
            val name = segs.drop(5).joinToString("/")
            if (owner.isEmpty() || repo.isEmpty() || tag.isEmpty() || name.isEmpty()) return null
            return GitHubReleaseAsset(owner, repo, tag, name)
        }
    }
}
