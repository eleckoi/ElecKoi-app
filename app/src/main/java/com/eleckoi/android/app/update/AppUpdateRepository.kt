package com.eleckoi.android.app.update

import android.content.Context
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSettings
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSource
import com.eleckoi.android.feature.settings.ui.update.normalizeMirrorPrefix
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appUpdateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_update",
)

internal data class AppUpdateSnapshot(
    val remindersEnabled: Boolean = true,
    val latestRelease: AppRelease? = null,
    val lastCheckedAtMillis: Long = 0L,
    val notifiedTag: String = "",
    val connection: GitHubConnectionSettings = GitHubConnectionSettings(),
)

internal class AppUpdateRepository(
    context: Context,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val dataStore = context.applicationContext.appUpdateDataStore

    val snapshot: Flow<AppUpdateSnapshot> = dataStore.data.map(::snapshotFrom)

    suspend fun current(): AppUpdateSnapshot = snapshot.first()

    suspend fun checkForUpdate(): AppUpdateSnapshot {
        val result = releaseClient.latest(current().connection)
        dataStore.edit { preferences ->
            preferences[Keys.LastCheckedAt] = nowMillis()
            when (result) {
                LatestReleaseResult.NonePublished -> clearRelease(preferences)
                is LatestReleaseResult.Published -> writeRelease(preferences, result.release)
            }
        }
        return current()
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.RemindersEnabled] = enabled }
    }

    suspend fun setConnection(settings: GitHubConnectionSettings) {
        val prefix = normalizeMirrorPrefix(settings.customPrefix)
        require(settings.source != GitHubConnectionSource.Custom || prefix != null) {
            "请填写有效的 HTTPS 镜像地址"
        }
        dataStore.edit { preferences ->
            preferences[Keys.ConnectionSource] = settings.source.name
            preferences[Keys.CustomMirrorPrefix] = prefix.orEmpty()
        }
    }

    suspend fun markNotified(tagName: String) {
        dataStore.edit { preferences -> preferences[Keys.NotifiedTag] = tagName }
    }

    private fun snapshotFrom(preferences: Preferences): AppUpdateSnapshot {
        val tagName = preferences[Keys.LatestTag].orEmpty()
        val pageUrl = preferences[Keys.LatestPageUrl].orEmpty()
        val release = if (tagName.isNotBlank() && pageUrl.isNotBlank()) {
            AppRelease(
                tagName = tagName,
                title = preferences[Keys.LatestTitle].orEmpty().ifBlank { tagName },
                pageUrl = pageUrl,
                notes = preferences[Keys.LatestNotes].orEmpty(),
                publishedAt = preferences[Keys.LatestPublishedAt].orEmpty(),
                apk = preferences[Keys.LatestApkUrl]?.let { downloadUrl ->
                    AppReleaseApk(
                        name = preferences[Keys.LatestApkName].orEmpty(),
                        downloadUrl = downloadUrl,
                        sizeBytes = preferences[Keys.LatestApkSize] ?: 0L,
                        sha256 = preferences[Keys.LatestApkSha256],
                    )
                },
            )
        } else {
            null
        }
        return AppUpdateSnapshot(
            remindersEnabled = preferences[Keys.RemindersEnabled] ?: true,
            latestRelease = release,
            lastCheckedAtMillis = preferences[Keys.LastCheckedAt] ?: 0L,
            notifiedTag = preferences[Keys.NotifiedTag].orEmpty(),
            connection = connectionFrom(preferences),
        )
    }

    private fun connectionFrom(preferences: Preferences): GitHubConnectionSettings {
        val prefix = normalizeMirrorPrefix(preferences[Keys.CustomMirrorPrefix].orEmpty()).orEmpty()
        val source = GitHubConnectionSource.entries.find {
            it.name == preferences[Keys.ConnectionSource]
        } ?: GitHubConnectionSource.Official
        return GitHubConnectionSettings(
            source = if (source == GitHubConnectionSource.Custom && prefix.isEmpty()) {
                GitHubConnectionSource.Official
            } else source,
            customPrefix = prefix,
        )
    }

    private fun writeRelease(preferences: MutablePreferences, release: AppRelease) {
        preferences[Keys.LatestTag] = release.tagName
        preferences[Keys.LatestTitle] = release.title
        preferences[Keys.LatestPageUrl] = release.pageUrl
        preferences[Keys.LatestNotes] = release.notes
        preferences[Keys.LatestPublishedAt] = release.publishedAt
        val apk = release.apk
        if (apk != null) {
            preferences[Keys.LatestApkName] = apk.name
            preferences[Keys.LatestApkUrl] = apk.downloadUrl
            preferences[Keys.LatestApkSize] = apk.sizeBytes
            if (apk.sha256 != null) {
                preferences[Keys.LatestApkSha256] = apk.sha256
            } else {
                preferences.remove(Keys.LatestApkSha256)
            }
        } else {
            clearApk(preferences)
        }
    }

    private fun clearRelease(preferences: MutablePreferences) {
        preferences.remove(Keys.LatestTag)
        preferences.remove(Keys.LatestTitle)
        preferences.remove(Keys.LatestPageUrl)
        preferences.remove(Keys.LatestNotes)
        preferences.remove(Keys.LatestPublishedAt)
        clearApk(preferences)
    }

    private fun clearApk(preferences: MutablePreferences) {
        preferences.remove(Keys.LatestApkName)
        preferences.remove(Keys.LatestApkUrl)
        preferences.remove(Keys.LatestApkSize)
        preferences.remove(Keys.LatestApkSha256)
    }

    private object Keys {
        val ConnectionSource = stringPreferencesKey("github_connection_source")
        val CustomMirrorPrefix = stringPreferencesKey("github_custom_mirror_prefix")
        val RemindersEnabled = booleanPreferencesKey("reminders_enabled")
        val LatestTag = stringPreferencesKey("latest_tag")
        val LatestTitle = stringPreferencesKey("latest_title")
        val LatestPageUrl = stringPreferencesKey("latest_page_url")
        val LatestNotes = stringPreferencesKey("latest_notes")
        val LatestPublishedAt = stringPreferencesKey("latest_published_at")
        val LastCheckedAt = longPreferencesKey("last_checked_at")
        val NotifiedTag = stringPreferencesKey("notified_tag")
        val LatestApkName = stringPreferencesKey("latest_apk_name")
        val LatestApkUrl = stringPreferencesKey("latest_apk_url")
        val LatestApkSize = longPreferencesKey("latest_apk_size")
        val LatestApkSha256 = stringPreferencesKey("latest_apk_sha256")
    }
}
