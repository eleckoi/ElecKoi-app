package com.eleckoi.android.feature.chat.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.sdk.author.*
import com.eleckoi.android.sdk.author.plugins.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.zip.ZipInputStream

/** One native mount point for plugin-owned HTML. The script runtime remains alive after closing. */
@Composable
fun PluginUiHost(gateway: AuthorChatGateway) {
    PluginFileSaveHost()
    val managerOpen by PluginUiRegistry.managerOpen.collectAsState()
    if (managerOpen) PluginManagerDialog(gateway) { PluginUiRegistry.managerOpen.value = false }
    val opened by PluginUiRegistry.opened.collectAsState()
    val descriptor = opened ?: return
    val owner = descriptor["pluginId"]?.jsonPrimitive?.content ?: "frontend"
    var installedPanel by remember(descriptor) { mutableStateOf<AuthorPluginPanelWebView?>(null) }
    var panelError by remember(descriptor) { mutableStateOf("") }
    DisposableEffect(descriptor) {
        onDispose { installedPanel?.close() }
    }
    Dialog(onDismissRequest = { PluginUiRegistry.opened.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(descriptor["label"]?.jsonPrimitive?.content ?: descriptor.getValue("id").jsonPrimitive.content, modifier = Modifier.padding(16.dp))
                    TextButton(onClick = { PluginUiRegistry.opened.value = null }) { Text("关闭") }
                }
                if (panelError.isNotBlank()) Text(panelError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                key(descriptor) {
                    AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
                        AuthorPluginPanelWebView(ctx, gateway, owner,
                            descriptor["html"]?.jsonPrimitive?.content ?: "<!doctype html><html><body></body></html>",
                            onError = { panelError = it },
                        ).also { installedPanel = it }.webView
                    })
                }
            }
        }
    }
}

@Composable
private fun PluginFileSaveHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request by PluginFileExport.request.collectAsState()
    var launched by remember { mutableStateOf<PluginTextFileRequest?>(null) }
    val exporter = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("text/plain") {
        override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
            super.createIntent(context, input).setType(launched?.mimeType ?: "text/plain")
    }) { uri ->
        launched?.let { pending -> scope.launch { PluginFileExport.finish(pending, context.contentResolver, uri) } }
        launched = null
    }
    DisposableEffect(Unit) {
        PluginFileExport.attach()
        onDispose { PluginFileExport.detach() }
    }
    LaunchedEffect(request) {
        request?.let { pending ->
            launched = pending
            try { exporter.launch(pending.name) }
            catch (error: Throwable) { launched = null; PluginFileExport.fail(pending, error) }
        }
    }
}

/** Minimal import/enable/error entry point. It deliberately does not implement a plugin marketplace. */
@Composable
fun PluginManagerDialog(gateway: AuthorChatGateway, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<JsonObject>(buildJsonObject {}) }
    var error by remember { mutableStateOf("") }
    val items by PluginUiRegistry.items.collectAsState()
    fun run(action: suspend () -> Unit) {
        scope.launch {
            try { action(); plugins = gateway.invokeExtension("plugins.list", buildJsonObject {}).jsonObject }
            catch (failure: Throwable) { error = failure.message ?: failure.toString() }
        }
    }
    LaunchedEffect(gateway) { plugins = gateway.invokeExtension("plugins.list", buildJsonObject {}).jsonObject }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val manifest = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                val files = mutableMapOf<String, ByteArray>()
                ZipInputStream(bytes.inputStream()).use { zip ->
                    while (true) { val entry = zip.nextEntry ?: break; if (!entry.isDirectory) files[entry.name] = zip.readBytes() }
                }
                val value = Json.parseToJsonElement(checkNotNull(files["plugin.json"]) { "ZIP 缺少 plugin.json" }.decodeToString()).jsonObject
                val source = checkNotNull(files[value.getValue("entry").jsonPrimitive.content]) { "ZIP 缺少插件 entry" }.decodeToString()
                JsonObject(value + mapOf("source" to JsonPrimitive(source), "resources" to buildJsonObject {
                    files.forEach { (name, bytes) -> put(name, JsonPrimitive(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))) }
                }))
            } else Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            val id = manifest.getValue("id").jsonPrimitive.content
            gateway.invokeExtension("plugins.install", buildJsonObject { put("id", id); put("manifest", manifest) })
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("插件") }, text = {
        Column {
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            plugins.forEach { (id, manifest) ->
                manifest.jsonObject["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(manifest.jsonObject["name"]?.jsonPrimitive?.content ?: id)
                    Switch(checked = manifest.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull != false, onCheckedChange = { enabled ->
                        run { gateway.invokeExtension("plugins.setEnabled", buildJsonObject { put("id", id); put("enabled", enabled) }) }
                    })
                    TextButton(onClick = { run { gateway.invokeExtension("plugins.remove", buildJsonObject { put("id", id) }) } }) { Text("删除") }
                }
            }
            items.forEach { item ->
                TextButton(onClick = {
                    val owner = item.getValue("pluginId").jsonPrimitive.content
                    val id = item.getValue("id").jsonPrimitive.content
                    if (item["html"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) PluginUiRegistry.open(owner, id)
                    run { gateway.invokeExtension("plugins.emitEvent", buildJsonObject {
                        put("pluginId", owner); put("event", "plugin:$owner:button:$id"); put("payload", buildJsonObject {})
                    }) }
                }) {
                    Text(item["label"]?.jsonPrimitive?.content ?: item.getValue("id").jsonPrimitive.content)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { importer.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) }) { Text("导入插件") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
