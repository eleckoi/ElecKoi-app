package com.eleckoi.android.feature.characters.transfer.format.json

import com.eleckoi.android.feature.characters.transfer.format.CharacterCardFormat
import com.eleckoi.android.feature.characters.transfer.format.CharacterCardJsonCodec
import com.eleckoi.android.feature.characters.transfer.model.DecodedCharacterCard
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal object JsonCharacterCardFormat : CharacterCardFormat {
    override fun decode(bytes: ByteArray): DecodedCharacterCard? {
        val text = bytes.toString(StandardCharsets.UTF_8).trim()
        if (!text.startsWith("{")) return null
        val root = runCatching { JSONObject(text) }.getOrElse { error("ElecKoi 角色卡 JSON 已损坏") }
        require(root.optString("format") == CharacterCardJsonCodec.PortableFormat) {
            "这不是 ElecKoi 角色卡"
        }
        val packageData = CharacterCardJsonCodec.decodeJson(text)
        return DecodedCharacterCard(
            packageData = packageData,
            sourceImage = null,
            complete = true,
            summary = "ElecKoi 完整角色卡",
            regexRules = packageData.regexRules,
        )
    }
}
