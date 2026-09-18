package com.eleckoi.android.engine.agent.adapter.request

import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Android-owned, provider-neutral context projected into every model step of one Agent turn. */
internal data class AgentTurnRequestContext(
    val userMessage: String,
    val history: List<AgentHistoryItem>,
    val injections: List<AgentContextInjection>,
    val historyProjection: AgentHistoryProjection,
) {
    /** Product history is immutable for one turn; parse its Room ledger envelope only once. */
    val parsedProductHistory: List<JsonObject> = history.mapNotNull { item ->
        runCatching {
            ElecKoiJson.parseToJsonElement(item.responseItemJson).jsonObject
        }.getOrNull()
    }
}

internal enum class AgentHistoryProjection {
    Native,
    SeedProductHistory,
}
