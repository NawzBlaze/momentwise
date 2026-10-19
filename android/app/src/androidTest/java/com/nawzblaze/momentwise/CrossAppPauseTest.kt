package com.nawzblaze.momentwise

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Opens a second package and checks that the system shows the pause notice.
 * This is not a welcome-screen test. It does not claim a physical-device result.
 */
@RunWith(AndroidJUnit4::class)
class CrossAppPauseTest {

    @Test
    fun openingTheFixtureShowsThePauseNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = "com.nawzblaze.momentwise.fixture"

        shell("pm grant $APP android.permission.POST_NOTIFICATIONS")
        shell("appops set $APP GET_USAGE_STATS allow")
        // Leave overlay off so the supported fallback is a notification we can read.
        shell("appops set $APP SYSTEM_ALERT_WINDOW deny")

        val launch = instrumentation.targetContext.packageManager.getLaunchIntentForPackage(APP)
            ?: Intent(Intent.ACTION_MAIN).setClassName(APP, "$APP.MainActivity")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.targetContext.startActivity(launch)
        Thread.sleep(2_000)

        shell(
            "am broadcast -a com.nawzblaze.momentwise.DEBUG_ENABLE_RULE " +
                "-n $APP/.debug.DebugRuleBridge " +
                "--es package $target --es label Fixture",
        )

        assertTrue(
            "Monitoring notification was not posted. dumpsys tail:\n${notificationTail()}",
            waitForNotification("Momentwise is guarding"),
        )

        shell("input keyevent KEYCODE_HOME")
        shell("am start -n $target/.FixtureActivity")

        assertTrue(
            "Opening notice was not delivered. dumpsys tail:\n${notificationTail()}",
            waitForNotification("What brought you here?"),
        )
    }

    private fun waitForNotification(text: String): Boolean {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (notificationDump().contains(text)) return true
            Thread.sleep(1_000)
        }
        return false
    }

    private fun notificationDump(): String =
        shell("sh -c 'dumpsys notification --noredact | grep -F -e \"Momentwise is guarding\" -e \"What brought you here?\" || true'")

    private fun notificationTail(): String = notificationDump().takeLast(1_500)

    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val stream = automation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    companion object {
        private const val APP = "com.nawzblaze.momentwise"
    }
}
