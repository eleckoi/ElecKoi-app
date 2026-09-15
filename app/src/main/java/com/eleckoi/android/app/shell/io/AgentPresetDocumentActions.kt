package com.eleckoi.android.app.shell

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.eleckoi.android.feature.characters.presets.model.AgentPresetExportFormat
import com.eleckoi.android.feature.characters.presets.model.ExportedAgentPresetFile
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportDocument
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AgentPresetDocumentActions(
    val importElecKoiPresets: () -> Unit,
    val importSillyTavernPresets: () -> Unit,
    val saveCard: (ExportedAgentPresetFile) -> Unit,
    val saveCards: (List<ExportedAgentPresetFile>) -> Unit,
    val shareOriginal: (ExportedAgentPresetFile) -> Unit,
    val shareOriginals: (List<ExportedAgentPresetFile>) -> Unit,
)

@Composable
internal fun rememberAgentPresetDocumentActions(
    onDocumentsSelected: (List<AgentPresetImportDocument>, AgentPresetImportSource) -> Unit,
): AgentPresetDocumentActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnDocumentsSelected by rememberUpdatedState(onDocumentsSelected)
    var pendingSource by remember { mutableStateOf(AgentPresetImportSource.ElecKoi) }
    var pendingSave by remember { mutableStateOf<ExportedAgentPresetFile?>(null) }
    var pendingBatchSave by remember { mutableStateOf<List<ExportedAgentPresetFile>>(emptyList()) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                require(uris.size <= MaxPresetImportCount) { "一次最多导入 $MaxPresetImportCount 个预设" }
                val documents = uris.mapIndexed { index, uri ->
                    val fallbackExtension = if (pendingSource == AgentPresetImportSource.ElecKoi) "png" else "json"
                    val fileName = context.displayName(uri).ifBlank { "预设-${index + 1}.$fallbackExtension" }
                    if (pendingSource == AgentPresetImportSource.ElecKoi) {
                        AgentPresetImportDocument(
                            fileName = fileName,
                            bytes = context.readPresetBytes(uri, MaxPresetFileBytes, "单个预设不能超过 64 MB"),
                        )
                    } else {
                        AgentPresetImportDocument(fileName = fileName, json = context.readPresetJson(uri))
                    }
                }
                currentOnDocumentsSelected(documents, pendingSource)
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        error.message ?: "读取预设失败",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val card = pendingSave
        pendingSave = null
        if (uri == null || card == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(card.bytes) }
                    ?: error("无法保存预设")
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error.message ?: "保存预设失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val saveJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val file = pendingSave
        pendingSave = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
                    ?: error("无法保存预设")
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error.message ?: "保存预设失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val saveFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val cards = pendingBatchSave
        pendingBatchSave = emptyList()
        if (treeUri == null || cards.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val directoryUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val failures = cards.mapNotNull { card ->
                runCatching {
                    val outputUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        directoryUri,
                        card.format.mediaType,
                        "${safePresetName(card.name)}.${card.format.extension}",
                    ) ?: error("无法创建 ${card.name}.${card.format.extension}")
                    context.contentResolver.openOutputStream(outputUri)?.use { it.write(card.bytes) }
                        ?: error("无法写入 ${card.name}.${card.format.extension}")
                }.exceptionOrNull()
            }
            withContext(Dispatchers.Main) {
                val savedCount = cards.size - failures.size
                val message = when {
                    failures.isEmpty() -> "已保存 ${cards.size} 个预设文件"
                    savedCount > 0 -> "已保存 $savedCount 个，${failures.size} 个失败"
                    else -> failures.firstOrNull()?.message ?: "保存预设失败"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    return remember(importLauncher, savePngLauncher, saveJsonLauncher, saveFolderLauncher, context) {
        AgentPresetDocumentActions(
            importElecKoiPresets = {
                pendingSource = AgentPresetImportSource.ElecKoi
                importLauncher.launch(ElecKoiPresetMimeTypes)
            },
            importSillyTavernPresets = {
                pendingSource = AgentPresetImportSource.SillyTavern
                importLauncher.launch(PresetJsonMimeTypes)
            },
            saveCard = { card ->
                pendingSave = card
                val fileName = "${safePresetName(card.name)}.${card.format.extension}"
                when (card.format) {
                    AgentPresetExportFormat.Png -> savePngLauncher.launch(fileName)
                    AgentPresetExportFormat.Json -> saveJsonLauncher.launch(fileName)
                }
            },
            saveCards = { cards ->
                if (cards.isNotEmpty()) {
                    pendingBatchSave = cards
                    saveFolderLauncher.launch(null)
                }
            },
            shareOriginal = { card -> sharePresetCards(context, listOf(card)) },
            shareOriginals = { cards -> sharePresetCards(context, cards) },
        )
    }
}

private fun android.content.Context.displayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()
}.getOrDefault("")

private fun android.content.Context.readPresetJson(uri: Uri): String {
    return readPresetBytes(uri, MaxPresetJsonBytes, "单个预设不能超过 8 MB")
        .toString(StandardCharsets.UTF_8)
}

private fun android.content.Context.readPresetBytes(
    uri: Uri,
    maxBytes: Long,
    tooLargeMessage: String,
): ByteArray {
    val output = ByteArrayOutputStream()
    contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { tooLargeMessage }
            output.write(buffer, 0, count)
        }
    } ?: error("无法读取预设文件")
    return output.toByteArray()
}

private fun sharePresetCards(
    context: android.content.Context,
    cards: List<ExportedAgentPresetFile>,
) {
    if (cards.isEmpty()) return
    val directory = File(
        context.cacheDir,
        "preset_transfer/exports/${System.currentTimeMillis()}",
    ).apply { mkdirs() }
    val uris = ArrayList(cards.mapIndexed { index, card ->
        val file = File(
            directory,
            "${index + 1}-${safePresetName(card.name)}.${card.format.extension}",
        ).apply { writeBytes(card.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    })
    val sharedClipData = ClipData.newRawUri(cards.first().name, uris.first()).apply {
        uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
    }
    val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        // PNG uses an opaque file MIME so social apps do not recompress it and strip metadata.
        type = if (cards.first().format == AgentPresetExportFormat.Png) {
            "application/octet-stream"
        } else {
            "application/json"
        }
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        clipData = sharedClipData
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val title = if (cards.size == 1) "分享预设" else "分享 ${cards.size} 个预设"
    context.startActivity(Intent.createChooser(send, title))
}

private fun safePresetName(name: String): String = name
    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .trim()
    .ifBlank { "预设" }

private val PresetJsonMimeTypes = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "application/octet-stream",
)
private val ElecKoiPresetMimeTypes = arrayOf(
    "image/png",
    *PresetJsonMimeTypes,
)
private const val MaxPresetJsonBytes = 8L * 1024 * 1024
private const val MaxPresetFileBytes = 64L * 1024 * 1024
private const val MaxPresetImportCount = 20
