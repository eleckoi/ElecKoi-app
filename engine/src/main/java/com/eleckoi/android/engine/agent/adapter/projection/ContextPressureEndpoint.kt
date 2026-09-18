package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJson
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.OutputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Validates and routes one DSH `contextPressure` projection update. */
internal class ContextPressureEndpoint(
    private val routes: DshProviderRouteRegistry,
) {
    fun accept(body: ByteArray, output: OutputStream) {
        val request = runCatching {
            ElecKoiJson.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "上下文投影请求无效：${error.message}")
            return
        }
        val sessionId = (request["sessionId"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { SessionId.matches(it) }
        val sequence = (request["seq"] as? JsonPrimitive)?.longOrNull
        val value = request["value"] as? JsonObject
        val pressureTokens = value.optionalNonNegativeLong("pressureTokens")
        val projectedTokens = value.optionalNonNegativeLong("projectedTokens")
        val contextWindow = value.optionalNonNegativeLong("contextWindow")
        val systemTokens = value.optionalNonNegativeLong("systemTokens")
        val toolsTokens = value.optionalNonNegativeLong("toolsTokens")
        val messageTokens = value.optionalNonNegativeLong("messageTokens")
        if (
            sessionId == null ||
            sequence == null || sequence < 0L ||
            value == null ||
            contextWindow == 0L ||
            value.hasInvalidLong("pressureTokens") ||
            value.hasInvalidLong("projectedTokens") ||
            value.hasInvalidLong("contextWindow") ||
            value.hasInvalidLong("systemTokens") ||
            value.hasInvalidLong("toolsTokens") ||
            value.hasInvalidLong("messageTokens")
        ) {
            writeJsonError(output, 400, "上下文投影缺少有效的 sessionId、seq 或 Token 数据")
            return
        }
        val accepted = routes.publishContextPressure(
            AdapterContextPressure(
                sessionId = sessionId,
                sequence = sequence,
                pressureTokens = pressureTokens,
                projectedTokens = projectedTokens,
                contextWindow = contextWindow,
                systemTokens = systemTokens,
                toolsTokens = toolsTokens,
                messageTokens = messageTokens,
            ),
        )
        if (!accepted) {
            writeJsonError(output, 404, "Agent session 上下文投影路由不可用")
            return
        }
        writeJson(output, buildJsonObject { put("accepted", true) })
    }

    private companion object {
        val SessionId = Regex("^[A-Za-z0-9._:-]{1,160}$")
    }
}

private fun JsonObject?.optionalNonNegativeLong(name: String): Long? =
    (this?.get(name) as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0L }

private fun JsonObject?.hasInvalidLong(name: String): Boolean {
    val element = this?.get(name) ?: return false
    val value = (element as? JsonPrimitive)?.longOrNull
    return value == null || value < 0L
}
