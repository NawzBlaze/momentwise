package com.nawzblaze.momentwise

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches a second installed package. Overlay granted and overlay denied are
 * separate cases. Neither injects a usage event.
 *
 * Limitations, on purpose: the target is `com.nawzblaze.momentwise.fixture`,
 * not Instagram. A GitHub-hosted emulator pass is not a physical-phone result,
 * not a battery measurement, and not proof the pause cannot be dismissed by
 * the system.
 */
@RunWith(AndroidJUnit4::class)
class CrossAppPauseTest {

    @Test
    fun withoutOverlayTheNotificationDoesNotCoverTheFixture() {
        val target = prepare(overlay = false)
        openFixture(target)

        assertTrue(
            "Opening notice was not delivered as a notification. ${diagnostics()}",
            waitFor {
                val notes = notificationDump()
                notes.contains(PROMPT) && notes.contains("not blocked") &&
                    statusFile().contains("overlay-not-granted")
            },
        )
        val status = statusFile()
        assertFalse(
            "Denied overlay still reported a cover. ${diagnostics()}",
            status.contains("intervention=overlay shown"),
        )
        val top = resumedActivityLine()
        assertTrue(
            "Fixture was not the resumed activity, so this is not the uncovered fallback: $top",
            top.contains(target),
        )
        assertFalse(
            "PauseActivity covered the fixture even though overlay was denied: $top",
            top.contains("PauseActivity"),
        )
    }

    @Test
    fun grantedOverlayShowsAVisibleCover() {
        val target = prepare(overlay = true)
        openFixture(target)

        val shown = waitFor {
            statusFile().contains("intervention=overlay shown") && overlayWindowVisible()
        }
        val intervention = statusFile().lineSequence().filter { it.contains("intervention=") }.joinToString(" | ")
        val watched = statusFile().lineSequence().filter {
            it.contains("seed ") || it.contains("tick ") || it.contains("opened ") ||
                it.contains("showing ") || it.contains("screen off") ||
                it.contains("session reset") || it.contains("sync finished")
        }.joinToString(" | ")
        assertTrue(
            "Visible overlay was not added. intervention=[$intervention] watched=[$watched] windows=${oneLine(windowDump().take(400))}",
            shown,
        )
        assertFalse(
            "Overlay path also left the not-blocked notification up. ${diagnostics()}",
            notificationDump().contains("not blocked"),
        )
    }

    /**
     * Home, then open the fixture again. A no-choice leave must not suppress
     * the next opening. This is an emulator fixture, not a phone.
     */
    @Test
    fun leaveAndReopenShowsTheOpeningAgain() {
        val target = prepare(overlay = true)
        openFixture(target)
        assertTrue(
            "First opening was not visible. ${diagnostics()}",
            waitFor { statusFile().contains("intervention=overlay shown") && overlayWindowVisible() },
        )
        shell("input keyevent KEYCODE_HOME")
        assertTrue(
            "Overlay stayed up after Home. ${diagnostics()}",
            waitFor { !overlayWindowVisible() },
        )
        openFixture(target)
        val shownAgain = waitFor {
            overlayWindowVisible() &&
                statusFile().lineSequence().count { it.contains("intervention=overlay shown") } >= 2
        }
        assertTrue(
            "Second visit did not show the opening. ${diagnostics()}",
            shownAgain,
        )
    }

    /**
     * The card must still be up after several polls. A one-second flash is the
     * reported phone bug. This emulator is not that phone.
     */
    @Test
    fun overlayStaysUpAcrossSeveralPolls() {
        val target = prepare(overlay = true)
        openFixture(target)
        assertTrue(
            "Opening was not visible. ${diagnostics()}",
            waitFor { overlayWindowVisible() },
        )
        Thread.sleep(4_000)
        assertTrue(
            "Overlay disappeared within 4 seconds. ${diagnostics()}",
            overlayWindowVisible(),
        )
        val shows = statusFile().lineSequence().count { it.contains("intervention=overlay shown") }
        shell(
            "am broadcast -p $APP -a com.nawzblaze.momentwise.DEBUG_ENABLE_RULE " +
                "--es target_package $target --ez inject_continue true",
        )
        assertTrue(
            "Continue did not dismiss the overlay. ${diagnostics()}",
            waitFor { !overlayWindowVisible() },
        )
        Thread.sleep(4_000)
        assertFalse(
            "Overlay came back after Continue without a leave. ${diagnostics()}",
            overlayWindowVisible(),
        )
        assertEquals(
            "Continue started another opening. ${diagnostics()}",
            shows,
            statusFile().lineSequence().count { it.contains("intervention=overlay shown") },
        )
    }

    /**
     * Times queryEvents in the app process on this emulator. Not a phone, and
     * not a battery percentage.
     */
    @Test
    fun usageQueryOnThisEmulatorIsRecorded() {
        // Do not force-stop Momentwise. This instrumentation runs in that
        // process, and force-stop kills the test.
        shell("am stopservice $APP/.monitoring.MonitoringService")
        shell("run-as $APP rm -f files/debug-status.txt")
        shell("appops set $APP GET_USAGE_STATS allow")
        shell("cmd appops set $APP GET_USAGE_STATS allow")
        val started = shell(
            "am start -W -n $APP/.debug.DebugRuleActivity --es measure_usage true",
        )
        assertTrue("Measure activity did not start: ${oneLine(started)}", started.contains("ok"))
        // The written line is "USAGE_QUERY samples=… meanMs=…", not "USAGE_QUERY meanMs=".
        val recorded = waitFor {
            val notes = statusFile()
            notes.contains("meanMs=") && !notes.contains("USAGE_QUERY error=")
        }
        val notes = statusFile()
        assertTrue(
            "Usage query was not recorded. start=${oneLine(started)} notes=${oneLine(notes)}",
            recorded && notes.contains("meanMs=") && !notes.contains("USAGE_QUERY error="),
        )
    }

    private fun prepare(overlay: Boolean): String {
        val target = "com.nawzblaze.momentwise.fixture"
        assertNotEquals(APP, target)
        // Do not force-stop Momentwise. Instrumentation runs in that process.
        shell("am stopservice $APP/.monitoring.MonitoringService")
        shell("am force-stop $target")
        // dumpsys keeps the service class name after it has stopped, so that
        // string is not proof it is still running. Do not block the test thread
        // here: a blocked main thread misses startForeground and API 34 kills
        // the process.
        shell("run-as $APP rm -f files/debug-status.txt")
        wakeScreen()

        val grant = shell("pm grant $APP android.permission.POST_NOTIFICATIONS")
        assertFalse(
            "Notification grant failed: ${oneLine(grant)}",
            grant.contains("Exception") || grant.contains("Error"),
        )
        shell("appops set $APP GET_USAGE_STATS allow")
        shell("cmd appops set $APP GET_USAGE_STATS allow")
        val overlayMode = if (overlay) "allow" else "deny"
        shell("appops set $APP SYSTEM_ALERT_WINDOW $overlayMode")
        shell("cmd appops set $APP SYSTEM_ALERT_WINDOW $overlayMode")
        val usage = shell("appops get $APP GET_USAGE_STATS")
        assertTrue("Usage Access was not allowed: $usage", usage.contains("allow"))
        val overlayOps = shell("appops get $APP SYSTEM_ALERT_WINDOW")
        assertTrue("Overlay appop was not $overlayMode: $overlayOps", overlayOps.contains(overlayMode))

        val enable = shell(
            "am start -W -n $APP/.debug.DebugRuleActivity " +
                "--es target_package $target --es label Fixture --es reset_session true",
        )
        assertTrue("Could not enable the fixture rule: $enable", enable.contains("ok"))
        assertTrue(
            "Monitoring notification was not posted. ${diagnostics()}",
            waitFor {
                val dump = notificationDump()
                dump.contains("Momentwise is on") && dump.contains("Watching your selected apps.")
            },
        )
        return target
    }

    private fun openFixture(target: String) {
        wakeScreen()
        shell("input keyevent KEYCODE_HOME")
        val opened = shell("am start -W -n $target/.FixtureActivity")
        assertTrue(
            "Fixture did not open as its own package: $opened",
            opened.contains("$target/.FixtureActivity") || opened.contains(target),
        )
        assertFalse("The start targeted Momentwise itself: $opened", opened.contains("$APP/.MainActivity"))
        assertTrue("Fixture did not open: $opened", opened.contains("ok"))
    }

    /** Emulator idle during an earlier test can turn the panel off and skip UsageStats. */
    private fun wakeScreen() {
        shell("settings put system screen_off_timeout 1800000")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    private fun waitFor(timeoutMillis: Long = 30_000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(1_000)
        }
        return predicate()
    }

    private fun notificationDump(): String = shell("dumpsys notification --noredact")

    private fun windowDump(): String = shell("dumpsys window windows")

    private fun overlayWindowVisible(): Boolean {
        val dump = windowDump()
        if (dump.contains("Momentwise intervention")) return true
        return dump.contains(APP) && (
            dump.contains("APPLICATION_OVERLAY") || dump.contains("ty=2038")
            )
    }

    private fun statusFile(): String = shell("run-as $APP cat files/debug-status.txt")

    private fun resumedActivityLine(): String {
        val dump = shell("dumpsys activity activities")
        return dump.lineSequence()
            .firstOrNull { it.contains("topResumedActivity") || it.contains("mResumedActivity") }
            .orEmpty()
            .ifBlank { oneLine(dump.take(500)) }
    }

    private fun diagnostics(): String {
        val posted = shell("dumpsys package $APP")
            .lineSequence()
            .filter { it.contains("POST_NOTIFICATIONS") || it.contains("SYSTEM_ALERT_WINDOW") }
            .take(4)
            .joinToString(" | ")
        val notes = notificationDump().lineSequence()
            .filter {
                it.contains("Momentwise") || it.contains("momentwise") ||
                    it.contains("not blocked") || it.contains("Is this where") ||
                    it.contains("guarding")
            }
            .take(12)
            .joinToString(" | ")
        return oneLine("status=[${statusFile()}] package=[$posted] notifications=[$notes]")
    }

    private fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").take(700)

    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val APP = "com.nawzblaze.momentwise"
        private const val PROMPT = "Before you scroll"
    }
}
