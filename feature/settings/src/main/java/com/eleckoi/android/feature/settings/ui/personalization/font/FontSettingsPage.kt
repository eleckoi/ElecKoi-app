package com.eleckoi.android.feature.settings.ui.personalization.font

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.QuietBackButton
import com.eleckoi.android.feature.appfont.data.AppFontCatalog
import com.eleckoi.android.feature.appfont.data.AppFontDownloader
import com.eleckoi.android.feature.appfont.data.AppFontRepository
import com.eleckoi.android.feature.appfont.data.AppFontScope
import com.eleckoi.android.feature.appfont.data.AppFontSelection
import kotlinx.coroutines.launch

private const val FontPreviewSample = "她安静地站在原地，等你伸手环住她。“路上辛苦了。”"

@Composable
fun FontSettingsPage(
    appearance: AppearanceTheme,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { AppFontRepository(context) }
    val selection by repository.selectionFlow.collectAsState(initial = AppFontSelection())
    val scope = rememberCoroutineScope()

    // Bumped after any file lands or leaves, because installed-ness is answered by the filesystem
    // rather than by a flow — nothing else would tell the list to recompose.
    // Download state lives in a process-scoped object, so leaving this page and coming back shows
    // the transfer still running instead of restarting it.
    val downloadingId by AppFontDownloader.activeFontId.collectAsState()
    val downloadProgress by AppFontDownloader.progress.collectAsState()
    val downloaderMessage by AppFontDownloader.message.collectAsState()
    val completions by AppFontDownloader.completions.collectAsState()

    var importRevision by remember { mutableStateOf(0) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val installRevision = completions + importRevision
    val message = importMessage ?: downloaderMessage

    LaunchedEffect(Unit) { AppFontDownloader.sweepAbandoned(repository) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = queryDisplayName(context, uri)
            repository.importFrom(uri, name)
                .onSuccess { fontId ->
                    importRevision += 1
                    repository.selectFont(fontId)
                    AppFontDownloader.consumeMessage()
                    importMessage = "已导入 $fontId"
                }
                .onFailure { error -> importMessage = error.message ?: "导入失败" }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = appearance.mobileBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appearance.mobileBg)
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietBackButton(
                    color = appearance.mobileText.copy(alpha = 0.84f),
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "字体",
                    modifier = Modifier.padding(start = 4.dp),
                    color = appearance.mobileText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            // The font names below are each drawn in their own typeface, but a name is four
            // characters — not enough to judge a paragraph. This shows the selected font on the kind
            // of sentence the app actually renders.
            val activeFamily = rememberFontFamily(repository.typefaceFor(selection.fontId), installRevision)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface)
                    .padding(14.dp),
            ) {
                Text("预览", color = appearance.mobileMuted, fontSize = 11.sp)
                Text(
                    FontPreviewSample,
                    color = appearance.mobileText,
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    fontFamily = activeFamily,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            FontSectionLabel("默认", appearance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface),
            ) {
                val defaultEntry = AppFontCatalog.entryFor(AppFontCatalog.DefaultFontId)!!
                FontRow(
                    name = defaultEntry.name,
                    note = "${defaultEntry.note} · 应用内置",
                    selected = selection.fontId == defaultEntry.id,
                    appearance = appearance,
                    nameFontFamily = rememberFontFamily(
                        repository.typefaceFor(defaultEntry.id),
                        installRevision,
                    ),
                    onClick = { scope.launch { repository.selectFont(defaultEntry.id) } },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 14.dp),
                    color = appearance.mobileLine,
                )
                FontRow(
                    name = "系统默认",
                    note = "跟随 Android 与手机厂商设置",
                    selected = selection.fontId.isBlank(),
                    appearance = appearance,
                    onClick = { scope.launch { repository.selectFont(AppFontCatalog.SystemFontId) } },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            FontSectionLabel("可下载", appearance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface),
            ) {
                AppFontCatalog.downloadableEntries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            color = appearance.mobileLine,
                        )
                    }
                    val installed = remember(entry.id, installRevision) { repository.isInstalled(entry.id) }
                    val family = rememberFontFamily(
                        typeface = if (installed) repository.typefaceFor(entry.id) else null,
                        revision = installRevision,
                    )
                    val isDownloading = downloadingId == entry.id
                    FontRow(
                        name = entry.name,
                        note = when {
                            isDownloading -> "正在下载 ${(downloadProgress * 100).toInt()}%"
                            installed -> "${entry.note} · 已下载"
                            else -> "${entry.note} · ${formatSize(entry.sizeBytes)}"
                        },
                        selected = selection.fontId == entry.id,
                        appearance = appearance,
                        nameFontFamily = family,
                        downloading = isDownloading,
                        trailingIcon = if (installed) null else PhosphorRegular.DownloadSimple,
                        onClick = {
                            when {
                                installed -> scope.launch { repository.selectFont(entry.id) }
                                downloadingId != null -> Unit
                                else -> {
                                    importMessage = null
                                    AppFontDownloader.start(repository, entry)
                                }
                            }
                        },
                    )
                }
            }

            val imported = remember(installRevision) { repository.importedFonts() }
            Spacer(modifier = Modifier.height(24.dp))
            FontSectionLabel("我的字体", appearance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface),
            ) {
                imported.forEach { fontId ->
                    val family = rememberFontFamily(repository.typefaceFor(fontId), installRevision)
                    FontRow(
                        name = fontId,
                        note = null,
                        selected = selection.fontId == fontId,
                        appearance = appearance,
                        nameFontFamily = family,
                        onClick = { scope.launch { repository.selectFont(fontId) } },
                        onRemove = {
                            scope.launch {
                                repository.remove(fontId)
                                importRevision += 1
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 14.dp),
                        color = appearance.mobileLine,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { importLauncher.launch("*/*") }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledSvgIcon(
                        paths = listOf(PhosphorRegular.Plus),
                        color = appearance.mobileText,
                        iconSize = 16.dp,
                        viewportSize = 256f,
                    )
                    Text(
                        "导入字体文件",
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            FontSectionLabel("应用范围", appearance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface),
            ) {
                FontRow(
                    name = "整个应用",
                    note = null,
                    selected = selection.scope == AppFontScope.All,
                    appearance = appearance,
                    onClick = { scope.launch { repository.selectScope(AppFontScope.All) } },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 14.dp),
                    color = appearance.mobileLine,
                )
                FontRow(
                    name = "仅聊天正文",
                    note = "界面骨架保持系统字体",
                    selected = selection.scope == AppFontScope.ChatOnly,
                    appearance = appearance,
                    onClick = { scope.launch { repository.selectScope(AppFontScope.ChatOnly) } },
                )
            }

            message?.let { text ->
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text,
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberFontFamily(typeface: android.graphics.Typeface?, revision: Int): FontFamily? =
    remember(typeface, revision) {
        typeface ?: return@remember null
        FontFamily(typeface)
    }

@Composable
private fun FontSectionLabel(text: String, appearance: AppearanceTheme) {
    Text(
        text,
        color = appearance.mobileText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun FontRow(
    name: String,
    note: String?,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    nameFontFamily: FontFamily? = null,
    downloading: Boolean = false,
    trailingIcon: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = appearance.mobileText,
                fontSize = if (nameFontFamily != null) 16.sp else 15.sp,
                fontFamily = nameFontFamily,
            )
            if (note != null) {
                Text(
                    note,
                    color = appearance.mobileSoft,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onRemove != null && !selected) {
            Text(
                "删除",
                color = ElecKoiDanger,
                fontSize = 12.sp,
                modifier = Modifier
                    .noRippleClickable(onClick = onRemove)
                    .padding(horizontal = 10.dp),
            )
        }
        when {
            downloading -> CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = appearance.mobileText,
            )
            selected -> SettingsSelectionCheck(selected = true, appearance = appearance)
            trailingIcon != null -> FilledSvgIcon(
                paths = listOf(trailingIcon),
                color = appearance.mobileMuted,
                iconSize = 18.dp,
                viewportSize = 256f,
            )
            else -> SettingsSelectionCheck(selected = false, appearance = appearance)
        }
    }
}

private fun formatSize(bytes: Long): String = "${(bytes / 1_048_576.0).let { "%.0f".format(it) }} MB"

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "imported.ttf"
}
