package com.eleckoi.android.sdk.author

object AuthorApiCatalog {
    val definitions: List<AuthorApiDefinition> = listOf(
        definition("app.getInfo", "app", "读取应用与作者 API 版本信息", AuthorApiPermission.AppRead),
        definition("app.getCapabilities", "app", "读取当前页面可调用的完整 API 清单", AuthorApiPermission.AppRead),
        definition("context.current", "context", "读取当前 WebView 所处的原生页面上下文", AuthorApiPermission.ContextRead),
        definition("variables.getState", "variables", "读取当前变量状态", AuthorApiPermission.VariablesRead),
        definition("variables.getConfig", "variables", "读取角色变量结构与初始配置", AuthorApiPermission.VariablesRead),
        definition("variables.setState", "variables", "替换当前聊天变量状态", AuthorApiPermission.VariablesWrite),
        definition("variables.merge", "variables", "合并更新当前聊天变量状态", AuthorApiPermission.VariablesWrite),
        definition("variables.applyPatch", "variables", "按操作清单更新当前聊天变量状态", AuthorApiPermission.VariablesWrite),
        definition("variables.reset", "variables", "重置当前聊天变量状态", AuthorApiPermission.VariablesWrite),
        definition("openings.list", "openings", "读取当前聊天的全部开场白选项", AuthorApiPermission.OpeningsRead),
        definition("openings.current", "openings", "读取当前选中的开场白", AuthorApiPermission.OpeningsRead),
        definition("openings.select", "openings", "切换当前聊天的开场白", AuthorApiPermission.OpeningsWrite),
        definition("messages.list", "messages", "读取当前聊天的消息列表", AuthorApiPermission.MessagesRead),
        definition("messages.get", "messages", "按消息 ID 读取一条消息", AuthorApiPermission.MessagesRead),
        definition("messages.current", "messages", "读取当前最后一条消息", AuthorApiPermission.MessagesRead),
        definition("messages.deleteFrom", "messages", "删除指定消息及其后面的全部消息", AuthorApiPermission.MessagesWrite),
        definition("messages.regenerate", "messages", "从指定 AI 消息重新生成", AuthorApiPermission.MessagesWrite),
        definition("messages.editAndRegenerate", "messages", "修改用户消息并从该处重新生成", AuthorApiPermission.MessagesWrite),
        definition("chat.current", "chat", "读取当前聊天会话摘要", AuthorApiPermission.ChatRead),
        definition("chat.list", "chat", "读取聊天会话列表", AuthorApiPermission.ChatRead),
        definition("chat.getGenerationState", "chat", "读取 AI 生成状态", AuthorApiPermission.ChatRead),
        definition("chat.getAgentTrajectory", "chat", "读取当前聊天的完整 Agent 处理轨迹", AuthorApiPermission.ChatRead),
        definition("chat.getModels", "chat", "读取可用于当前聊天的模型摘要", AuthorApiPermission.ChatRead),
        definition("chat.send", "chat", "发送消息并开始 AI 回复", AuthorApiPermission.ChatSend),
        definition("chat.stopGeneration", "chat", "停止当前 AI 回复", AuthorApiPermission.ChatWrite),
        definition("chat.create", "chat", "为角色创建新对话", AuthorApiPermission.ChatWrite),
        definition("chat.open", "chat", "切换当前对话", AuthorApiPermission.ChatWrite),
        definition("chat.delete", "chat", "删除指定对话", AuthorApiPermission.ChatWrite),
        definition("chat.selectModel", "chat", "选择聊天模型", AuthorApiPermission.ChatWrite),
        definition("character.current", "character", "读取当前角色摘要", AuthorApiPermission.CharacterRead),
        definition("settingLibrary.current", "settingLibrary", "读取当前聊天实际生效的设定库内容", AuthorApiPermission.SettingLibraryRead),
        definition("settingLibrary.getSummary", "settingLibrary", "读取当前角色设定库摘要", AuthorApiPermission.SettingLibraryRead),
        definition("media.getMessageAttachments", "media", "读取一条消息里的全部媒体附件", AuthorApiPermission.MediaRead),
        definition("media.getMessageAttachment", "media", "读取一条消息里的指定媒体附件", AuthorApiPermission.MediaRead),
        definition("audio.play", "audio", "播放背景音乐、环境音、语音或音效", AuthorApiPermission.AudioWrite),
        definition("audio.pause", "audio", "暂停一个音频频道", AuthorApiPermission.AudioWrite),
        definition("audio.resume", "audio", "继续播放一个音频频道", AuthorApiPermission.AudioWrite),
        definition("audio.stop", "audio", "停止一个音频频道", AuthorApiPermission.AudioWrite),
        definition("audio.seek", "audio", "跳转一个音频频道的播放位置", AuthorApiPermission.AudioWrite),
        definition("audio.getState", "audio", "读取一个音频频道的播放状态", AuthorApiPermission.AudioRead),
        definition("audio.getPlaylist", "audio", "读取一个音频频道的播放列表", AuthorApiPermission.AudioRead),
        definition("audio.setPlaylist", "audio", "替换一个音频频道的播放列表", AuthorApiPermission.AudioWrite),
        definition("audio.appendPlaylist", "audio", "向一个音频频道追加曲目", AuthorApiPermission.AudioWrite),
        definition("audio.getSettings", "audio", "读取宿主音频设置", AuthorApiPermission.AudioRead),
        definition("audio.setSettings", "audio", "修改宿主音频设置", AuthorApiPermission.AudioWrite),
        definition("input.get", "input", "读取当前聊天输入框内容", AuthorApiPermission.InputRead),
        definition("input.set", "input", "设置当前聊天输入框内容", AuthorApiPermission.InputWrite),
        definition("input.append", "input", "向当前聊天输入框追加内容", AuthorApiPermission.InputWrite),
        definition("input.clear", "input", "清空当前聊天输入框", AuthorApiPermission.InputWrite),
        definition("input.send", "input", "发送当前输入框内容", AuthorApiPermission.ChatWrite),
        definition("events.list", "events", "读取作者前端可订阅的事件名称", AuthorApiPermission.EventsRead),
    )

    private val definitionsByMethod = definitions.associateBy(AuthorApiDefinition::method)

    fun find(method: String): AuthorApiDefinition? = definitionsByMethod[method]

    internal fun require(method: String): AuthorApiDefinition {
        return checkNotNull(find(method)) { "Author API catalog is missing $method" }
    }

    private fun definition(
        method: String,
        namespace: String,
        description: String,
        permission: AuthorApiPermission,
    ) = AuthorApiDefinition(
        method = method,
        namespace = namespace,
        description = description,
        permission = permission.wireName,
    )
}
