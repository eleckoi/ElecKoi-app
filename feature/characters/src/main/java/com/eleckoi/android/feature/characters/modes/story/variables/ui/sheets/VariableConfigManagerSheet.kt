package com.eleckoi.android.feature.characters.modes.story.variables.ui

import androidx.compose.runtime.Composable
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.feature.characters.modes.story.ui.shared.FeatureVersionManagerSheet
import com.eleckoi.android.feature.characters.modes.story.ui.shared.ManagedFeatureVersion
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryVersionCreateDialog

@Composable
internal fun VariableConfigManagerSheet(
    name: String,
    versions: List<VariableConfigVersion>,
    activeVersionId: String,
    appearance: AppearanceTheme,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateConfig: () -> Unit,
    onSelectVersion: (VariableConfigVersion) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDeleteConfig: () -> Unit,
) {
    FeatureVersionManagerSheet(
        title = "变量配置管理",
        featureTitle = "启用变量配置",
        featureDescription = "允许变量状态参与聊天生成与校验",
        namePlaceholder = "待命名",
        versionSectionTitle = "变量配置版本",
        createActionTitle = "新建版本",
        deleteActionTitle = "删除当前变量配置",
        name = name,
        showFeatureToggle = false,
        enabled = true,
        versions = versions.map { ManagedFeatureVersion(it.id, it.name) },
        activeVersionId = activeVersionId,
        appearance = appearance,
        onNameChange = onNameChange,
        onEnabledChange = {},
        onDismiss = onDismiss,
        onCreateVersion = onCreateConfig,
        onSelectVersion = { selected -> versions.firstOrNull { it.id == selected.id }?.let(onSelectVersion) },
        onImport = onImport,
        onExport = onExport,
        onDeleteVersion = onDeleteConfig,
    )
}

@Composable
internal fun VariableConfigCreateVersionDialog(
    versions: List<VariableConfigVersion>,
    activeVersionId: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (name: String, sourceVersionId: String?) -> Unit,
) {
    StoryVersionCreateDialog(
        versions = versions.map { ManagedFeatureVersion(it.id, it.name) },
        activeVersionId = activeVersionId,
        blankDescription = "不复制变量组、变量或校验脚本",
        appearance = appearance,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
