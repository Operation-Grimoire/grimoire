package io.grimoire.desktop

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

internal val browserLoopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
internal val browserJson = Gson()

/** A private, framed connection. Chromium may write to the helper's standard output. */
internal class BrowserWire(socket: Socket) {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())

    fun read(): JsonArray {
        val size = input.readInt()
        if (size !in 1..MAX_FRAME_SIZE) throw IOException("Invalid browser message length")
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonArray
    }

    @Synchronized
    fun write(message: JsonArray) {
        val bytes = browserJson.toJson(message).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_SIZE) throw IOException("Browser message is too large")
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private companion object {
        const val MAX_FRAME_SIZE = 8 * 1024 * 1024
    }
}

internal fun browserRequest(id: Long, operation: String, arguments: Array<out String?>) = JsonArray().apply {
    add(id)
    add(operation)
    arguments.forEach { add(it) }
}

internal fun BrowserWire.reply(id: Long, result: JsonElement = JsonNull.INSTANCE) =
    write(JsonArray().apply { add(id); add(true); add(result) })

internal fun BrowserWire.fail(id: Long, failure: Throwable) =
    write(JsonArray().apply { add(id); add(false); add(JsonPrimitive(failure.message ?: "Browser operation failed")) })
