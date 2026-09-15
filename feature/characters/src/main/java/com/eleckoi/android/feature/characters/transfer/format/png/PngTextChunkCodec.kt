package com.eleckoi.android.feature.characters.transfer.format.png

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

internal object PngTextChunkCodec {
    private val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private const val MaxPngBytes = 96 * 1024 * 1024
    private const val MaxChunkBytes = 96 * 1024 * 1024

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }

    fun readText(bytes: ByteArray): Map<String, String> {
        val chunks = parse(bytes)
        return buildMap {
            chunks.filter { it.type == "tEXt" }.forEach { chunk ->
                val separator = chunk.data.indexOf(0)
                if (separator in 1 until chunk.data.lastIndex) {
                    val keyword = chunk.data.copyOfRange(0, separator)
                        .toString(StandardCharsets.ISO_8859_1)
                    val text = chunk.data.copyOfRange(separator + 1, chunk.data.size)
                        .toString(StandardCharsets.ISO_8859_1)
                    put(keyword, text)
                }
            }
        }
    }

    fun writeText(
        bytes: ByteArray,
        values: Map<String, String>,
        removeKeys: Set<String> = emptySet(),
    ): ByteArray {
        require(values.isNotEmpty()) { "没有要写入的角色卡数据" }
        val replacementKeys = (values.keys + removeKeys).map(String::lowercase).toSet()
        val chunks = parse(bytes).filterNot { chunk ->
            if (chunk.type != "tEXt") return@filterNot false
            val separator = chunk.data.indexOf(0)
            if (separator <= 0) return@filterNot false
            chunk.data.copyOfRange(0, separator)
                .toString(StandardCharsets.ISO_8859_1)
                .lowercase() in replacementKeys
        }
        return writeTextChunks(chunks, values)
    }

    /** Replaces every existing tEXt chunk so the result contains only [values] as text metadata. */
    fun writeTextOnly(bytes: ByteArray, values: Map<String, String>): ByteArray {
        require(values.isNotEmpty()) { "没有要写入的 PNG 文本数据" }
        return writeTextChunks(parse(bytes).filterNot { it.type == "tEXt" }, values)
    }

    private fun writeTextChunks(
        sourceChunks: List<Chunk>,
        values: Map<String, String>,
    ): ByteArray {
        val chunks = sourceChunks.toMutableList()
        val endIndex = chunks.indexOfLast { it.type == "IEND" }
        require(endIndex >= 0) { "PNG 缺少结束标记" }
        val additions = values.map { (keyword, value) ->
            require(keyword.isNotBlank() && keyword.length <= 79 && '\u0000' !in keyword) {
                "PNG 元数据键无效"
            }
            val data = keyword.toByteArray(StandardCharsets.ISO_8859_1) +
                byteArrayOf(0) + value.toByteArray(StandardCharsets.ISO_8859_1)
            require(data.size <= MaxChunkBytes) { "角色卡数据过大" }
            Chunk("tEXt", data)
        }
        chunks.addAll(endIndex, additions)
        return encode(chunks).also { result ->
            require(result.size <= MaxPngBytes) { "PNG 角色卡不能超过 96 MB" }
        }
    }

    private fun parse(bytes: ByteArray): List<Chunk> {
        require(bytes.size <= MaxPngBytes) { "角色卡图片不能超过 96 MB" }
        require(isPng(bytes)) { "这不是 PNG 角色卡" }
        val chunks = mutableListOf<Chunk>()
        var offset = signature.size
        while (offset < bytes.size) {
            require(bytes.size - offset >= 12) { "PNG 数据不完整" }
            val length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            require(length in 0..MaxChunkBytes) { "PNG 数据块大小无效" }
            val end = offset.toLong() + 12L + length
            require(end <= bytes.size) { "PNG 数据块不完整" }
            val type = bytes.copyOfRange(offset + 4, offset + 8).toString(StandardCharsets.US_ASCII)
            require(type.length == 4 && type.all { it.code in 65..122 }) { "PNG 数据块类型无效" }
            val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
            chunks += Chunk(type, data)
            offset = end.toInt()
            if (type == "IEND") {
                require(offset == bytes.size) { "PNG 结束标记后存在异常数据" }
                return chunks
            }
        }
        error("PNG 缺少结束标记")
    }

    private fun encode(chunks: List<Chunk>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(signature)
        chunks.forEach { chunk ->
            output.write(intBytes(chunk.data.size))
            val type = chunk.type.toByteArray(StandardCharsets.US_ASCII)
            output.write(type)
            output.write(chunk.data)
            val crc = CRC32().apply {
                update(type)
                update(chunk.data)
            }
            output.write(intBytes(crc.value.toInt()))
        }
        return output.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array()

    private data class Chunk(val type: String, val data: ByteArray)
}
