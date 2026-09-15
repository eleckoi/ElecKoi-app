package com.eleckoi.android.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubReleaseClientTest {
    @Test
    fun releasePayloadKeepsOnlyUpdateMetadata() {
        val release = GitHubReleaseClient.parseRelease(
            """
            {
              "tag_name": "v0.2.0",
              "name": "ElecKoi 0.2.0",
              "html_url": "https://github.com/eleckoi/ElecKoi-app/releases/tag/v0.2.0",
              "body": "修复与体验改进",
              "published_at": "2026-08-31T10:00:00Z",
              "assets": [{"name": "ignored.apk"}]
            }
            """.trimIndent(),
        )

        assertEquals("v0.2.0", release.tagName)
        assertEquals("ElecKoi 0.2.0", release.title)
        assertEquals("修复与体验改进", release.notes)
    }

    @Test
    fun foreignReleaseUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubReleaseClient.parseRelease(
                """
                {
                  "tag_name": "v1.0.0",
                  "html_url": "https://example.invalid/releases/v1.0.0"
                }
                """.trimIndent(),
            )
        }
    }
}
