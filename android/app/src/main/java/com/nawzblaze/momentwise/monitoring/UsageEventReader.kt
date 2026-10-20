package com.nawzblaze.momentwise.monitoring

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import com.nawzblaze.momentwise.intervention.ForegroundEvent

/**
 * Reads resume and stop events. `ACTIVITY_RESUMED` (API 29+) and
 * `MOVE_TO_FOREGROUND` are the same numeric value on current platforms; both
 * are checked so the older constant is still handled while minSdk is 26.
 *
 * `ACTIVITY_STOPPED` is read on API 29+. A pause (`ACTIVITY_PAUSED` /
 * `MOVE_TO_BACKGROUND`) is not a leave: guarded apps emit it on ordinary
 * navigation. A stop that is not resumed in the same window is a leave.
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
            val kind = kindOf(event.eventType) ?: continue
            val packageName = event.packageName ?: continue
            if (packageName.isBlank()) continue
            out += ForegroundEvent(packageName, event.timeStamp, event.className, kind)
        }
        return out
    }

    private fun kindOf(type: Int): ForegroundEvent.Kind? {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return ForegroundEvent.Kind.RESUMED
        if (Build.VERSION.SDK_INT < 29) return null
        return when (type) {
            UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEvent.Kind.RESUMED
            UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEvent.Kind.STOPPED
            else -> null
        }
    }
}
