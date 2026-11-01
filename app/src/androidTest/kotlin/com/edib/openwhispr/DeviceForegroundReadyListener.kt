package com.edib.openwhispr

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Redmi foreground workaround completes before ActivityScenario owns an Activity. */
class DeviceForegroundReadyListener : RunListener() {
    override fun testRunStarted(description: Description) {
        val name = InstrumentationRegistry.getArguments().getString("runnerForegroundReadyFile")
        require(name != null && name.matches(Regex("foreground-ready-[0-9a-f]{32}\\.tmp")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = File(instrumentation.targetContext.cacheDir, name)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "com.edib.openwhispr: waiting for runner foreground\n")
        })
        val deadline = SystemClock.uptimeMillis() + 45_000
        while (!ready.isFile && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
        check(ready.isFile) { "Runner must complete its foreground launch before Activity tests" }
    }
}
