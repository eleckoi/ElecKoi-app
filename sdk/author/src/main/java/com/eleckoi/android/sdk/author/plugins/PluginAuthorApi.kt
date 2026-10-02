package com.eleckoi.android.sdk.author.plugins

import com.eleckoi.android.sdk.author.*

/** These routes are backed by the application service graph, not a WebView-local mock. */
internal object PluginAuthorApi {
    val definitions = listOf(
        "plugins.list", "plugins.install", "plugins.remove", "plugins.setEnabled", "plugins.bootstrap", "plugins.hookResult", "plugins.runtimeReady", "plugins.openManager", "plugins.emitEvent",
        "storage.get", "storage.set", "storage.delete", "storage.list", "storage.sql", "storage.transaction",
        "messages.read", "messages.update", "messages.insert", "messages.delete", "messages.metadata", "messages.swipes",
        "variables.readScope", "variables.writeScope", "settings.get", "settings.set",
        "generation.invoke", "generation.start", "generation.get", "generation.cancel", "network.request", "network.cancel", "prompts.set", "prompts.remove",
        "worldbooks.list", "worldbooks.get", "worldbooks.put", "worldbooks.delete", "worldbooks.bind", "worldbooks.bindings",
        "characters.list", "characters.read", "characters.write", "personas.get", "personas.set",
        "presets.list", "presets.get", "presets.select", "presets.update", "regex.get", "regex.set",
        "ui.register", "ui.unregister", "ui.list", "ui.open", "macros.register", "macros.unregister", "files.saveText",
    ).map { method -> AuthorApiDefinition(method, method.substringBefore('.'), "插件宿主能力：$method", "chat.write") }

    val routes = definitions.map { definition ->
        AuthorApiRoute(definition) { environment, params -> environment.requireChatGateway().invokeExtension(definition.method, params) }
    }
}
