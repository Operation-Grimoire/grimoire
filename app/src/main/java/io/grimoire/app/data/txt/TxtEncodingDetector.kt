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

    fun detect(sample: ByteArray, complete: Boolean): Charset = when {
        sample.startsWith(0xFF, 0xFE, 0x00, 0x00) ||
            sample.startsWith(0x00, 0x00, 0xFE, 0xFF) ->
            throw IllegalArgumentException("UTF-32 TXT files are not supported")
        sample.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
        sample.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
        sample.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
        decode(sample, Charsets.UTF_8, complete) != null -> Charsets.UTF_8
        else -> detectLegacyEncoding(sample, complete)
    }

    private fun detectLegacyEncoding(sample: ByteArray, complete: Boolean): Charset {
        val shiftJis = decode(sample, SHIFT_JIS, complete)
        if (shiftJis != null && shiftJis.countKana() >= 2) return SHIFT_JIS

        val gb18030 = decode(sample, GB18030, complete)
        if (gb18030?.hasStrongCjkEvidence() == true) return GB18030
        if (shiftJis?.hasStrongCjkEvidence() == true) return SHIFT_JIS
        return WINDOWS_1252
    }

    private fun decode(sample: ByteArray, charset: Charset, endOfInput: Boolean): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val output = CharBuffer.allocate(sample.size.coerceAtLeast(1))
        return try {
            // Only a sample truncated by the size limit may end in a partial
            // multibyte character. At EOF, an incomplete sequence is invalid.
            val result = decoder.decode(ByteBuffer.wrap(sample), output, endOfInput)
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

    private fun String.hasStrongCjkEvidence(): Boolean {
        val cjk = countCjk()
        val latin = count {
            it in 'A'..'Z' || it in 'a'..'z' || it in '\u00C0'..'\u00FF'
        }
        return cjk >= 2 && cjk * 4 >= latin
    }
}
