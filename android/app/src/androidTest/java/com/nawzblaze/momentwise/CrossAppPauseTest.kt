package com.nawzblaze.momentwise

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches a second installed package and reads the opening notice from the
 * system notification dump. Overlay stays denied, so this is the notification
 * fallback, not a full-screen pause, and not an injected usage event.
 *
 * Limitations, on purpose: the target is `com.nawzblaze.momentwise.fixture`,
 * not Instagram or any other real app. A GitHub-hosted emulator pass does not
 * establish physical-device reliability, battery survival, or exact timing.
 */
@RunWith(AndroidJUnit4::class)
class CrossAppPauseTest {

    @Test
    fun openingTheFixtureShowsThePauseNotification() {
        val target = "com.nawzblaze.momentwise.fixture"
        assertNotEquals(APP, target)

        val grant = shell("pm grant $APP android.permission.POST_NOTIFICATIONS")
        assertFalse("Notification grant failed: ${oneLine(grant)}", grant.contains("Exception") || grant.contains("Error"))
        shell("appops set $APP GET_USAGE_STATS allow")
        shell("cmd appops set $APP GET_USAGE_STATS allow")
        // Leave overlay off so the supported fallback is a notification we can read.
        shell("appops set $APP SYSTEM_ALERT_WINDOW deny")
        val usage = shell("appops get $APP GET_USAGE_STATS")
        assertTrue("Usage Access was not allowed: $usage", usage.contains("allow"))

        val enable = shell(
            "am start -W -n $APP/.debug.DebugRuleActivity " +
                "--es target_package $target --es label Fixture",
        )
        assertTrue("Could not enable the fixture rule: $enable", enable.contains("Status: ok") || enable.contains("ok"))

        assertTrue(
            "Monitoring notification was not posted. ${diagnostics()}",
            waitForNotification("Momentwise is guarding"),
        )

        shell("input keyevent KEYCODE_HOME")
        val opened = shell("am start -W -n $target/.FixtureActivity")
        assertTrue(
            "Fixture did not open as its own package: $opened",
            opened.contains("$target/.FixtureActivity") || opened.contains(target),
        )
        assertFalse("The start targeted Momentwise itself: $opened", opened.contains("$APP/.MainActivity"))
        assertTrue("Fixture did not open: $opened", opened.contains("Status: ok") || opened.contains("ok"))

        assertTrue(
            "Opening notice was not delivered. ${diagnostics()}",
            waitForNotification("What brought you here?"),
        )
    }

    private fun waitForNotification(text: String): Boolean {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (notificationDump().contains(text)) return true
            Thread.sleep(1_000)
        }
        return false
    }

    private fun notificationDump(): String {
        val dump = shell("dumpsys notification --noredact")
        val hits = dump.lineSequence()
            .filter {
                it.contains("Momentwise") || it.contains("momentwise") ||
                    it.contains("guarding") || it.contains("brought you") || it.contains("pause is waiting")
            }
            .take(20)
            .joinToString(" | ")
        return if (hits.isNotBlank()) hits else "none len=${dump.length} head=${dump.take(180)}"
    }

    private fun diagnostics(): String {
        val status = shell("run-as $APP cat files/debug-status.txt")
        val posted = shell("dumpsys package $APP")
            .lineSequence()
            .filter { it.contains("POST_NOTIFICATIONS") }
            .take(2)
            .joinToString(" | ")
        return oneLine("status=[$status] permission=[$posted] notifications=[${notificationDump()}]")
    }

    private fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").take(700)

    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val APP = "com.nawzblaze.momentwise"
    }
}
