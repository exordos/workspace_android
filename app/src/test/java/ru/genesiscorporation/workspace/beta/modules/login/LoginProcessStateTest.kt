package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginProcessStateTest {

    @Test
    fun `credentials recreation restores only bounded non-secret login`() {
        val firstHandle = SavedStateHandle()
        val firstState = LoginProcessState(firstHandle)
        firstState.restore()
        firstState.updateLogin("cassi\n@example.invalid" + "x".repeat(400))
        firstState.markPhase(LoginPhase.CREDENTIALS)

        val recreatedHandle = SavedStateHandle(firstHandle.toMap())
        val restored = LoginProcessState(recreatedHandle).restore()

        assertFalse(restored.interrupted)
        assertEquals(320, restored.login.length)
        assertFalse(restored.login.contains('\n'))
        assertEquals(
            setOf("login.safe_login", "login.phase"),
            recreatedHandle.keys(),
        )
    }

    @Test
    fun `every secret-bearing phase fails closed after process recreation`() {
        listOf(
            LoginPhase.AUTHENTICATING,
            LoginPhase.OTP,
            LoginPhase.PROJECT,
        ).forEach { phase ->
            val firstHandle = SavedStateHandle()
            val firstState = LoginProcessState(firstHandle)
            firstState.restore()
            firstState.updateLogin("cassi@example.invalid")
            firstState.markPhase(phase)

            val recreatedHandle = SavedStateHandle(firstHandle.toMap())
            val restored = LoginProcessState(recreatedHandle).restore()

            assertTrue("Expected $phase to be interrupted", restored.interrupted)
            assertEquals("cassi@example.invalid", restored.login)
            assertEquals(phase.name, recreatedHandle["login.phase"])
            assertTrue(LoginProcessState(recreatedHandle).restore().interrupted)
        }
    }

    @Test
    fun `unknown persisted phase fails closed`() {
        val handle = SavedStateHandle(
            mapOf(
                "login.safe_login" to "cassi@example.invalid",
                "login.phase" to "FUTURE_SECRET_STEP",
            ),
        )

        val restored = LoginProcessState(handle).restore()

        assertTrue(restored.interrupted)
        assertEquals("FUTURE_SECRET_STEP", handle["login.phase"])
        assertTrue(LoginProcessState(handle).restore().interrupted)
    }

    @Test
    fun `successful login clears all persisted login state`() {
        val handle = SavedStateHandle()
        val state = LoginProcessState(handle)
        state.restore()
        state.updateLogin("cassi@example.invalid")
        state.markPhase(LoginPhase.PROJECT)

        state.clear()

        assertTrue(handle.keys().isEmpty())
    }

    private fun SavedStateHandle.toMap(): Map<String, Any?> =
        keys().associateWith { key -> get<Any?>(key) }
}
