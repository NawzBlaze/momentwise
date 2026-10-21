package com.nawzblaze.momentwise.intervention

/**
 * A pause overlay belongs to one guarded app, not to the launcher or the
 * device's home screen. A temporarily hidden opening remains pending for one
 * short return window; this is monotonic time, not wall-clock time.
 */
object PauseAttachmentPolicy {
    const val RETURN_GRACE_MILLIS = 60_000L

    fun belongsOver(targetPackage: String?, foregroundPackage: String?): Boolean =
        !targetPackage.isNullOrBlank() && targetPackage == foregroundPackage

    fun shouldResume(
        targetPackage: String?,
        foregroundPackage: String?,
        suspendedAtElapsed: Long,
        nowElapsed: Long,
    ): Boolean =
        belongsOver(targetPackage, foregroundPackage) &&
            nowElapsed >= suspendedAtElapsed &&
            nowElapsed - suspendedAtElapsed < RETURN_GRACE_MILLIS

    fun graceExpired(suspendedAtElapsed: Long, nowElapsed: Long): Boolean =
        nowElapsed >= suspendedAtElapsed &&
            nowElapsed - suspendedAtElapsed >= RETURN_GRACE_MILLIS
}
