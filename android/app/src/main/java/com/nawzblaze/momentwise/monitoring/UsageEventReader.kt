package com.nawzblaze.momentwise.monitoring

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import com.nawzblaze.momentwise.intervention.ForegroundEvent

/**
 * Reads resume events only. `ACTIVITY_RESUMED` (API 29+) and `MOVE_TO_FOREGROUND`
 * are the same numeric value on current platforms; both are checked so the
 * older constant is still handled while minSdk is 26.
 */
object UsageEventReader {

    @SuppressLint("MissingPermission") // Caller checks AppOps for OPSTR_GET_USAGE_STATS. This is not a runtime permission.
    fun read(usageStats: UsageStatsManager, fromMillis: Long, toMillis: Long): List<ForegroundEvent> {
        if (toMillis <= fromMillis) return emptyList()
        val events = usageStats.queryEvents(fromMillis, toMillis) ?: return emptyList()
        val event = UsageEvents.Event()
        val out = ArrayList<ForegroundEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!isResume(event.eventType)) continue
            val packageName = event.packageName ?: continue
            if (packageName.isBlank()) continue
            out += ForegroundEvent(packageName, event.timeStamp, event.className)
        }
        return out
    }

    private fun isResume(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        return Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED
    }
}
