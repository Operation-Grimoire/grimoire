package io.grimoire.app.data.athenaeum

import kotlinx.serialization.Serializable

/**
 * One observation submitted to POST /ingest/observations. Mirrors the backend
 * ObservationItem; SERIES and CHAPTER share the shape and use the relevant
 * fields. Nulls are omitted on the wire (Json.explicitNulls = false), so e.g.
 * releaseKind is left for the platform default and chapter-only fields don't
 * appear on SERIES items.
 */
@Serializable
data class ObservationItem(
    val kind: String, // "SERIES" | "CHAPTER"
    val platformDomain: String,
    val url: String,
    val title: String? = null,
    val altTitles: List<AltTitleDto> = emptyList(),
    val status: String? = null,
    val language: String? = null,
    val format: String? = null,
    val releaseKind: String? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val seriesUrl: String? = null,
    val number: Double? = null,
    val publishedAt: String? = null, // ISO-8601
)

@Serializable
data class AltTitleDto(val title: String, val type: String = "SYNONYM", val language: String? = null)

@Serializable
data class SubmitObservationsRequest(val observations: List<ObservationItem>)
