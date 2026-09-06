package com.eleckoi.android.app.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Read-only, app-private bridge used by Coil to render creator assets in the assistant timeline. */
class CreatorMediaAssetProvider : ContentProvider() {
    private val repository: CreatorWorkspaceRepository by lazy {
        val appContext = requireNotNull(context).applicationContext
        CreatorWorkspaceRepository(
            appContext,
            ElecKoiDatabase.get(appContext),
        )
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Creator media is read-only")
        val segments = uri.pathSegments
        if (segments.size != 2) throw FileNotFoundException("Invalid creator media URI")
        val file = runBlocking(Dispatchers.IO) {
            repository.creatorMediaAssetFile(
                workspaceId = segments[0],
                assetId = segments[1],
            )
        } ?: throw FileNotFoundException("Creator media asset not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
