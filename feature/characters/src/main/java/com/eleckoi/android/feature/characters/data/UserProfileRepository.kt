package com.eleckoi.android.feature.characters.data

import android.content.Context
import android.net.Uri
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.JsonFileStore
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class UserProfileRepository(
    context: Context,
    private val store: JsonFileStore,
    database: ElecKoiDatabase,
) {
    private val appContext = context.applicationContext
    private val dao = database.userProfileDao()

    val profileFlow: Flow<UserProfile> = dao.profileFlow().map { it.toUserProfile() }

    fun load(): UserProfile {
        return dao.profile().toUserProfile()
    }

    fun saveName(name: String): UserProfile {
        val current = load()
        val saved = current.copy(userName = name.trim())
        if (saved == current) return current
        dao.upsert(saved.toEntity())
        return saved
    }

    /** Restores an already materialized profile snapshot after its media paths were rewritten. */
    fun restoreSnapshot(profile: UserProfile): UserProfile {
        val saved = profile.copy(userName = profile.userName.trim())
        dao.upsert(saved.toEntity())
        return saved
    }

    /** 和角色头像同一套规则：传进来的槽位各写各的，没传的不动。 */
    fun saveAvatars(files: Map<AvatarSlot, File>): UserProfile {
        if (files.isEmpty()) throw ElecKoiDataException("没有要保存的头像")
        val current = load()
        val stored = mutableMapOf<AvatarSlot, File>()
        try {
            files.forEach { (slot, source) ->
                val extension = if (slot == AvatarSlot.Circle) "png" else "jpg"
                val destination = store.file("user", "${slot.userFilePrefix}-${newId(10)}.$extension")
                destination.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                stored[slot] = destination
            }
            val saved = current.copy(
                userAvatar = stored[AvatarSlot.Circle]?.absolutePath ?: current.userAvatar,
                userSquare = stored[AvatarSlot.Square]?.absolutePath ?: current.userSquare,
                userPortrait = stored[AvatarSlot.Portrait]?.absolutePath ?: current.userPortrait,
            )
            dao.upsert(saved.toEntity())
            stored.forEach { (slot, file) -> cleanupUserFiles(slot.userFilePrefix, file) }
            return saved
        } catch (error: Throwable) {
            stored.values.forEach { it.delete() }
            throw error
        }
    }

    fun saveCover(coverUri: Uri): UserProfile {
        val target = store.file("user", "cover-${newId(10)}.jpg")
        target.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(coverUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw ElecKoiDataException("资料背景读取失败")
        val saved = load().copy(userCover = target.absolutePath)
        return try {
            dao.upsert(saved.toEntity())
            cleanupUserFiles("cover", target)
            saved
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    /** Same persistence path as the document-picker variant, for app-level backup restore. */
    fun saveCoverFile(coverFile: File): UserProfile {
        val target = store.file("user", "cover-${newId(10)}.jpg")
        target.parentFile?.mkdirs()
        coverFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val saved = load().copy(userCover = target.absolutePath)
        return try {
            dao.upsert(saved.toEntity())
            cleanupUserFiles("cover", target)
            saved
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    // 用户的三张头像和资料背景都堆在 user/ 一个目录里，槽位自带的 "cover" 前缀会和背景图撞名，
    // 清理旧文件时就会互删。这里给头像单独一套前缀。
    private val AvatarSlot.userFilePrefix: String get() = "user-${name.lowercase()}"

    private fun cleanupUserFiles(prefix: String, keep: File) {
        val dir = store.dir("user")
        val rootPath = dir.canonicalPath
        val keepPath = keep.canonicalPath
        dir.listFiles { file -> file.isFile && file.name.startsWith("$prefix-") }
            .orEmpty()
            .filter { it.canonicalPath != keepPath && it.canonicalPath.startsWith(rootPath) }
            .forEach { it.delete() }
    }
}
