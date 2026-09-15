package com.eleckoi.android.app.service

import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.transfer.data.CharacterTransferRepository
import com.eleckoi.android.feature.characters.transfer.api.CharacterTransferService
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreview
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportSource
import com.eleckoi.android.feature.characters.transfer.model.CharacterExportFormat
import com.eleckoi.android.feature.characters.transfer.model.ExportedCharacterCard
import java.io.File

internal class CharacterTransferServiceImpl(
    private val transfers: CharacterTransferRepository,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
) : CharacterTransferService {
    override fun prepareCharacterImports(
        files: List<File>,
        source: CharacterImportSource,
    ): CharacterImportPreview {
        return transfers.prepareImports(files, source)
    }

    override fun discardPreparedCharacter(token: String) {
        transfers.discardImport(token)
    }

    override suspend fun importPreparedCharacters(token: String): List<CharacterSlot> {
        val characters = transfers.importPrepared(token)
        characters.forEach { character -> creatorWorkspaces.ensureCharacterContainer(character.id) }
        return characters
    }

    override suspend fun exportCharacterCard(
        characterId: String,
        format: CharacterExportFormat,
    ): ExportedCharacterCard {
        return transfers.exportCharacter(characterId, format)
    }

    override suspend fun exportCharacterCards(
        characterIds: List<String>,
        format: CharacterExportFormat,
    ): List<ExportedCharacterCard> {
        return transfers.exportCharacters(characterIds, format)
    }
}
