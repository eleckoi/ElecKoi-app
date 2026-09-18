package com.eleckoi.android.engine.agent.adapter

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal data class AdapterHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

internal object AdapterHttpCodec {
    private const val MaxRequestLineBytes = 4 * 1024
    private const val MaxHeaderLineBytes = 8 * 1024
    private const val MaxHeaderBytes = 32 * 1024
    private const val MaxChunkHeaderBytes = 1 * 1024
    private const val MaxBodyBytes = 24 * 1024 * 1024
    private const val MaxClientErrorChars = 2_000

    fun readRequest(input: InputStream): AdapterHttpRequest {
        val requestLine = readAsciiLine(input, MaxRequestLineBytes)
        val parts = requestLine.split(' ')
        require(parts.size == 3 && parts[2].startsWith("HTTP/1.")) {
            "HTTP request line 无效"
        }
        val headers = linkedMapOf<String, String>()
        var totalHeaderBytes = requestLine.length
        while (true) {
            val line = readAsciiLine(input, MaxHeaderLineBytes)
            totalHeaderBytes += line.length
            require(totalHeaderBytes <= MaxHeaderBytes) { "HTTP headers 过大" }
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            require(separator > 0) { "HTTP header 无效" }
            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }
        val transferEncoding = headers["transfer-encoding"]?.lowercase().orEmpty()
        val body = when {
            transferEncoding.isBlank() -> readFixedBody(input, headers["content-length"])
            transferEncoding.split(',').map(String::trim).lastOrNull() == "chunked" -> {
                require(headers["content-length"].isNullOrBlank()) {
                    "chunked 请求不能同时包含 Content-Length"
                }
                readChunkedBody(input)
            }
            else -> error("不支持的 Transfer-Encoding")
        }
        return AdapterHttpRequest(parts[0], parts[1].substringBefore('?'), headers, body)
    }

    fun writeProxyHeaders(
        output: OutputStream,
        status: Int,
        contentType: String,
    ) {
        val safeContentType = contentType
            .takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
            ?: "text/event-stream; charset=utf-8"
        output.write(
            (
                "HTTP/1.1 $status OK\r\n" +
                    "Content-Type: $safeContentType\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII),
        )
        output.flush()
    }

    fun writeJsonError(output: OutputStream, status: Int, message: String) {
        val safeMessage = message.take(MaxClientErrorChars)
        val body =
            "{\"error\":{\"message\":${JsonPrimitive(safeMessage)},\"type\":\"eleckoi_adapter_error\"}}"
                .toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            in 500..599 -> "Bad Gateway"
            else -> "Error"
        }
        output.write(
            (
                "HTTP/1.1 $status $reason\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII),
        )
        output.write(body)
        output.flush()
    }

    fun writeJson(output: OutputStream, body: JsonElement, status: Int = 200) {
        val encoded = body.toString().toByteArray(Charsets.UTF_8)
        require(encoded.size <= MaxBodyBytes) { "JSON 响应超过 24 MiB" }
        val reason = when (status) {
            200 -> "OK"
            201 -> "Created"
            else -> "OK"
        }
        output.write(
            (
                "HTTP/1.1 $status $reason\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${encoded.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII),
        )
        output.write(encoded)
        output.flush()
    }

    fun readBounded(stream: InputStream?, maxBytes: Int): ByteArray {
        if (stream == null) return ByteArray(0)
        return stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (output.size() < maxBytes) {
                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun readFixedBody(input: InputStream, rawLength: String?): ByteArray {
        val length = rawLength?.toIntOrNull() ?: 0
        require(length in 1..MaxBodyBytes) { "Provider 请求体大小无效" }
        return readExactly(input, length, "Provider 请求体提前结束")
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input, MaxChunkHeaderBytes).substringBefore(';').trim()
            val size = sizeLine.toLongOrNull(16) ?: error("chunked 请求块大小无效")
            require(size in 0..MaxBodyBytes.toLong()) { "chunked 请求块过大" }
            if (size == 0L) {
                var trailerBytes = 0
                while (true) {
                    val trailer = readAsciiLine(input, MaxHeaderLineBytes)
                    trailerBytes += trailer.length
                    require(trailerBytes <= MaxHeaderBytes) { "chunked trailers 过大" }
                    if (trailer.isEmpty()) break
                }
                break
            }
            require(output.size().toLong() + size <= MaxBodyBytes) {
                "Provider 请求超过 24 MiB"
            }
            output.write(readExactly(input, size.toInt(), "chunked 请求块提前结束"))
            require(readAsciiLine(input, 0).isEmpty()) { "chunked 请求块结尾无效" }
        }
        require(output.size() > 0) { "Provider 请求体不能为空" }
        return output.toByteArray()
    }

    private fun readExactly(input: InputStream, length: Int, eofMessage: String): ByteArray {
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) throw EOFException(eofMessage)
            offset += count
        }
        return body
    }

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size <= maxBytes) {
            val value = input.read()
            if (value < 0) throw EOFException("HTTP 请求提前结束")
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        require(bytes.size <= maxBytes) { "HTTP 行过长" }
        return bytes.toByteArray().toString(Charsets.ISO_8859_1)
    }
}
