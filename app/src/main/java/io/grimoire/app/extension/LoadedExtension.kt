package io.grimoire.app.extension

import io.grimoire.api.source.AdultContent
import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.sourceIdFor
import kotlinx.coroutines.CancellationException

data class LoadedExtension(
    val info: ExtensionInfo,
    val source: Source,
) {
    // Loading the class succeeds even when an old APK lacks a current API method.
    // Probe metadata at the boundary, before publishing it as a usable source.
    private val metadata = runCatching {
        source.lang.code to (source.javaClass.getAnnotation(SourceInfo::class.java)?.adultContent ?: AdultContent.NONE)
    }.onFailure {
        if (it is CancellationException || it is VirtualMachineError || it is ThreadDeath) throw it
    }
    val lang: String? get() = metadata.getOrNull()?.first
    val isCompatible: Boolean get() = metadata.isSuccess

    /** Canonical identity the app keys saved novels by — derived from the package, not [Source.id]. */
    val id: Long get() = sourceIdFor(info.packageName)

    /** Adult-content rating declared on the source's `@SourceInfo`; [AdultContent.NONE] if absent. */
    val adultContent: AdultContent
        get() = metadata.getOrNull()?.second ?: AdultContent.NONE
}
