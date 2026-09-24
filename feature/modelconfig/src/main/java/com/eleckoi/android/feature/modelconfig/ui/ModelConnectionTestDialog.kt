package com.eleckoi.android.feature.modelconfig.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.ElecKoiSuccess
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.foundation.design.overlayScrim

internal enum class ModelTestStatus { Pending, Running, Passed, Failed }

internal data class ModelTestStep(
    val label: String,
    val hint: String = "",
    val status: ModelTestStatus = ModelTestStatus.Pending,
    val detail: String = "",
)

internal data class ModelTestState(
    val steps: List<ModelTestStep>,
    val finished: Boolean = false,
    val toolsSupported: Boolean? = null,
    val formatFallbackSuggested: Boolean = false,
    val completionMessage: String = "",
) {
    val failed: Boolean get() = steps.any { it.status == ModelTestStatus.Failed }
}

// Step by step rather than one success/failure message. When a proxy does not work, the useful
// information is which stage it broke at — an unreachable address, a rejected key and a model that
// cannot do tool calls need three different fixes, and a single "连接测试失败" tells you none of them.
@Composable
internal fun ModelConnectionTestDialog(
    state: ModelTestState,
    modelLabel: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.overlayScrim())
            .noRippleClickable { if (state.finished) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(appearance.mobileSurface)
                .noRippleClickable {}
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text("测试连接", color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                modelLabel.ifBlank { "未选择模型" },
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 14.dp),
            )

            state.steps.forEach { step ->
                val compactDetail = step.detail.takeIf { it.length <= 10 }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelTestStatusMark(step.status, appearance)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                step.label,
                                color = if (step.status == ModelTestStatus.Pending) {
                                    appearance.mobileSoft
                                } else {
                                    appearance.mobileText
                                },
                                fontSize = 14.sp,
                            )
                            if (step.hint.isNotBlank()) {
                                Text(
                                    step.hint,
                                    color = appearance.mobileSoft,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 5.dp),
                                )
                            }
                            if (compactDetail != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    compactDetail,
                                    color = if (step.status == ModelTestStatus.Failed) {
                                        ElecKoiDanger
                                    } else {
                                        appearance.mobileMuted
                                    },
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        if (step.detail.isNotBlank() && compactDetail == null) {
                            Text(
                                step.detail,
                                color = if (step.status == ModelTestStatus.Failed) {
                                    ElecKoiDanger
                                } else {
                                    appearance.mobileMuted
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appearance.mobileSearchBg)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Text(
                    text = when {
                        !state.finished -> "正在按当前接口格式验证连接和工具调用。"
                        state.formatFallbackSuggested -> "当前接口格式未通过测试，请尝试其他接口格式。"
                        state.completionMessage.isNotBlank() -> state.completionMessage
                        state.toolsSupported == true -> "这个配置支持工具调用，可以用于 Agent。"
                        state.toolsSupported == false -> "这个配置不支持工具调用，Agent 功能会失败，建议更换 API 地址或接口格式。"
                        else -> "检测未完成，工具调用能力未知。"
                    },
                    color = if (state.failed) ElecKoiDanger else appearance.mobileMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.finished) appearance.mobileBlue else appearance.mobileSearchBg)
                    .noRippleClickable { if (state.finished) onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.finished) "完成" else "检测中…",
                    color = if (state.finished) androidx.compose.ui.graphics.Color.White else appearance.mobileSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ModelTestStatusMark(status: ModelTestStatus, appearance: AppearanceTheme) {
    when (status) {
        ModelTestStatus.Running -> CircularProgressIndicator(
            modifier = Modifier.size(17.dp),
            strokeWidth = 2.dp,
            color = appearance.mobileBlue,
        )
        ModelTestStatus.Passed -> FilledSvgIcon(
            paths = listOf(PhosphorRegular.CheckCircle),
            color = ElecKoiSuccess,
            iconSize = 18.dp,
            viewportSize = 256f,
        )
        ModelTestStatus.Failed -> FilledSvgIcon(
            paths = listOf(PhosphorRegular.XCircle),
            color = ElecKoiDanger,
            iconSize = 18.dp,
            viewportSize = 256f,
        )
        ModelTestStatus.Pending -> Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .border(1.5.dp, appearance.mobileLine, CircleShape),
        )
    }
}
