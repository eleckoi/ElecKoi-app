package com.eleckoi.android.app.service

import android.net.Uri
import com.eleckoi.android.feature.characters.data.UserProfileRepository
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.settings.api.ProfileService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

internal class ProfileServiceImpl(
    private val profile: UserProfileRepository,
) : ProfileService {
    override val userProfileFlow: Flow<UserProfile> = profile.profileFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override fun saveUserName(name: String): UserProfile = profile.saveName(name)

    override fun saveUserAvatars(files: Map<AvatarSlot, File>): UserProfile = profile.saveAvatars(files)

    override fun saveUserCover(coverUri: Uri): UserProfile = profile.saveCover(coverUri)

    override fun clearUserCover(): UserProfile = profile.clearCover()
}
