package com.eleckoi.android.feature.characters.transfer.format.png

import com.eleckoi.android.feature.characters.transfer.format.CharacterCardFormat
import com.eleckoi.android.feature.characters.transfer.format.CharacterCardJsonCodec
import com.eleckoi.android.feature.characters.transfer.model.DecodedCharacterCard
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacterPackage

internal object PngCharacterCardFormat : CharacterCardFormat {
    override fun decode(bytes: ByteArray): DecodedCharacterCard? {
        if (!PngTextChunkCodec.isPng(bytes)) return null
        val text = PngTextChunkCodec.readText(bytes)
        text[CharacterCardJsonCodec.PortableKeyword]?.let { encoded ->
            return DecodedCharacterCard(
                packageData = CharacterCardJsonCodec.decodePortable(encoded),
                sourceImage = bytes,
                complete = true,
            ).let { decoded ->
                decoded.copy(
                    summary = "ElecKoi 完整角色卡",
                    regexRules = decoded.packageData.regexRules,
                )
            }
        }
        error("图片里没有 ElecKoi 角色卡数据")
    }

    fun encode(image: ByteArray, value: PortableCharacterPackage): ByteArray {
        return PngTextChunkCodec.writeText(
            image,
            mapOf(CharacterCardJsonCodec.PortableKeyword to CharacterCardJsonCodec.encodePortable(value)),
            removeKeys = ThirdPartyCharacterKeywords,
        )
    }

    private val ThirdPartyCharacterKeywords = setOf("chara", "ccv3", "chara_card_v2")
}
