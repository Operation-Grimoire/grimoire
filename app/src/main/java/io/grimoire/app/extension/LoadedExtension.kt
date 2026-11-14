package io.grimoire.app.extension

import io.grimoire.api.source.AdultContent
import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.sourceIdFor
import kotlinx.coroutines.CancellationException

data class LoadedExtension(
    val info: ExtensionInfo,
    val source: Source?,
) {
    // Rejected API requirements keep package identity without loading a Source.
    // Legacy/supported APKs still need a metadata probe for missing API methods.
    private val metadata = runCatching {
        val current = requireNotNull(source)
        current.lang.code to (current.javaClass.getAnnotation(SourceInfo::class.java)?.adultContent ?: AdultContent.NONE)
    }.onFailure {
        if (it is CancellationException || it is VirtualMachineError || it is ThreadDeath) throw it
    }
    val lang: String? get() = metadata.getOrNull()?.first
    val isCompatible: Boolean get() = metadata.isSuccess
    val requiredApiVersion: String?
        get() = if (source == null) declaredExtensionApi(info.minimumApiVersion) else null

    /** Canonical identity the app keys saved novels by — derived from the package, not [Source.id]. */
    val id: Long get() = sourceIdFor(info.packageName)

    /** Adult-content rating declared on the source's `@SourceInfo`; [AdultContent.NONE] if absent. */
    val adultContent: AdultContent
        get() = metadata.getOrNull()?.second ?: AdultContent.NONE
}
