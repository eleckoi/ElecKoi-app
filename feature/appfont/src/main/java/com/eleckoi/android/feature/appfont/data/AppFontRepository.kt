package com.eleckoi.android.feature.appfont.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private val Context.appFontDataStore by preferencesDataStore(name = "app_font")

enum class AppFontScope {
    All,
    ChatOnly,
    ;

    companion object {
        fun fromKey(key: String?): AppFontScope = if (key == ChatOnly.name) ChatOnly else All
    }
}

data class AppFontSelection(
    val fontId: String = AppFontCatalog.DefaultFontId,
    val scope: AppFontScope = AppFontScope.All,
)

internal fun resolveSelectedFontId(storedFontId: String?): String =
    storedFontId ?: AppFontCatalog.DefaultFontId

class AppFontRepository(private val context: Context) {

    private val client by lazy { OkHttpClient() }

    private val downloadedDirectory: File
        get() = File(context.filesDir, "fonts/downloaded").apply { mkdirs() }

    private val importedDirectory: File
        get() = File(context.filesDir, "fonts/imported").apply { mkdirs() }

    val selectionFlow: Flow<AppFontSelection> = context.appFontDataStore.data.map { preferences ->
        AppFontSelection(
            // A missing key means the user has never chosen a font and receives the product
            // default. An explicitly stored empty string means they deliberately chose Android's
            // system font, so it must survive upgrades and restores.
            fontId = resolveSelectedFontId(preferences[SelectedFontId]),
            scope = AppFontScope.fromKey(preferences[SelectedScope]),
        )
    }

    /** Portable selection only; the selected font file is copied by the app-level backup service. */
    suspend fun exportSelectionJson(): String {
        val selection = selectionFlow.first()
        return JSONObject()
            .put("format", "eleckoi.app-font")
            .put("version", 1)
            .put("font_id", selection.fontId)
            .put("scope", selection.scope.name)
            .toString(2)
    }

    suspend fun restoreSelectionJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "eleckoi.app-font") {
            "字体偏好格式不正确"
        }
        require(root.optInt("version", -1) == 1) { "不支持的字体偏好版本" }
        val fontId = root.optString("font_id")
        val scope = AppFontScope.fromKey(root.optString("scope"))
        context.appFontDataStore.edit { preferences ->
            preferences[SelectedFontId] = fontId
            preferences[SelectedScope] = scope.name
        }
    }

    suspend fun selectFont(fontId: String) {
        context.appFontDataStore.edit { it[SelectedFontId] = fontId }
    }

    suspend fun selectScope(scope: AppFontScope) {
        context.appFontDataStore.edit { it[SelectedScope] = scope.name }
    }

    // A downloaded catalog font and an imported file are looked up the same way, so the UI never
    // has to care which kind a selection is.
    fun fileFor(fontId: String): File? {
        if (fontId.isBlank()) return null
        AppFontCatalog.entryFor(fontId)?.let { entry ->
            if (entry.bundledAssetPath != null) return null
            return File(downloadedDirectory, entry.fileName).takeIf(File::exists)
        }
        return File(importedDirectory, fontId).takeIf(File::exists)
    }

    fun typefaceFor(fontId: String): Typeface? {
        if (fontId.isBlank()) return null
        AppFontCatalog.entryFor(fontId)?.bundledAssetPath?.let { assetPath ->
            BundledTypefaces[assetPath]?.let { return it }
            val loaded = runCatching { Typeface.createFromAsset(context.assets, assetPath) }.getOrNull()
                ?: return null
            return BundledTypefaces.putIfAbsent(assetPath, loaded) ?: loaded
        }
        return fileFor(fontId)?.let { file ->
            runCatching { Typeface.createFromFile(file) }.getOrNull()
        }
    }

    fun isInstalled(fontId: String): Boolean =
        AppFontCatalog.entryFor(fontId)?.bundledAssetPath != null || fileFor(fontId) != null

    fun importedFonts(): List<String> = importedDirectory
        .listFiles()
        ?.filter(File::isFile)
        ?.map(File::getName)
        ?.sorted()
        .orEmpty()

    // A 15-25 MB download over mobile data needs a real progress number; an indeterminate spinner
    // is indistinguishable from a stall.
    suspend fun download(
        entry: AppFontCatalogEntry,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(downloadedDirectory, entry.fileName)
            // Download to a sibling first: a half-written file at the real path would look installed
            // and then crash text layout the moment it is selected.
            val staging = File(downloadedDirectory, "${entry.fileName}.part")
            val request = Request.Builder().url(entry.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("响应为空")
                val expected = body.contentLength().takeIf { it > 0L } ?: entry.sizeBytes
                staging.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress((written.toFloat() / expected).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!isUsableFont(staging)) {
                staging.delete()
                throw IOException("文件损坏，无法作为字体加载")
            }
            if (!staging.renameTo(target)) {
                staging.delete()
                throw IOException("无法保存到本地")
            }
            Unit
        }.onFailure { staging(entry).delete() }
    }

    private fun staging(entry: AppFontCatalogEntry) = File(downloadedDirectory, "${entry.fileName}.part")

    // Leaving the page cancels the download coroutine, which leaves a partial file behind. Clearing
    // them on entry keeps the directory from filling up with abandoned halves.
    fun clearAbandonedDownloads() {
        downloadedDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        importedDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    suspend fun importFrom(uri: Uri, displayName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitizeFileName(displayName)
            // The picker hands back a temporary permission, so the bytes have to be copied in now;
            // holding the Uri would break the font on the next launch.
            val target = File(importedDirectory, safeName)
            val staging = File(importedDirectory, "$safeName.part")
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")
            if (!isUsableFont(staging)) {
                staging.delete()
                throw IOException("这不是可用的字体文件")
            }
            if (!hasChineseGlyphs(staging)) {
                staging.delete()
                throw IOException("这个字体没有中文，界面会变成方块")
            }
            staging.renameTo(target)
            safeName
        }
    }

    suspend fun remove(fontId: String) = withContext(Dispatchers.IO) {
        fileFor(fontId)?.delete()
        if (selectionFlow.first().fontId == fontId) {
            selectFont(AppFontCatalog.SystemFontId)
        }
        Unit
    }

    private fun isUsableFont(file: File): Boolean = runCatching {
        Typeface.createFromFile(file) != Typeface.DEFAULT
    }.getOrDefault(false)

    // A font with no CJK coverage renders the entire interface as tofu boxes, and the failure only
    // shows up after it is applied. Cheaper to refuse it at import.
    private fun hasChineseGlyphs(file: File): Boolean = runCatching {
        val paint = Paint().apply { typeface = Typeface.createFromFile(file) }
        paint.hasGlyph("字") && paint.hasGlyph("的")
    }.getOrDefault(false)

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fff._-]"), "_")
        val withExtension = if (cleaned.lowercase().endsWith(".ttf") || cleaned.lowercase().endsWith(".otf")) {
            cleaned
        } else {
            "$cleaned.ttf"
        }
        return withExtension.ifBlank { "imported.ttf" }
    }

    private companion object {
        val SelectedFontId = stringPreferencesKey("selected_font_id")
        val SelectedScope = stringPreferencesKey("selected_font_scope")
        val BundledTypefaces = ConcurrentHashMap<String, Typeface>()
    }
}
