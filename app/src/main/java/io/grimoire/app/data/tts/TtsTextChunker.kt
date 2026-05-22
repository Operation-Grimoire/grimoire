package io.grimoire.app.data.tts

import io.grimoire.api.model.NovelPage
import java.text.BreakIterator
import java.util.Locale

/**
 * Splits a chapter's pages into speakable [Utterance]s. Each page is one paragraph;
 * short paragraphs become a single utterance, long ones are split on sentence
 * boundaries so no chunk exceeds the TTS input limit. Every utterance keeps its
 * originating [NovelPage.index] so the reader can highlight the spoken paragraph.
 */
object TtsTextChunker {

    /** Safely below [android.speech.tts.TextToSpeech.getMaxSpeechInputLength] (~4000). */
    private const val MAX_CHARS = 3500

    fun chunk(pages: List<NovelPage>, locale: Locale): List<Utterance> {
        val out = ArrayList<Utterance>()
        for (page in pages) {
            val text = page.text.trim()
            if (text.isBlank()) continue
            if (text.length <= MAX_CHARS) {
                out += Utterance(page.index, text)
            } else {
                for (slice in splitLongParagraph(text, locale)) {
                    out += Utterance(page.index, slice)
                }
            }
        }
        return out
    }

    private fun splitLongParagraph(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val slices = ArrayList<String>()
        val current = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end)
            when {
                current.isEmpty() && sentence.length > MAX_CHARS ->
                    slices += sentence.chunked(MAX_CHARS)
                current.length + sentence.length > MAX_CHARS -> {
                    slices += current.toString().trim()
                    current.setLength(0)
                    current.append(sentence)
                }
                else -> current.append(sentence)
            }
            start = end
            end = iterator.next()
        }
        if (current.isNotBlank()) slices += current.toString().trim()
        return slices.filter { it.isNotBlank() }
    }
}
