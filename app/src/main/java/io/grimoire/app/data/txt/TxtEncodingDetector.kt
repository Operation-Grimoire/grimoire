package io.grimoire.app.data.txt

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TxtEncodingDetector {
    private val GB18030: Charset = Charset.forName("GB18030")
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    fun detect(sample: ByteArray): Charset = when {
        sample.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
        sample.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
        sample.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
        decode(sample, Charsets.UTF_8) != null -> Charsets.UTF_8
        else -> detectLegacyEncoding(sample)
    }

    private fun detectLegacyEncoding(sample: ByteArray): Charset {
        val shiftJis = decode(sample, SHIFT_JIS)
        if (shiftJis != null && shiftJis.countKana() >= 2) return SHIFT_JIS

        val gb18030 = decode(sample, GB18030)
        if (gb18030 != null && gb18030.countCjk() >= 2) return GB18030
        if (shiftJis != null && shiftJis.countCjk() >= 2) return SHIFT_JIS
        return WINDOWS_1252
    }

    private fun decode(sample: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val output = CharBuffer.allocate(sample.size.coerceAtLeast(1))
        return try {
            // A sample may end in the middle of a multibyte character. Actual
            // malformed sequences still fail; only the final partial one is allowed.
            val result = decoder.decode(ByteBuffer.wrap(sample), output, false)
            if (result.isError) null else {
                output.flip()
                output.toString()
            }
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { this[it] == bytes[it].toByte() }

    private fun String.countKana(): Int = count {
        it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF'
    }

    private fun String.countCjk(): Int = count { it in '\u4E00'..'\u9FFF' }
}
