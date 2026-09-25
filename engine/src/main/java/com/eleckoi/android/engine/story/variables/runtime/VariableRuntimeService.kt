package com.eleckoi.android.engine.story.variables.runtime

import android.content.Context
import com.dokar.quickjs.QuickJsInterruptedException
import com.eleckoi.android.engine.story.variables.runtime.script.VariableRuntimeScripts
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class VariableRuntimeCheckResult(
    val ok: Boolean,
    val message: String = "",
    val detail: String = "",
    val normalizedStateJson: String = "",
)

data class EjsTemplateSource(
    val id: String,
    val controllerId: String,
    val title: String,
    val path: String,
    val content: String,
)

data class EjsTemplateMessage(
    val id: String,
    val role: String,
    val content: String,
)

data class EjsTemplateResolvedReference(
    val id: String,
    val title: String,
    val path: String,
)

data class EjsTemplateRenderResult(
    val content: String,
    val references: List<EjsTemplateResolvedReference> = emptyList(),
)

class VariableRuntimeService(
    private val context: Context,
) {
    private val runtimeManager = ProcessQuickJsRuntimeManager.instance

    suspend fun renderEjsTemplates(
        stateJson: String,
        messages: List<EjsTemplateMessage>,
        sources: List<EjsTemplateSource>,
        targetIds: Set<String>,
    ): Map<String, EjsTemplateRenderResult> {
        if (targetIds.isEmpty()) return emptyMap()
        val input = JSONObject()
            .put("state", jsonObjectOrEmpty(stateJson))
            .put(
                "messages",
                JSONArray(messages.map { message ->
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role)
                        .put("content", message.content)
                }),
            )
            .put(
                "sources",
                JSONArray(sources.map { source ->
                    JSONObject()
                        .put("id", source.id)
                        .put("controller_id", source.controllerId)
                        .put("title", source.title)
                        .put("path", source.path)
                        .put("content", source.content)
                }),
            )
            .put("target_ids", JSONArray(targetIds))
        val result = parseJsonResult(
            evaluateRaw(
                VariableRuntimeScripts.helpers + "\n" + VariableRuntimeScripts.ejsRuntime +
                    "\nawait __eleckoiRenderTemplates(${input});",
            ),
        )
        result.optString("error").takeIf(String::isNotBlank)?.let { message ->
            throw ElecKoiDataException("EJS 模板执行失败：$message")
        }
        val rendered = result.optJSONObject("rendered") ?: JSONObject()
        val references = result.optJSONObject("references") ?: JSONObject()
        return targetIds.associateWith { id ->
            val referenceArray = references.optJSONArray(id) ?: JSONArray()
            EjsTemplateRenderResult(
                content = rendered.optString(id),
                references = buildList {
                    repeat(referenceArray.length()) { index ->
                        val reference = referenceArray.optJSONObject(index) ?: return@repeat
                        add(
                            EjsTemplateResolvedReference(
                                id = reference.optString("id"),
                                title = reference.optString("title"),
                                path = reference.optString("path"),
                            ),
                        )
                    }
                },
            )
        }
    }

    suspend fun checkJavaScriptEngine(): VariableRuntimeCheckResult {
        return runCatching {
            evaluateRaw(zodBundleScript() + "\nBoolean(globalThis.z && globalThis.z.object)")
        }.fold(
            onSuccess = { result ->
                VariableRuntimeCheckResult(
                    ok = result == "true",
                    message = if (result == "true") "QuickJS 与 Zod 运行库可用" else "Zod 运行库加载失败",
                )
            },
            onFailure = { error ->
                VariableRuntimeCheckResult(
                    ok = false,
                    message = error.message.orEmpty(),
                    detail = error.stackTraceToString(),
                )
            },
        )
    }

    suspend fun validateSchemaCode(schemaCode: String): VariableRuntimeCheckResult {
        val code = schemaCode.trim()
        if (code.isBlank()) {
            return VariableRuntimeCheckResult(ok = true, message = "未填写总校验配置")
        }
        return runCatching {
            evaluateRaw(zodBundleScript() + "\n" + VariableRuntimeScripts.schemaProbe(code))
        }.fold(
            onSuccess = { raw -> parseRuntimeResult(raw) },
            onFailure = { error ->
                VariableRuntimeCheckResult(
                    ok = false,
                    message = error.message.orEmpty(),
                    detail = error.stackTraceToString(),
                )
            },
        )
    }

    suspend fun validateState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult {
        val code = schemaCode.trim()
        val state = stateJson.trim()
        if (code.isBlank()) {
            return VariableRuntimeCheckResult(ok = false, message = "未填写总校验配置")
        }
        if (state.isBlank()) {
            return VariableRuntimeCheckResult(ok = false, message = "未填写初始化变量配置")
        }
        return runCatching {
            evaluateRaw(
                zodBundleScript() + "\n" + VariableRuntimeScripts.stateValidation(code, state),
            )
        }.fold(
            onSuccess = { raw -> parseRuntimeResult(raw) },
            onFailure = { error ->
                VariableRuntimeCheckResult(
                    ok = false,
                    message = error.message.orEmpty(),
                    detail = error.stackTraceToString(),
                )
            },
        )
    }

    private suspend fun evaluateRaw(script: String): String = try {
        runtimeManager.evaluate(script)
    } catch (error: QuickJsInterruptedException) {
        throw ElecKoiDataException("变量脚本执行超时", error)
    }

    private fun jsonObjectOrEmpty(value: String): JSONObject = runCatching {
        JSONObject(value.ifBlank { "{}" })
    }.getOrElse { throw ElecKoiDataException("变量状态 JSON 已损坏", it) }

    private fun parseJsonResult(raw: String): JSONObject {
        runCatching { return JSONObject(raw) }
        val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
        return decoded?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?: throw ElecKoiDataException("QuickJS 返回了无法识别的数据")
    }

    private fun parseRuntimeResult(raw: String): VariableRuntimeCheckResult {
        val jsonText = raw.trim().trim('"')
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
        val json = runCatching { JSONObject(jsonText) }.getOrNull()
            ?: return VariableRuntimeCheckResult(ok = false, message = raw)
        return VariableRuntimeCheckResult(
            ok = json.optBoolean("ok", false),
            message = json.optString("message"),
            detail = json.optString("detail"),
            normalizedStateJson = json.optJSONObject("state")?.toString(2).orEmpty(),
        )
    }

    private fun zodBundleScript(): String {
        val zodBundle = context.assets.open(VariableRuntimeScripts.ZodBundleAssetPath)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return zodBundle + "\n" + VariableRuntimeScripts.schemaFactory
    }
}
