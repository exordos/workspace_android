package com.exordos.workspace

import android.content.ComponentName
import android.content.Intent
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.exordos.workspace.modules.chatdialog.ForwardToWorkspaceActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PackageRenameCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun installedIdentityAndSourceNamespaceAreExordos() {
        assertEquals("com.exordos.workspace", context.packageName)
        assertEquals("com.exordos.workspace.WorkspaceApplication", context.applicationContext.javaClass.name)
        assertEquals("com.exordos.workspace", R::class.java.`package`!!.name)
        val launchers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        assertEquals(1, launchers.size)
        assertEquals(MainActivity::class.java.name, launchers.single().activityInfo.name)
    }

    @Test fun currentLauncherAndForwardingActivityTargetsOpen() {
        val main = ComponentName(context.packageName, MainActivity::class.java.name)
        val info = context.packageManager.getActivityInfo(main, 0)
        assertEquals(MainActivity::class.java.name, info.name)
        val forward = context.packageManager.getActivityInfo(
            ComponentName(context, ForwardToWorkspaceActivity::class.java), 0)
        assertEquals(ForwardToWorkspaceActivity::class.java.name, forward.name)
        assertFalse(forward.exported)
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_MAIN).setComponent(main)).use {
            it.onActivity { activity -> assertEquals(MainActivity::class.java, activity.javaClass) }
        }
    }

    @Test fun savedTypedBackStackRestoresWithCurrentDestinations() {
        instrumentation.runOnMainSync {
            fun controller() = NavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            val before = controller().apply {
                graph = createGraph(startDestination = HomeFlow.HomeBase) {
                    composable<HomeFlow.HomeBase> { }
                    composable<ChatFlow.ChatTopic> { }
                }
                navigate(ChatFlow.ChatTopic("CASSI saved topic", "stream"))
            }
            val state = requireNotNull(before.saveState())
            val restored = controller().apply {
                restoreState(state)
                graph = createGraph(startDestination = HomeFlow.HomeBase) {
                    composable<HomeFlow.HomeBase> { }
                    composable<ChatFlow.ChatTopic> { }
                }
            }
            assertEquals(ChatFlow.ChatTopic("CASSI saved topic", "stream"),
                restored.currentBackStackEntry!!.toRoute<ChatFlow.ChatTopic>())
        }
    }
}
