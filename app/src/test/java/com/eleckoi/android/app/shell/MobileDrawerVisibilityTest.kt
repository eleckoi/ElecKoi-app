package com.eleckoi.android.app.shell

import com.eleckoi.android.app.navigation.MobileRoute
import androidx.compose.ui.graphics.Color
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.withDarkAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileDrawerVisibilityTest {
    @Test
    fun `drawer stays requested while profile temporarily hides it`() {
        assertTrue(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Root))
        assertFalse(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Profile))
        assertTrue(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Root))
    }

    @Test
    fun `closed drawer stays hidden on every route`() {
        assertFalse(shouldShowMorePanel(moreOpen = false, route = MobileRoute.Root))
        assertFalse(shouldShowMorePanel(moreOpen = false, route = MobileRoute.Profile))
    }

    @Test
    fun `requested drawer restores without exposing route pop motion`() {
        assertTrue(shouldRestoreMorePanelAtomically(moreOpen = true))
        assertFalse(shouldRestoreMorePanelAtomically(moreOpen = false))
    }

    @Test
    fun `drawer uses its own subtle light surface and preserves dark surface`() {
        assertEquals(
            Color(0xFFFAFAFA),
            mobileDrawerContainerColor(AppearanceTheme()),
        )
        val dark = AppearanceTheme().withDarkAppearance(true)
        assertEquals(dark.mobileSurface, mobileDrawerContainerColor(dark))
    }
}
