package io.grimoire.app.extension

import io.grimoire.api.source.AdultContent
import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.sourceIdFor

data class LoadedExtension(
    val info: ExtensionInfo,
    val source: Source,
) {
    /** Canonical identity the app keys saved novels by — derived from the package, not [Source.id]. */
    val id: Long get() = sourceIdFor(info.packageName)

    /** Adult-content rating declared on the source's `@SourceInfo`; [AdultContent.NONE] if absent. */
    val adultContent: AdultContent
        get() = source.javaClass.getAnnotation(SourceInfo::class.java)?.adultContent ?: AdultContent.NONE
}
