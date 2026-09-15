package com.eleckoi.android.sdk.author.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiEvent
import com.eleckoi.android.sdk.author.AuthorApiRoute
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal object AudioAuthorApi {
    val routes = listOf(
        "audio.play",
        "audio.pause",
        "audio.resume",
        "audio.stop",
        "audio.seek",
        "audio.getState",
        "audio.getPlaylist",
        "audio.setPlaylist",
        "audio.appendPlaylist",
        "audio.getSettings",
        "audio.setSettings",
    ).map { method ->
        AuthorApiRoute(AuthorApiCatalog.require(method)) { environment, params ->
            AuthorAudioHost.invoke(environment, method, params)
        }
    }
}

/**
 * One app-level audio host, matching the desktop renderer host. Author frontends may be split
 * across multiple Android WebViews, but the four playback channels must still be shared.
 */
internal object AuthorAudioHost {
    private val mutableEvents = MutableSharedFlow<AuthorApiEvent>(extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()

    private val entries = AuthorAudioChannel.entries.associateWith(::AudioEntry).toMutableMap()
    private val channelSettings = AuthorAudioChannel.entries
        .associateWith { AudioChannelSettings() }
        .toMutableMap()
    private var masterVolume = 1.0
    private var masterMuted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun invoke(
        environment: AuthorApiEnvironment,
        method: String,
        params: JsonObject,
    ): JsonElement {
        val conversationId = environment.conversationId()
        return when (method) {
            "audio.play" -> play(environment, conversationId, params)
            "audio.pause" -> pause(conversationId, params.channel())
            "audio.resume" -> resume(conversationId, params.channel())
            "audio.stop" -> stop(conversationId, params.channel())
            "audio.seek" -> seek(conversationId, params.channel(), params.seconds())
            "audio.getState" -> state(entries.getValue(params.channel()), conversationId)
            "audio.getPlaylist" -> playlist(entries.getValue(params.channel()), conversationId)
            "audio.setPlaylist" -> setPlaylist(environment, conversationId, params)
            "audio.appendPlaylist" -> appendPlaylist(environment, conversationId, params)
            "audio.getSettings" -> settings()
            "audio.setSettings" -> setSettings(params["settings"]?.jsonObject)
            else -> throw AuthorApiCallException(AuthorApiErrorCode.MethodNotFound, "没有找到创作能力：$method")
        }
    }

    private fun play(
        environment: AuthorApiEnvironment,
        conversationId: String,
        params: JsonObject,
    ): JsonObject {
        val channel = params.channel()
        val entry = entries.getValue(channel)
        entry.conversationId = conversationId
        entry.playlist = listOf(track(params["track"], "audio-${UUID.randomUUID()}"))
        entry.currentIndex = 0
        entry.loop = params.boolean("loop")
        entry.shuffle = false
        entry.volume = params.number("volume")?.coerceIn(0.0, 1.0) ?: 1.0
        entry.muted = false
        load(environment, entry, params.number("startAt")?.coerceAtLeast(0.0) ?: 0.0, autoplay = true)
        return state(entry, conversationId)
    }

    private fun pause(conversationId: String, channel: AuthorAudioChannel): JsonObject {
        val entry = entries.getValue(channel).claim(conversationId)
        runCatching { entry.player?.pause() }
        stopTimeUpdates(entry)
        if (entry.status !in setOf("idle", "ended")) entry.status = "paused"
        publish("audio.state.changed", entry)
        return state(entry, conversationId)
    }

    private fun resume(conversationId: String, channel: AuthorAudioChannel): JsonObject {
        val entry = entries.getValue(channel).claim(conversationId)
        if (entry.currentTrack() == null) {
            throw AuthorApiCallException("AUDIO_EMPTY", "当前音频频道没有可播放的曲目")
        }
        if (entry.status == "loading") {
            entry.autoplayWhenPrepared = true
        } else {
            try {
                entry.player?.start()
                entry.status = "playing"
                entry.error = ""
                startTimeUpdates(entry)
            } catch (error: Throwable) {
                entry.status = "error"
                entry.error = error.message ?: "音频播放失败"
                publish("audio.state.changed", entry)
                throw AuthorApiCallException("AUDIO_PLAY_FAILED", entry.error)
            }
        }
        publish("audio.state.changed", entry)
        return state(entry, conversationId)
    }

    private fun stop(conversationId: String, channel: AuthorAudioChannel): JsonObject {
        val entry = entries.getValue(channel).claim(conversationId)
        runCatching {
            entry.player?.pause()
            entry.player?.seekTo(0)
        }
        entry.status = "idle"
        entry.error = ""
        entry.autoplayWhenPrepared = false
        stopTimeUpdates(entry)
        publish("audio.state.changed", entry)
        return state(entry, conversationId)
    }

    private fun seek(
        conversationId: String,
        channel: AuthorAudioChannel,
        seconds: Double,
    ): JsonObject {
        val entry = entries.getValue(channel).claim(conversationId)
        val millis = (seconds * 1_000.0).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
        runCatching { entry.player?.seekTo(millis) }
            .getOrElse { throw AuthorApiCallException("AUDIO_SEEK_FAILED", it.message ?: "无法跳转播放位置") }
        publish("audio.time.updated", entry)
        return state(entry, conversationId)
    }

    private fun setPlaylist(
        environment: AuthorApiEnvironment,
        conversationId: String,
        params: JsonObject,
    ): JsonObject {
        val channel = params.channel()
        val entry = entries.getValue(channel).claim(conversationId)
        val items = params.trackList()
        entry.player.releaseSafely()
        stopTimeUpdates(entry)
        entry.player = null
        entry.playlist = items
        entry.currentIndex = if (items.isEmpty()) {
            -1
        } else {
            (params["currentIndex"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, items.lastIndex)
        }
        entry.loop = params.boolean("loop")
        entry.shuffle = params.boolean("shuffle")
        entry.error = ""
        if (items.isEmpty()) {
            entry.status = "idle"
        } else {
            load(environment, entry, startAtSeconds = 0.0, autoplay = params.boolean("autoplay"))
        }
        publish("audio.state.changed", entry)
        return state(entry, conversationId)
    }

    private fun appendPlaylist(
        environment: AuthorApiEnvironment,
        conversationId: String,
        params: JsonObject,
    ): JsonObject {
        val entry = entries.getValue(params.channel()).claim(conversationId)
        val wasEmpty = entry.playlist.isEmpty()
        entry.playlist = entry.playlist + params.trackList()
        if (wasEmpty && entry.playlist.isNotEmpty()) {
            entry.currentIndex = 0
            load(environment, entry, startAtSeconds = 0.0, autoplay = false)
        }
        publish("audio.state.changed", entry)
        return state(entry, conversationId)
    }

    private fun setSettings(value: JsonObject?): JsonObject {
        if (value == null) {
            throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "音频设置必须是对象")
        }
        value.number("masterVolume")?.let { masterVolume = it.coerceIn(0.0, 1.0) }
        value["muted"]?.jsonPrimitive?.let { masterMuted = it.content.toBooleanStrictOrNull() ?: masterMuted }
        value["channels"]?.jsonObject?.forEach { (wireName, json) ->
            val channel = AuthorAudioChannel.fromWireName(wireName) ?: return@forEach
            val update = json as? JsonObject ?: return@forEach
            val settings = channelSettings.getValue(channel)
            update.number("volume")?.let { settings.volume = it.coerceIn(0.0, 1.0) }
            update["muted"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { settings.muted = it }
        }
        entries.values.forEach { entry ->
            applyVolume(entry)
            publish("audio.state.changed", entry)
        }
        return settings()
    }

    private fun settings(): JsonObject = buildJsonObject {
        put("masterVolume", masterVolume)
        put("muted", masterMuted)
        put("channels", buildJsonObject {
            AuthorAudioChannel.entries.forEach { channel ->
                val setting = channelSettings.getValue(channel)
                put(channel.wireName, buildJsonObject {
                    put("volume", setting.volume)
                    put("muted", setting.muted)
                })
            }
        })
    }

    private fun load(
        environment: AuthorApiEnvironment,
        entry: AudioEntry,
        startAtSeconds: Double,
        autoplay: Boolean,
    ) {
        val selected = entry.currentTrack() ?: return
        entry.player.releaseSafely()
        entry.status = "loading"
        entry.error = ""
        entry.autoplayWhenPrepared = autoplay
        entry.pendingStartAtMillis = (startAtSeconds * 1_000.0)
            .coerceAtMost(Int.MAX_VALUE.toDouble())
            .toInt()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
        }
        entry.player = player
        player.setOnPreparedListener { prepared ->
            if (entry.pendingStartAtMillis > 0) prepared.seekTo(entry.pendingStartAtMillis)
            entry.pendingStartAtMillis = 0
            entry.status = if (entry.autoplayWhenPrepared) "playing" else "paused"
            if (entry.autoplayWhenPrepared) {
                runCatching { prepared.start() }.onFailure { error ->
                    entry.status = "error"
                    entry.error = error.message ?: "音频播放失败"
                }
            }
            if (entry.status == "playing") startTimeUpdates(entry)
            applyVolume(entry)
            publish("audio.state.changed", entry)
        }
        player.setOnCompletionListener { advance(environment, entry) }
        player.setOnErrorListener { _, what, extra ->
            stopTimeUpdates(entry)
            entry.status = "error"
            entry.error = "音频加载或播放失败（$what/$extra）"
            publish("audio.state.changed", entry)
            true
        }
        try {
            player.setDataSource(environment.resolveAudioSource(selected.url))
            applyVolume(entry)
            player.prepareAsync()
            publish("audio.state.changed", entry)
        } catch (error: Throwable) {
            player.releaseSafely()
            entry.player = null
            entry.status = "error"
            entry.error = error.message ?: "音频地址不可读取"
            publish("audio.state.changed", entry)
            throw AuthorApiCallException("AUDIO_LOAD_FAILED", entry.error)
        }
    }

    private fun advance(environment: AuthorApiEnvironment, entry: AudioEntry) {
        if (entry.playlist.isEmpty()) return
        entry.currentIndex = when {
            entry.shuffle && entry.playlist.size > 1 -> {
                generateSequence { entry.playlist.indices.random() }
                    .first { it != entry.currentIndex }
            }
            entry.currentIndex + 1 < entry.playlist.size -> entry.currentIndex + 1
            entry.loop -> 0
            else -> {
                stopTimeUpdates(entry)
                entry.status = "ended"
                publish("audio.state.changed", entry)
                return
            }
        }
        load(environment, entry, startAtSeconds = 0.0, autoplay = true)
    }

    private fun applyVolume(entry: AudioEntry) {
        val channel = channelSettings.getValue(entry.channel)
        val muted = masterMuted || channel.muted || entry.muted
        val volume = if (muted) 0f else (masterVolume * channel.volume * entry.volume).toFloat()
        runCatching { entry.player?.setVolume(volume, volume) }
    }

    private fun startTimeUpdates(entry: AudioEntry) {
        stopTimeUpdates(entry)
        val ticker = object : Runnable {
            override fun run() {
                if (entry.status != "playing") return
                publish("audio.time.updated", entry)
                mainHandler.postDelayed(this, 250L)
            }
        }
        entry.timeUpdateTicker = ticker
        mainHandler.postDelayed(ticker, 250L)
    }

    private fun stopTimeUpdates(entry: AudioEntry) {
        entry.timeUpdateTicker?.let(mainHandler::removeCallbacks)
        entry.timeUpdateTicker = null
    }

    private fun publish(name: String, entry: AudioEntry) {
        mutableEvents.tryEmit(
            AuthorApiEvent(
                name = name,
                payload = buildJsonObject {
                    put("conversationId", entry.conversationId)
                    put("state", state(entry, entry.conversationId))
                },
            ),
        )
    }

    private fun playlist(entry: AudioEntry, conversationId: String): JsonObject = buildJsonObject {
        put("channel", entry.channel.wireName)
        put("items", buildJsonArray { entry.playlist.forEach { add(it.toJson()) } })
        put("currentIndex", entry.currentIndex)
        if (entry.conversationId.isBlank()) entry.conversationId = conversationId
    }

    private fun state(entry: AudioEntry, conversationId: String): JsonObject {
        if (entry.conversationId.isBlank()) entry.conversationId = conversationId
        val durationMillis = runCatching { entry.player?.duration ?: -1 }.getOrDefault(-1)
        val currentMillis = runCatching { entry.player?.currentPosition ?: 0 }.getOrDefault(0)
        return buildJsonObject {
            put("conversationId", entry.conversationId)
            put("channel", entry.channel.wireName)
            put("status", entry.status)
            put("playlist", buildJsonArray { entry.playlist.forEach { add(it.toJson()) } })
            put("currentIndex", entry.currentIndex)
            put("currentTrack", entry.currentTrack()?.toJson() ?: JsonNull)
            put("currentTime", currentMillis.coerceAtLeast(0) / 1_000.0)
            put("duration", if (durationMillis >= 0) JsonPrimitive(durationMillis / 1_000.0) else JsonNull)
            put("loop", entry.loop)
            put("shuffle", entry.shuffle)
            put("volume", entry.volume)
            put("muted", entry.muted)
            put("error", entry.error)
        }
    }
}

private enum class AuthorAudioChannel(val wireName: String) {
    Bgm("bgm"),
    Ambient("ambient"),
    Voice("voice"),
    Sfx("sfx"),
    ;

    companion object {
        fun fromWireName(value: String): AuthorAudioChannel? = entries.firstOrNull { it.wireName == value }
    }
}

private data class AudioTrack(
    val id: String,
    val url: String,
    val name: String,
    val mimeType: String,
    val metadata: JsonObject,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("url", url)
        put("name", name)
        put("mimeType", mimeType)
        put("metadata", metadata)
    }
}

private data class AudioChannelSettings(
    var volume: Double = 1.0,
    var muted: Boolean = false,
)

private data class AudioEntry(
    val channel: AuthorAudioChannel,
    var conversationId: String = "",
    var player: MediaPlayer? = null,
    var playlist: List<AudioTrack> = emptyList(),
    var currentIndex: Int = -1,
    var status: String = "idle",
    var loop: Boolean = false,
    var shuffle: Boolean = false,
    var volume: Double = 1.0,
    var muted: Boolean = false,
    var error: String = "",
    var autoplayWhenPrepared: Boolean = false,
    var pendingStartAtMillis: Int = 0,
    var timeUpdateTicker: Runnable? = null,
) {
    fun claim(value: String): AudioEntry = apply { conversationId = value }
    fun currentTrack(): AudioTrack? = playlist.getOrNull(currentIndex)
}

private fun JsonObject.channel(): AuthorAudioChannel {
    val value = this["channel"]?.jsonPrimitive?.contentOrNull ?: "bgm"
    return AuthorAudioChannel.fromWireName(value)
        ?: throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "未知的音频频道：$value")
}

private fun JsonObject.seconds(): Double {
    val value = number("seconds")
    if (value == null || value < 0.0) {
        throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "播放位置必须是非负秒数")
    }
    return value
}

private fun JsonObject.number(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.boolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun JsonObject.trackList(): List<AudioTrack> {
    val items = this["items"] as? JsonArray
        ?: throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "播放列表必须是曲目数组")
    if (items.size > 1_000) {
        throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "播放列表不能超过 1000 项")
    }
    return items.mapIndexed { index, item -> track(item, "audio-${UUID.randomUUID()}-$index") }
}

private fun track(value: JsonElement?, fallbackId: String): AudioTrack {
    val source = when (value) {
        is JsonPrimitive -> buildJsonObject { put("url", value.content) }
        is JsonObject -> value
        else -> null
    } ?: throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "音频曲目必须提供可读取的 url")
    val url = source["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (url.isBlank()) {
        throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "音频曲目必须提供可读取的 url")
    }
    val id = source["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { fallbackId }
    val name = source["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        .ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "音频" } }
    return AudioTrack(
        id = id,
        url = url,
        name = name,
        mimeType = source["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        metadata = source["metadata"] as? JsonObject ?: buildJsonObject {},
    )
}

private fun AuthorApiEnvironment.conversationId(): String =
    runtime.chatGateway?.snapshot()?.draft?.session?.id
        ?: runtime.currentMessage?.conversationId
        ?: ""

private fun AuthorApiEnvironment.resolveAudioSource(url: String): String {
    if (url.startsWith("/eleckoi-runtime/author-media/")) {
        val source = runtime.chatGateway?.snapshot()?.draft?.session?.messages
            ?.asSequence()
            ?.flatMap { it.attachments.asSequence() }
            ?.firstOrNull { it.url == url }
            ?.sourcePath
            .orEmpty()
        if (source.isNotBlank()) return source
    }
    if (!url.startsWith("data:")) return url
    val comma = url.indexOf(',')
    if (comma <= 5 || !url.substring(0, comma).contains(";base64")) {
        throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "音频 data URL 必须使用 base64 编码")
    }
    val bytes = runCatching { Base64.decode(url.substring(comma + 1), Base64.DEFAULT) }
        .getOrElse { throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "音频 data URL 编码无效") }
    if (bytes.size > 32 * 1024 * 1024) {
        throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "单个音频不能超过 32 MiB")
    }
    val directory = File(appContext.cacheDir, "author-audio").apply { mkdirs() }
    return File(directory, "${url.hashCode().toUInt().toString(16)}.audio").apply {
        if (!isFile || length() != bytes.size.toLong()) writeBytes(bytes)
    }.absolutePath
}

private fun MediaPlayer?.releaseSafely() {
    runCatching { this?.release() }
}
