package com.eleckoi.android.feature.characters.transfer.api

import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreview
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportSource
import com.eleckoi.android.feature.characters.transfer.model.CharacterExportFormat
import com.eleckoi.android.feature.characters.transfer.model.ExportedCharacterCard
import java.io.File

interface CharacterTransferService {
    fun prepareCharacterImports(files: List<File>, source: CharacterImportSource): CharacterImportPreview
    fun discardPreparedCharacter(token: String)
    suspend fun importPreparedCharacters(token: String): List<CharacterSlot>
    suspend fun exportCharacterCard(characterId: String, format: CharacterExportFormat): ExportedCharacterCard
    suspend fun exportCharacterCards(
        characterIds: List<String>,
        format: CharacterExportFormat,
    ): List<ExportedCharacterCard>
}
