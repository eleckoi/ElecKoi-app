package com.eleckoi.android.feature.characters.presets.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetExportFormat
import com.eleckoi.android.feature.characters.presets.model.ExportedAgentPresetFile
import com.eleckoi.android.feature.characters.transfer.format.png.PngTextChunkCodec
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class AgentPresetPngPayload(
    val json: String,
)

/** One normal PNG whose text chunks carry the lossless ElecKoi preset payload. */
internal object AgentPresetPngFormat {
    private const val PresetKeyword = "eleckoi_agent_preset"

    fun isPng(bytes: ByteArray): Boolean = PngTextChunkCodec.isPng(bytes)

    fun encode(image: ByteArray, json: String): ByteArray = PngTextChunkCodec.writeTextOnly(
        image,
        mapOf(
            PresetKeyword to Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8)),
        )
    )

    fun decode(image: ByteArray): AgentPresetPngPayload {
        require(isPng(image)) { "这不是 PNG 预设卡" }
        val text = PngTextChunkCodec.readText(image)
        val encodedPreset = text[PresetKeyword] ?: error("图片里没有 ElecKoi 预设数据")
        val json = runCatching {
            Base64.getDecoder().decode(encodedPreset).toString(StandardCharsets.UTF_8)
        }.getOrElse { error ->
            throw IllegalArgumentException("预设卡数据已损坏", error)
        }
        return AgentPresetPngPayload(json = json)
    }
}

internal object AgentPresetCardExporter {
    fun export(
        preset: AgentPreset,
        format: AgentPresetExportFormat,
        authorAvatar: AgentPresetTransferAvatar?,
        versions: List<AgentPresetTransferVersion>,
    ): ExportedAgentPresetFile {
        val json = AgentPresetImportCodec.encodeElecKoi(preset, authorAvatar, versions)
        val bytes = when (format) {
            AgentPresetExportFormat.Json -> json.toByteArray(StandardCharsets.UTF_8)
            AgentPresetExportFormat.Png -> {
                val cover = authorAvatar
                    ?.takeIf { it.mediaType == "image/png" && AgentPresetPngFormat.isPng(it.bytes) }
                    ?.bytes
                    ?: AgentPresetCardMedia.render(
                        preset.copy(profile = preset.profile.copy(authorAvatarPath = "")),
                    )
                AgentPresetPngFormat.encode(cover, json)
            }
        }
        return ExportedAgentPresetFile(
            presetId = preset.id,
            name = preset.name,
            format = format,
            bytes = bytes,
        )
    }
}

/** Deterministic 3:4 sharing card: preset identity first, author identity second. */
private object AgentPresetCardMedia {
    private const val Width = 1200
    private const val Height = 1600

    private data class Palette(val start: Int, val end: Int, val accent: Int)

    private val palettes = listOf(
        Palette(Color.rgb(232, 241, 255), Color.rgb(224, 216, 255), Color.rgb(56, 115, 235)),
        Palette(Color.rgb(237, 248, 243), Color.rgb(207, 235, 225), Color.rgb(34, 139, 104)),
        Palette(Color.rgb(255, 242, 232), Color.rgb(250, 220, 224), Color.rgb(210, 90, 92)),
        Palette(Color.rgb(244, 235, 255), Color.rgb(220, 228, 255), Color.rgb(118, 78, 210)),
    )

    fun render(preset: AgentPreset): ByteArray {
        val palette = palettes[(preset.name.hashCode() and Int.MAX_VALUE) % palettes.size]
        val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        try {
            paint.shader = LinearGradient(
                0f,
                0f,
                Width.toFloat(),
                Height.toFloat(),
                palette.start,
                palette.end,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, Width.toFloat(), Height.toFloat(), paint)
            paint.shader = null

            paint.color = Color.argb(34, 255, 255, 255)
            canvas.drawCircle(1050f, 150f, 330f, paint)
            canvas.drawCircle(160f, 1480f, 390f, paint)
            paint.color = Color.argb(112, 255, 255, 255)
            canvas.drawRoundRect(RectF(62f, 62f, 1138f, 1538f), 58f, 58f, paint)

            drawHeader(canvas, preset, palette, paint)
            drawTitle(canvas, preset.name, paint)
            drawTags(canvas, preset, palette, paint)
            drawAuthor(canvas, preset, palette, paint)

            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawHeader(canvas: Canvas, preset: AgentPreset, palette: Palette, paint: Paint) {
        paint.color = palette.accent
        paint.textSize = 30f
        paint.letterSpacing = 0.12f
        paint.isFakeBoldText = true
        canvas.drawText("ELECKOI  ·  STORY PRESET", 104f, 158f, paint)
        paint.letterSpacing = 0f

        val countText = buildString {
            append("${preset.entries.size} 条设定")
            if (preset.regexRules.isNotEmpty()) append(" · ${preset.regexRules.size} 条正则")
        }
        paint.textSize = 29f
        paint.isFakeBoldText = false
        paint.color = Color.argb(150, 31, 42, 58)
        canvas.drawText(countText, 1096f - paint.measureText(countText), 158f, paint)
    }

    private fun drawTitle(canvas: Canvas, title: String, paint: Paint) {
        val text = title.trim().ifBlank { "未命名预设" }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 32, 45)
            textSize = 94f
            isFakeBoldText = true
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, titlePaint, 992)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(10f, 1f)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(104f, 306f)
        layout.draw(canvas)
        canvas.restore()

        paint.color = Color.argb(45, 31, 42, 58)
        canvas.drawRoundRect(RectF(104f, 745f, 1096f, 748f), 2f, 2f, paint)
    }

    private fun drawTags(canvas: Canvas, preset: AgentPreset, palette: Palette, paint: Paint) {
        val tags = preset.modelTags.map { it.label }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
        paint.textSize = 34f
        paint.isFakeBoldText = false
        var x = 104f
        var y = 820f
        var row = 0
        tags.forEach { raw ->
            if (row >= 2) return@forEach
            val label = raw.take(20)
            val chipWidth = (paint.measureText(label) + 58f).coerceAtMost(440f)
            if (x + chipWidth > 1096f) {
                row += 1
                if (row >= 2) return@forEach
                x = 104f
                y += 86f
            }
            paint.color = Color.argb(24, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
            canvas.drawRoundRect(RectF(x, y, x + chipWidth, y + 64f), 32f, 32f, paint)
            paint.color = palette.accent
            canvas.drawText(label, x + 29f, y + 43f, paint)
            x += chipWidth + 18f
        }
    }

    private fun drawAuthor(canvas: Canvas, preset: AgentPreset, palette: Palette, paint: Paint) {
        val authorName = preset.profile.authorName.trim().ifBlank { "未署名" }
        val avatarRect = RectF(104f, 1180f, 294f, 1370f)
        val avatar = preset.profile.authorAvatarPath
            .takeIf(String::isNotBlank)
            ?.let(BitmapFactory::decodeFile)
        if (avatar != null) {
            try {
                val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val scale = maxOf(avatarRect.width() / avatar.width, avatarRect.height() / avatar.height)
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(
                        avatarRect.centerX() - avatar.width * scale / 2f,
                        avatarRect.centerY() - avatar.height * scale / 2f,
                    )
                }
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), avatarRect.width() / 2f, paint)
                paint.shader = null
            } finally {
                avatar.recycle()
            }
        } else {
            paint.color = palette.accent
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), avatarRect.width() / 2f, paint)
            paint.color = Color.WHITE
            paint.textSize = 70f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            val initial = authorName.codePoints().limit(1).toArray().firstOrNull()?.let(Character::toChars)
                ?.concatToString().orEmpty().ifBlank { "E" }
            val baseline = avatarRect.centerY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(initial, avatarRect.centerX(), baseline, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        paint.color = Color.argb(145, 31, 42, 58)
        paint.textSize = 30f
        paint.isFakeBoldText = false
        canvas.drawText("作者", 340f, 1240f, paint)
        paint.color = Color.rgb(26, 32, 45)
        paint.textSize = 54f
        paint.isFakeBoldText = true
        canvas.drawText(ellipsize(authorName, paint, 700f), 340f, 1318f, paint)

        paint.color = Color.argb(120, 31, 42, 58)
        paint.textSize = 28f
        paint.isFakeBoldText = false
        val footer = "可导入的 ElecKoi 预设卡"
        canvas.drawText(footer, 1096f - paint.measureText(footer), 1464f, paint)
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        val suffix = "…"
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end -= 1
        return value.substring(0, end) + suffix
    }
}
