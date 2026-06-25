package io.grimoire.app.extension.repo

import kotlinx.serialization.Serializable

@Serializable
data class RemoteExtension(
    val name: String,
    val pkg: String,
    val lang: String,
    val baseUrl: String,
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val url: String,
    val iconUrl: String? = null,
    val sha256: String? = null,
    /**
     * Adult-content rating from `@SourceInfo(adultContent = …)`, emitted into
     * `index.json` by the extensions-ci index generator. One of the
     * [io.grimoire.api.source.AdultContent] names (`NONE` / `PARTIAL` / `FULL`);
     * defaults to `NONE` for entries that don't declare it.
     */
    val adultContent: String = "NONE",
    /**
     * NovelUpdates release-group identifiers this source corresponds to, declared
     * via `@SourceInfo(novelUpdatesGroups = …)` and emitted into `index.json` by
     * the extensions-ci index generator. Each entry is a group URL slug or display
     * name, matched against a series' release group slugs/names on an
     * alphanumeric-only, case-insensitive key (so "Cale Red Hair" matches the
     * scraped "caleredhair"). Lets the in-app NovelUpdates browser tell — without
     * installing the extension — that a series' release group is available as a
     * source. Defaults to empty for entries that don't declare it.
     */
    val novelUpdatesGroups: List<String> = emptyList(),
)
