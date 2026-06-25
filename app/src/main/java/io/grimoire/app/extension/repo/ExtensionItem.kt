package io.grimoire.app.extension.repo

import io.grimoire.api.source.AdultContent
import io.grimoire.app.extension.LoadedExtension

sealed interface ExtensionItem {
    val packageName: String
    val name: String
    val lang: String
    val versionName: String
    val iconUrl: String?
    val adultContent: AdultContent

    /** Whether the source serves any adult content (PARTIAL or FULL) — drives the 18+ badge. */
    val isAdult: Boolean get() = adultContent != AdultContent.NONE

    data class InstalledOnly(
        val loaded: LoadedExtension,
    ) : ExtensionItem {
        override val packageName = loaded.info.packageName
        override val name = loaded.info.label.substringAfter(": ", loaded.info.label)
        override val lang = loaded.source.lang.code
        override val versionName = loaded.info.versionName
        override val iconUrl: String? = null
        override val adultContent = loaded.adultContent
    }

    data class Available(
        val remote: RemoteExtension,
    ) : ExtensionItem {
        override val packageName = remote.pkg
        override val name = remote.name
        override val lang = remote.lang
        override val versionName = remote.versionName
        override val iconUrl: String? = remote.iconUrl
        override val adultContent = parseAdultContent(remote.adultContent)
    }

    data class Installed(
        val loaded: LoadedExtension,
        val remote: RemoteExtension,
    ) : ExtensionItem {
        override val packageName = loaded.info.packageName
        override val name = remote.name
        override val lang = loaded.source.lang.code
        override val versionName = loaded.info.versionName
        override val iconUrl: String? = remote.iconUrl
        override val adultContent = loaded.adultContent
        val hasUpdate: Boolean = remote.versionCode > loaded.info.versionCode
        val remoteVersionName: String = remote.versionName
        val apkUrl: String = remote.url
    }
}

/** Resolve an index.json adult-content name to [AdultContent], tolerant of case / unknowns. */
private fun parseAdultContent(raw: String): AdultContent =
    AdultContent.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        ?: AdultContent.NONE
