package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseAttachmentPolicyTest {
    @Test
    fun overlayIsAttachedOnlyToItsExactTargetPackage() {
        assertTrue(PauseAttachmentPolicy.belongsOver("com.instagram.android", "com.instagram.android"))
        assertFalse(PauseAttachmentPolicy.belongsOver("com.instagram.android", "com.android.launcher"))
        assertFalse(PauseAttachmentPolicy.belongsOver("com.instagram.android", "com.nawzblaze.momentwise"))
        assertFalse(PauseAttachmentPolicy.belongsOver("com.instagram.android", null))
        assertFalse(PauseAttachmentPolicy.belongsOver(null, "com.instagram.android"))
    }

    @Test
    fun unansweredPauseResumesOnlyInsideTheSixtySecondWindow() {
        val hiddenAt = 10_000L
        assertTrue(
            PauseAttachmentPolicy.shouldResume(
                "com.instagram.android",
                "com.instagram.android",
                hiddenAt,
                hiddenAt + 59_999L,
            ),
        )
        assertFalse(
            PauseAttachmentPolicy.shouldResume(
                "com.instagram.android",
                "com.instagram.android",
                hiddenAt,
                hiddenAt + PauseAttachmentPolicy.RETURN_GRACE_MILLIS,
            ),
        )
        assertFalse(
            PauseAttachmentPolicy.shouldResume(
                "com.instagram.android",
                "com.android.launcher",
                hiddenAt,
                hiddenAt + 1_000L,
            ),
        )
        assertFalse(
            PauseAttachmentPolicy.shouldResume(
                "com.instagram.android",
                "com.instagram.android",
                hiddenAt,
                hiddenAt - 1L,
            ),
        )
    }

    @Test
    fun pendingPauseExpiresAtTheGraceBoundary() {
        val hiddenAt = 42_000L
        assertFalse(PauseAttachmentPolicy.graceExpired(hiddenAt, hiddenAt + 59_999L))
        assertTrue(
            PauseAttachmentPolicy.graceExpired(
                hiddenAt,
                hiddenAt + PauseAttachmentPolicy.RETURN_GRACE_MILLIS,
            ),
        )
        assertFalse(PauseAttachmentPolicy.graceExpired(hiddenAt, hiddenAt - 1L))
    }
}
