package com.nawzblaze.momentwise.monitoring

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlin.concurrent.thread

/**
 * Debug-build notes only. No package lists, no notice text, no private content.
 * A release build writes nothing.
 */
internal object DebugNotes {
    private val lock = Any()

    fun append(context: Context, line: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val app = context.applicationContext
        thread(name = "debug-note") {
            synchronized(lock) {
                try {
                    app.openFileOutput("debug-status.txt", Context.MODE_APPEND).bufferedWriter().use {
                        it.appendLine(line)
                    }
                } catch (_: Exception) {
                    // Diagnosis only. A missing note must not stop guarding.
                }
            }
        }
    }
}
