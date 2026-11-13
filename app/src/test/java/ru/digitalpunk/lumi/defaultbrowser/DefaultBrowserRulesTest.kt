package ru.digitalpunk.lumi.defaultbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultBrowserRulesTest {
    @Test fun theRoleIsRequestedOnlyFromApi29WhenAvailableNotHeldAndRefusedLessThanTwice() {
        assertEquals(DefaultStep.SystemSettings, defaultBrowserStep(26, roleAvailable = true, roleHeld = false, refusals = 0))
        assertEquals(DefaultStep.SystemSettings, defaultBrowserStep(28, roleAvailable = true, roleHeld = false, refusals = 0))
        assertEquals(DefaultStep.RoleRequest, defaultBrowserStep(29, roleAvailable = true, roleHeld = false, refusals = 0))
        assertEquals(DefaultStep.RoleRequest, defaultBrowserStep(37, roleAvailable = true, roleHeld = false, refusals = 1))
        assertEquals(DefaultStep.SystemSettings, defaultBrowserStep(37, roleAvailable = true, roleHeld = false, refusals = 2))
        assertEquals(DefaultStep.SystemSettings, defaultBrowserStep(37, roleAvailable = false, roleHeld = false, refusals = 0))
        assertEquals(DefaultStep.SystemSettings, defaultBrowserStep(37, roleAvailable = true, roleHeld = true, refusals = 0))
    }

    @Test fun theRoleWinsOverTheResolverWhichIsTheFallback() {
        assertFalse(isLumiDefault(roleHeld = false, resolvedPackage = "own", own = "own"))
        assertTrue(isLumiDefault(roleHeld = true, resolvedPackage = "other", own = "own"))
        assertTrue(isLumiDefault(roleHeld = null, resolvedPackage = "own", own = "own"))
        assertFalse(isLumiDefault(roleHeld = null, resolvedPackage = "android", own = "own"))
        assertFalse(isLumiDefault(roleHeld = null, resolvedPackage = null, own = "own"))
    }

    @Test fun theCardShowsFromTheThirdLaunchUntilDismissedAndNeverForTheDefaultOrInAPrivateTab() {
        assertFalse(showDefaultCard(2, dismissed = false, isDefault = false, inPrivateTab = false))
        assertTrue(showDefaultCard(3, dismissed = false, isDefault = false, inPrivateTab = false))
        assertTrue(showDefaultCard(100, dismissed = false, isDefault = false, inPrivateTab = false))
        assertFalse(showDefaultCard(3, dismissed = true, isDefault = false, inPrivateTab = false))
        assertFalse(showDefaultCard(3, dismissed = false, isDefault = true, inPrivateTab = false))
        assertFalse(showDefaultCard(3, dismissed = false, isDefault = false, inPrivateTab = true))
        assertFalse(showDefaultCard(-3, dismissed = false, isDefault = false, inPrivateTab = false))
    }

    @Test fun onlyAFreshLauncherStartCounts() {
        val launcher = setOf("android.intent.category.LAUNCHER")
        assertTrue(launcherStart(true, "android.intent.action.MAIN", launcher))
        assertFalse(launcherStart(false, "android.intent.action.MAIN", launcher))
        assertFalse(launcherStart(true, "android.intent.action.VIEW", setOf("android.intent.category.BROWSABLE")))
        assertFalse(launcherStart(true, "ru.digitalpunk.lumi.SHOW_DOWNLOADS", null))
        assertFalse(launcherStart(true, "android.intent.action.MAIN", null))
    }

    @Test fun aRefusalFasterThan400MsHadNoDialog() {
        assertTrue(refusedWithoutDialog(399))
        assertFalse(refusedWithoutDialog(400))
    }
}
