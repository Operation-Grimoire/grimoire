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
     * NovelUpdates release-group identifiers this source corresponds to, declared
     * via `@SourceInfo(novelUpdatesGroups = …)` and emitted into `index.json` by
     * the extensions-ci index generator. Each entry is a group URL slug or display
     * name, matched case-insensitively against a series' release group slugs/names
     * and English publisher. Lets the in-app NovelUpdates browser tell — without
     * installing the extension — that a series' release group is available as a
     * source. Defaults to empty for entries that don't declare it.
     */
    val novelUpdatesGroups: List<String> = emptyList(),
)
