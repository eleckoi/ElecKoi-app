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
    fun `drawer stays requested while sidebar destinations temporarily hide it`() {
        assertTrue(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Root))
        assertFalse(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Profile))
        assertFalse(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Settings))
        assertFalse(shouldShowMorePanel(moreOpen = true, route = MobileRoute.AppUpdate))
        assertTrue(shouldShowMorePanel(moreOpen = true, route = MobileRoute.Root))
    }

    @Test
    fun `closed drawer stays hidden on every route`() {
        assertFalse(shouldShowMorePanel(moreOpen = false, route = MobileRoute.Root))
        assertFalse(shouldShowMorePanel(moreOpen = false, route = MobileRoute.Profile))
    }

    @Test
    fun `sidebar destinations use the drawer fade only at the root boundary`() {
        assertTrue(
            shouldUseMorePanelRouteTransition(
                moreOpen = true,
                fromContentKey = MobileRoute.Root.toString(),
                toContentKey = MobileRoute.Settings.toString(),
            ),
        )
        assertTrue(
            shouldUseMorePanelRouteTransition(
                moreOpen = true,
                fromContentKey = MobileRoute.AppUpdate.toString(),
                toContentKey = MobileRoute.Root.toString(),
            ),
        )
        assertFalse(
            shouldUseMorePanelRouteTransition(
                moreOpen = true,
                fromContentKey = MobileRoute.Settings.toString(),
                toContentKey = MobileRoute.Profile.toString(),
            ),
        )
        assertFalse(
            shouldUseMorePanelRouteTransition(
                moreOpen = false,
                fromContentKey = MobileRoute.Root.toString(),
                toContentKey = MobileRoute.Settings.toString(),
            ),
        )
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
