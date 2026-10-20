package com.nawzblaze.momentwise.data.local

import com.nawzblaze.momentwise.intervention.AppUse
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
            putNullable("quitSuppressedPackage", snapshot.quitSuppressedPackage)
            put("clearedWithoutChoice", snapshot.clearedWithoutChoice)
            put("visitId", snapshot.visitId)
            putNullable("reminderHoldUntilMillis", snapshot.reminderHoldUntilMillis)
            putNullable("backgroundedAtElapsed", snapshot.backgroundedAtElapsed)
            put("useByPackage", encodeUse(snapshot.useByPackage))
        }.toString()
    }

    private fun encodeUse(use: Map<String, AppUse>): JSONObject {
        val json = JSONObject()
        use.forEach { (pkg, clock) ->
            json.put(
                pkg,
                JSONObject()
                    .put("accumulatedMillis", clock.accumulatedMillis)
                    .put("budgetMillis", clock.budgetMillis)
                    .put("phase", clock.phase.name)
                    .put("reminderShown", clock.reminderShown),
            )
        }
        return json
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
                quitSuppressedPackage = json.optString("quitSuppressedPackage").ifBlank { null },
                clearedWithoutChoice = json.optBoolean("clearedWithoutChoice", false),
                visitId = json.optLong("visitId", 0L),
                reminderHoldUntilMillis = json.optNullableLong("reminderHoldUntilMillis"),
                useByPackage = decodeUse(json.optJSONObject("useByPackage")),
                backgroundedAtElapsed = json.optNullableLong("backgroundedAtElapsed"),
            )
        } catch (_: Exception) {
            GuardSnapshot()
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.putNullable(key: String, value: Long?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun decodeUse(json: JSONObject?): Map<String, AppUse> {
        if (json == null) return emptyMap()
        val use = mutableMapOf<String, AppUse>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue
            if (key.isBlank()) continue
            val phase = runCatching { AppUse.Phase.valueOf(item.optString("phase")) }
                .getOrDefault(AppUse.Phase.IDLE)
            use[key] = AppUse(
                accumulatedMillis = item.optLong("accumulatedMillis").coerceAtLeast(0),
                budgetMillis = item.optLong("budgetMillis").coerceAtLeast(0),
                phase = phase,
                anchorElapsed = null,
                reminderShown = item.optBoolean("reminderShown", false),
            )
        }
        return use
    }
}
