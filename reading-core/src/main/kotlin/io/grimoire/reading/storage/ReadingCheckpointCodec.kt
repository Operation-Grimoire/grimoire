package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.anchor.*
import kotlinx.serialization.json.*

/** Versioned protocol envelope, shared by both databases. It contains no source body or pixel hints. */
fun encodeReadingCheckpoint(state: ReadingCheckpointState): String = buildJsonObject {
    require(state.anchor == null || state.anchor.key == state.binding.observation.key) { "Checkpoint identity mismatch" }
    put("format", 1)
    val binding = state.binding
    val observation = binding.observation
    put("session", binding.id)
    put("source", observation.key.novel.sourceId)
    put("novel", observation.key.novel.url)
    put("chapter", observation.key.url)
    put("scope", observation.scope)
    put("generation", observation.generation)
    put("presence", observation.presence.name)
    put("logical", binding.logicalRevision)
    put("sequence", state.sequence)
    put("anchor", state.anchor?.let { anchor -> buildJsonObject {
        put("version", anchor.modelVersion)
        put("logical", anchor.logicalRevision)
        when (val position = anchor.position) {
            AnchorPosition.Start -> put("kind", "start")
            AnchorPosition.End -> put("kind", "end")
            is AnchorPosition.Block -> {
                put("kind", "block"); put("ordinal", position.ordinal)
                put("fingerprint", position.fingerprint); put("offset", position.textOffset)
                put("precision", position.precision.name)
            }
        }
    } } ?: JsonNull)
}.toString()

/** Unknown/corrupt envelopes fail without overwriting evidence or manufacturing an empty checkpoint. */
fun decodeReadingCheckpoint(encoded: String): ReadingCheckpointState {
    val obj = Json.parseToJsonElement(encoded).jsonObject
    require(obj.integer("format") == 1) { "Unsupported checkpoint format" }
    val key = ChapterKey(NovelKey(obj.getValue("source").jsonPrimitive.long, obj.string("novel")), obj.string("chapter"))
    val observation = ChapterStorageObservation(key, obj.string("scope"), obj.string("generation"),
        ChapterContentPresence.valueOf(obj.string("presence")))
    val binding = ReadingSessionBinding(obj.string("session"), observation, obj.string("logical"))
    val anchor = obj.getValue("anchor").takeUnless { it == JsonNull }?.jsonObject?.let { a ->
        val position = when (a.string("kind")) {
            "start" -> AnchorPosition.Start
            "end" -> AnchorPosition.End
            "block" -> AnchorPosition.Block(a.integer("ordinal"), a.string("fingerprint"), a.integer("offset"),
                AnchorPrecision.valueOf(a.string("precision")))
            else -> error("Unsupported checkpoint position")
        }
        ReadingAnchor(key, a.string("logical"), position, a.integer("version"))
    }
    return ReadingCheckpointState(binding, obj.getValue("sequence").jsonPrimitive.long, anchor).also {
        require(binding.id.isNotBlank() && it.sequence >= 0 && (it.sequence == 0L || anchor != null)) { "Invalid checkpoint state" }
    }
}

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.integer(key: String) = getValue(key).jsonPrimitive.int
