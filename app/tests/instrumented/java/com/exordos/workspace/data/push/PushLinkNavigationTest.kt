package com.exordos.workspace.data.push

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.exordos.workspace.R
import com.exordos.workspace.ApplicationSwitcher
import com.exordos.workspace.ChatNavigation
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.UserState
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.ui.theme.WokspaceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PushLinkNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var link: String
    private val pending = mutableStateOf<String?>(null)
    private val mounted = mutableStateOf(false)
    private val handled = AtomicInteger()
    private val pushLookups = AtomicInteger()
    private val owner = UUID.randomUUID().toString()
    private val stream = UUID.randomUUID().toString()
    private val topic = UUID.randomUUID().toString()
    private val message = UUID.randomUUID().toString()

    @Before fun prepare() = runBlocking {
        assertFalse(context.resources.getBoolean(R.bool.push_enabled))
        server = LoopbackWorkspaceServer { request ->
            // Keep /me owner verification, without starting unrelated repository bootstrap jobs.
            if (request.path.contains("/message_reactions/"))
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 503)
            val body = when {
                request.path.endsWith("/me/") ->
                    """{"username":"cassi-fixture","uuid":"$owner","status":"active","avatar":""}"""
                request.path.endsWith("/streams/$stream") -> {
                    pushLookups.incrementAndGet()
                    """{"uuid":"$stream","name":"Old push target","unread_count":0,"active_unread_count":0,"passive_unread_count":0,"updated_at":"2026-11-01T00:00:00Z","private":false,"color":0,"notification_mode":"all_messages"}"""
                }
                request.path.endsWith("/stream_topics/") ->
                    """[{"uuid":"$topic","stream_uuid":"$stream","name":"Push topic","color":0,"updated_at":"2026-11-01T00:00:00Z","unread_count":0,"active_unread_count":0,"is_done":false,"is_default":true,"notification_mode":"default"}]"""
                request.path.endsWith("/messages/") ->
                    """[{"uuid":"$message","stream_uuid":"$stream","topic_uuid":"$topic","user_uuid":"$owner","author_uuid":"$owner","created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z","payload":{"kind":"markdown","content":"Old push message"},"is_own":false,"read":true,"reactions":{}}]"""
                request.path.endsWith("/server_settings/") ->
                    """{"email_auth_enabled":true,"realm_name":"CASSI fixture","realm_url":"","realm_icon":"","meet_url":""}"""
                request.path.endsWith("/epoch/") -> """{"epoch_version":1,"epoch_generation":"cassi-fixture"}"""
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(body)
        }
        account = WorkspaceTestAccount.create(server.url)
        withTimeout(8_000) { account.repo.loadUserInfo() }
        assertNotNull(confirmedAccountContext(account.api, account.repo))
        val registration = PushKeyStore(context).getOrCreate(account.config.id,
            UUID.randomUUID().toString(), requireNotNull(account.config.projectUuid), owner)
        val route = PushRoute(UUID.randomUUID().toString(), registration.serverId,
            registration.organizationUuid, registration.projectUuid, owner, stream, topic, message)
        PushInbox(context).accept(route, System.currentTimeMillis())
        link = "workspace-push/${route.identity}"
        assertEquals(route, WorkspacePushNotifications(context).resolve(link))
    }

    @After fun close() = runBlocking {
        if (mounted.value) {
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
        }
        if (::account.isInitialized) {
            WorkspacePushNotifications(context).cancelServer(account.config.id)
            PushKeyStore(context).remove(account.config.id)
            account.close()
        }
        if (::server.isInitialized) server.close()
    }

    @Test fun coldPushLinkOpensMessageWhilePushDeliveryIsDisabled() {
        pending.value = link
        render(chatOnly = false)
        assertLinkedMessageOpens()
    }

    @Test fun warmPushLinkOpensMessageWhilePushDeliveryIsDisabled() {
        render(chatOnly = true)
        compose.onNodeWithText("Мессенджер").assertIsDisplayed()
        compose.runOnIdle { pending.value = link }
        assertLinkedMessageOpens()
    }

    private fun render(chatOnly: Boolean) {
        mounted.value = true
        compose.setContent {
            WokspaceTheme {
                if (mounted.value) CompositionLocalProvider(UserState provides account.user, LocalBottomBarVisible provides {}) {
                    val clear = { handled.incrementAndGet(); pending.value = null }
                    if (chatOnly) ChatNavigation(account.api, account.events, pending.value, clear)
                    else ApplicationSwitcher(account.api, account.events, pending.value, clear)
                }
            }
        }
    }

    private fun assertLinkedMessageOpens() {
        compose.waitUntil(8_000) { handled.get() == 1 }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        onView(withText("Old push message")).check(matches(isDisplayed()))
        compose.runOnIdle {
            assertEquals(null, pending.value)
            assertTrue("The valid link must fetch its target", pushLookups.get() > 0)
        }
    }
}
