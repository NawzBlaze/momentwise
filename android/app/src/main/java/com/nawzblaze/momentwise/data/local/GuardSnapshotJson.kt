package com.nawzblaze.momentwise.data.local

import com.nawzblaze.momentwise.intervention.GuardSnapshot
import org.json.JSONObject

/** Small JSON mapping for the one active guard session. Corrupt input becomes an empty snapshot. */
internal object GuardSnapshotJson {

    fun encode(snapshot: GuardSnapshot): String {
        val dismissals = JSONObject()
        snapshot.dismissals.forEach { (pkg, date) -> dismissals.put(pkg, date) }
        return JSONObject().apply {
            putNullable("activePackage", snapshot.activePackage)
            put("phase", snapshot.phase.name)
            putNullable("nextReminderAtMillis", snapshot.nextReminderAtMillis)
            putNullable("reminderPresentedFor", snapshot.reminderPresentedFor)
            putNullable("openingHandledForPackage", snapshot.openingHandledForPackage)
            put("dismissals", dismissals)
            put("sessionDroppedOnBoot", snapshot.sessionDroppedOnBoot)
        }.toString()
    }

    fun decode(raw: String?): GuardSnapshot {
        if (raw.isNullOrBlank()) return GuardSnapshot()
        return try {
            val json = JSONObject(raw)
            val dismissalsJson = json.optJSONObject("dismissals")
            val dismissals = mutableMapOf<String, String>()
            if (dismissalsJson != null) {
                val keys = dismissalsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = dismissalsJson.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) dismissals[key] = value
                }
            }
            GuardSnapshot(
                activePackage = json.optString("activePackage").ifBlank { null },
                phase = runCatching { GuardSnapshot.Phase.valueOf(json.optString("phase")) }
                    .getOrDefault(GuardSnapshot.Phase.NONE),
                nextReminderAtMillis = json.optNullableLong("nextReminderAtMillis"),
                reminderPresentedFor = json.optNullableLong("reminderPresentedFor"),
                openingHandledForPackage = json.optString("openingHandledForPackage").ifBlank { null },
                dismissals = dismissals,
                sessionDroppedOnBoot = json.optBoolean("sessionDroppedOnBoot", false),
            )
        } catch (_: Exception) {
            GuardSnapshot()
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, NULL) else put(key, value)
    }

    private fun JSONObject.putNullable(key: String, value: Long?) {
        if (value == null) put(key, NULL) else put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
