package com.nawzblaze.momentwise.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantPresentationTest {

    @Test
    fun oneControlChangesLabelFromTheGrantNotFromATap() {
        assertEquals("Allow", GrantPresentation.label(allowed = false))
        assertEquals("Allowed ✓", GrantPresentation.label(allowed = true))
        assertFalse(GrantPresentation.separateStatusShown())
    }
}

class GrantSignalsTest {

    @Test
    fun usageIsAllowedOnlyFromAnAndroidGrant() {
        assertTrue(GrantSignals.usageAllowed(GrantSignals.MODE_ALLOWED, selfPermissionGranted = false))
        assertTrue(GrantSignals.usageAllowed(GrantSignals.MODE_DEFAULT, selfPermissionGranted = true))
        assertFalse(GrantSignals.usageAllowed(GrantSignals.MODE_IGNORED, selfPermissionGranted = true))
        assertFalse(GrantSignals.usageAllowed(GrantSignals.MODE_DEFAULT, selfPermissionGranted = false))
        assertFalse(GrantSignals.usageAllowed(null, selfPermissionGranted = true))
    }

    @Test
    fun overlayTrustsEitherSettingsOrAppOps() {
        assertTrue(GrantSignals.overlayAllowed(settingsSaysYes = true, appOpsMode = GrantSignals.MODE_IGNORED))
        assertTrue(GrantSignals.overlayAllowed(settingsSaysYes = false, appOpsMode = GrantSignals.MODE_ALLOWED))
        assertFalse(GrantSignals.overlayAllowed(settingsSaysYes = false, appOpsMode = GrantSignals.MODE_IGNORED))
    }

    @Test
    fun notificationsNeedTheRuntimeGrantAndTheManagerSwitch() {
        assertTrue(
            GrantSignals.notificationsAllowed(
                requiresRuntime = true,
                runtimeGranted = true,
                managerEnabled = true,
            ),
        )
        assertFalse(
            GrantSignals.notificationsAllowed(
                requiresRuntime = true,
                runtimeGranted = false,
                managerEnabled = true,
            ),
        )
        assertFalse(
            GrantSignals.notificationsAllowed(
                requiresRuntime = false,
                runtimeGranted = true,
                managerEnabled = false,
            ),
        )
    }

    @Test
    fun eitherAppOpsReadCanShowTheGrant() {
        assertEquals(
            GrantSignals.MODE_ALLOWED,
            GrantSignals.preferAllowed(GrantSignals.MODE_IGNORED, GrantSignals.MODE_ALLOWED),
        )
        assertEquals(
            GrantSignals.MODE_IGNORED,
            GrantSignals.preferAllowed(GrantSignals.MODE_IGNORED, GrantSignals.MODE_ERRORED),
        )
    }
}
