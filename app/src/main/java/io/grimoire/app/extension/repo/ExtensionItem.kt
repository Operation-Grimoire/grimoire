package io.grimoire.app.extension.repo

import io.grimoire.app.extension.LoadedExtension

sealed interface ExtensionItem {
    val packageName: String
    val name: String
    val lang: String
    val versionName: String
    val iconUrl: String?

    data class InstalledOnly(
        val loaded: LoadedExtension,
    ) : ExtensionItem {
        override val packageName = loaded.info.packageName
        override val name = loaded.info.label.substringAfter(": ", loaded.info.label)
        override val lang = loaded.source.lang
        override val versionName = loaded.info.versionName
        override val iconUrl: String? = null
    }

    data class Available(
        val remote: RemoteExtension,
    ) : ExtensionItem {
        override val packageName = remote.pkg
        override val name = remote.name
        override val lang = remote.lang
        override val versionName = remote.versionName
        override val iconUrl: String? = remote.iconUrl
    }

    data class Installed(
        val loaded: LoadedExtension,
        val remote: RemoteExtension,
    ) : ExtensionItem {
        override val packageName = loaded.info.packageName
        override val name = remote.name
        override val lang = loaded.source.lang
        override val versionName = loaded.info.versionName
        override val iconUrl: String? = remote.iconUrl
        val hasUpdate: Boolean = remote.versionCode > loaded.info.versionCode
        val remoteVersionName: String = remote.versionName
        val apkUrl: String = remote.url
    }
}
