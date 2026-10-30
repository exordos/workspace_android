package com.exordos.workspace

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateReceiverTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val tasks get() = context.getSystemService(ActivityManager::class.java).appTasks

    @Test fun updateKeepsResolvableActivityTasks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var taskId = -1
            scenario.onActivity { taskId = it.taskId }
            assertTrue(tasks.any { it.taskInfo.taskId == taskId })
            AppUpdateReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
            instrumentation.waitForIdleSync()
            assertTrue(tasks.any { it.taskInfo.taskId == taskId })
        }
    }

    @Test fun updateRemovesTasksWhoseActivityNoLongerResolves() {
        val component = ComponentName(context, MainActivity::class.java)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var taskId = -1
            scenario.onActivity { taskId = it.taskId }
            context.packageManager.setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            try {
                assertTrue("The stale task must still exist before cleanup",
                    tasks.any { it.taskInfo.taskId == taskId })
                assertTrue(context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).setComponent(component), 0) == null)
                AppUpdateReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
                instrumentation.waitForIdleSync()
                assertFalse(tasks.any { it.taskInfo.taskId == taskId })
            } finally {
                context.packageManager.setComponentEnabledSetting(component,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
