package io.grimoire.reading.storage

import io.grimoire.reading.*
import io.grimoire.reading.state.*
import kotlinx.serialization.json.*
import java.security.MessageDigest

private fun keyJson(key: ChapterKey) = buildJsonObject {
    put("source", key.novel.sourceId); put("novel", key.novel.url); put("chapter", key.url)
}
private fun valueJson(value: ReadingCompletionState) = buildJsonObject {
    put("key", keyJson(value.key)); put("status", value.status.name)
    put("firstReadAt", value.firstReadAt?.let(::JsonPrimitive) ?: JsonNull)
}
private fun observationJson(value: ChapterStorageObservation) = buildJsonObject {
    put("key", keyJson(value.key)); put("scope", value.scope); put("generation", value.generation); put("presence", value.presence.name)
}
private fun bindingJson(value: ReadingStateBinding) = buildJsonObject {
    put("key", keyJson(value.key)); put("scope", value.scope); put("incarnation", value.incarnation); put("revision", value.intentRevision)
}
private fun writerJson(value: ReadingStateWriter?) = value?.let { buildJsonObject {
    put("id", it.id); put("scope", it.scope); put("content", observationJson(it.content)); put("logical", it.logicalRevision); put("sequence", it.sequence)
} } ?: JsonNull

fun encodeReadingStateRecord(value: ReadingStateRecord): String = buildJsonObject {
    put("format", 1); put("incarnation", value.incarnation); put("revision", value.intentRevision); put("value", valueJson(value.value))
    put("visual", writerJson(value.visual)); put("audio", writerJson(value.audio))
}.toString()

fun decodeReadingStateRecord(encoded: String): ReadingStateRecord {
    val j = Json.parseToJsonElement(encoded).jsonObject
    require(j.number("format").int == 1) { "Unsupported reading state format" }
    val value = j.getValue("value").jsonObject.readValue()
    fun writer(name: String) = j.getValue(name).takeUnless { it == JsonNull }?.jsonObject?.let {
        ReadingStateWriter(it.string("id"), it.string("scope"), it.getValue("content").jsonObject.readObservation(),
            it.string("logical"), it.number("sequence").long).also { w ->
            require(w.id.isNotBlank() && w.scope.isNotBlank() && w.content.key == value.key && w.sequence >= 0 && fingerprint.matches(w.logicalRevision))
        }
    }
    return ReadingStateRecord(j.string("incarnation"), j.number("revision").long, value, writer("visual"), writer("audio")).also {
        require(it.incarnation.isNotBlank() && it.intentRevision >= 0)
    }
}

fun encodeReadingStateReceipt(value: ReadingStateReceipt): String = buildJsonObject {
    put("format", 1); put("command", value.commandId); put("incarnation", value.incarnation); put("fingerprint", value.fingerprint)
    put("value", valueJson(value.valueAtCommit)); put("revision", value.intentRevisionAtCommit)
}.toString()

fun decodeReadingStateReceipt(encoded: String): ReadingStateReceipt {
    val j = Json.parseToJsonElement(encoded).jsonObject
    require(j.number("format").int == 1) { "Unsupported reading state receipt" }
    return ReadingStateReceipt(j.string("command"), j.string("incarnation"), j.string("fingerprint"),
        j.getValue("value").jsonObject.readValue(), j.number("revision").long).also {
        require(it.commandId.isNotBlank() && it.incarnation.isNotBlank() && fingerprint.matches(it.fingerprint) && it.intentRevisionAtCommit >= 0)
    }
}

/** Hash semantic input, including caller evidence. Bodies/cache URLs are represented by canonical revision and projection ordinals. */
fun fingerprintReadingStateCommand(command: ReadingStateCommand): String {
    val json = buildJsonObject {
        put("format", 1); put("id", command.id); put("binding", bindingJson(command.binding)); put("sequence", command.sequence)
        put("session", command.session?.let { buildJsonObject {
            put("id", it.id); put("binding", bindingJson(it.binding)); put("content", observationJson(it.content))
            put("logical", it.document.logicalRevision); put("documentKey", keyJson(it.document.key)); put("kind", it.kind.name)
        } } ?: JsonNull)
        put("input", buildJsonObject {
            put("key", keyJson(command.input.key))
            when (val input = command.input) {
                is ReadingCompletionInput.MarkRead -> { put("kind", "read"); put("at", input.atMillis) }
                is ReadingCompletionInput.MarkUnread -> put("kind", "unread")
                is ReadingCompletionInput.Next -> {
                    put("kind", "next"); put("loaded", input.loadedChapter?.let(::keyJson) ?: JsonNull)
                    put("successor", input.hasSuccessor); put("at", input.atMillis)
                }
                is ReadingCompletionInput.Visual -> {
                    put("kind", "visual"); put("at", input.atMillis)
                    put("policy", when (val p = input.policy) {
                        AutomaticReadPolicy.Disabled -> "disabled"
                        AutomaticReadPolicy.AtEnd -> "end"
                        is AutomaticReadPolicy.Percent -> "percent:${p.threshold}"
                        is AutomaticReadPolicy.ItemsFromEnd -> "remaining:${p.count}"
                    })
                    val e = input.evidence; val c = input.current
                    put("load", e.loadId); put("observed", observationJson(e.observation)); put("logical", e.logicalRevision)
                    put("first", e.viewport.first); put("last", e.viewport.last); put("total", e.viewport.total); put("fraction", e.viewport.fraction)
                    put("currentLoad", c.loadId); put("currentObserved", observationJson(c.observation))
                    put("documentKey", c.projection.document?.key?.let(::keyJson) ?: JsonNull)
                    put("currentLogical", c.projection.document?.logicalRevision?.let(::JsonPrimitive) ?: JsonNull)
                    put("leading", c.projection.leadingStart); put("trailing", c.projection.trailingEnd)
                    put("ordinals", JsonArray(c.projection.body.map { JsonPrimitive(it.canonicalOrdinal) }))
                    put("inputEnabled", c.userInputEnabled); put("current", c.observationCurrent)
                }
                is ReadingCompletionInput.Audio -> {
                    put("kind", "audio"); put("at", input.atMillis)
                    val e = input.evidence; val c = input.current
                    put("playback", e.playbackId); put("observed", observationJson(e.observation)); put("logical", e.logicalRevision)
                    put("utterances", e.utteranceCount); put("completed", e.completedUtterance); put("engine", e.engineCompleted)
                    put("currentPlayback", c.playbackId); put("currentObserved", observationJson(c.observation))
                    put("documentKey", keyJson(c.document.key)); put("currentLogical", c.document.logicalRevision)
                    put("currentUtterances", c.utteranceCount); put("playing", c.playing); put("current", c.observationCurrent)
                }
            }
        })
    }.toString()
    // Exact UTF-16 code units preserve isolated surrogates too; UTF-8 encoder replacement must not alias requests.
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("grimoire-reading-state-command-v1\u0000".toByteArray(Charsets.US_ASCII))
    for (c in json) { digest.update((c.code ushr 8).toByte()); digest.update(c.code.toByte()) }
    return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

private val fingerprint = Regex("[a-f0-9]{64}")
private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.let { require(it.isString); it.content }
private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.also { require(!it.isString) }
private fun JsonObject.readKey() = ChapterKey(NovelKey(number("source").long, string("novel")), string("chapter"))
private fun JsonObject.readValue() = ReadingCompletionState(getValue("key").jsonObject.readKey(), ReadStatus.valueOf(string("status")),
    getValue("firstReadAt").takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(!it.isString); it.long })
private fun JsonObject.readObservation() = ChapterStorageObservation(getValue("key").jsonObject.readKey(), string("scope"), string("generation"),
    ChapterContentPresence.valueOf(string("presence")))
