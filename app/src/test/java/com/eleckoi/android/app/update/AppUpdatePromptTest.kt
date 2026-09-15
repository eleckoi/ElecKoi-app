package com.eleckoi.android.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePromptTest {
    @Test
    fun availableVersionIsPromptedUntilThatVersionIsDismissed() {
        assertTrue(
            shouldShowAppUpdatePrompt(
                remindersEnabled = true,
                updateAvailable = true,
                latestTag = "v0.1.6",
                dismissedTag = "",
            ),
        )
        assertFalse(
            shouldShowAppUpdatePrompt(
                remindersEnabled = true,
                updateAvailable = true,
                latestTag = "v0.1.6",
                dismissedTag = "v0.1.6",
            ),
        )
        assertTrue(
            shouldShowAppUpdatePrompt(
                remindersEnabled = true,
                updateAvailable = true,
                latestTag = "v0.1.7",
                dismissedTag = "v0.1.6",
            ),
        )
    }

    @Test
    fun currentOrUnknownVersionIsNotPrompted() {
        assertFalse(
            shouldShowAppUpdatePrompt(
                remindersEnabled = true,
                updateAvailable = false,
                latestTag = "v0.1.5",
                dismissedTag = "",
            ),
        )
        assertFalse(
            shouldShowAppUpdatePrompt(
                remindersEnabled = true,
                updateAvailable = true,
                latestTag = "",
                dismissedTag = "",
            ),
        )
        assertFalse(
            shouldShowAppUpdatePrompt(
                remindersEnabled = false,
                updateAvailable = true,
                latestTag = "v0.1.6",
                dismissedTag = "",
            ),
        )
    }
}
