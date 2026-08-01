package ru.genesiscorporation.workspace.beta.modules.login

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

internal class LoginProcessStateViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    internal val processState = LoginProcessState(savedStateHandle)
}

internal val LocalLoginProcessState = staticCompositionLocalOf<LoginProcessStateViewModel> {
    error("Login process state is not available")
}

internal enum class LoginPhase {
    CREDENTIALS,
    AUTHENTICATING,
    OTP,
    PROJECT,
}

internal data class RestoredLoginProcessState(
    val login: String,
    val interrupted: Boolean,
)

/**
 * Persists only the non-secret portion of the login flow in Activity saved state.
 * Passwords, OTP values and tokens deliberately remain in ViewModel memory.
 */
class LoginProcessState(
    private val savedStateHandle: SavedStateHandle,
) {
    internal fun restore(): RestoredLoginProcessState {
        val storedPhase = savedStateHandle.get<String>(PHASE_KEY)
        val restoredPhase = storedPhase?.let { value ->
            LoginPhase.entries.firstOrNull { it.name == value }
        }
        val login = sanitizeLogin(savedStateHandle.get<String>(LOGIN_KEY).orEmpty())
        if (login.isBlank()) {
            savedStateHandle.remove<String>(LOGIN_KEY)
        } else {
            savedStateHandle[LOGIN_KEY] = login
        }
        savedStateHandle[PHASE_KEY] = LoginPhase.CREDENTIALS.name
        return RestoredLoginProcessState(
            login = login,
            interrupted = storedPhase != null && restoredPhase != LoginPhase.CREDENTIALS,
        )
    }

    internal fun updateLogin(login: String) {
        val safeLogin = sanitizeLogin(login)
        if (safeLogin.isBlank()) {
            savedStateHandle.remove<String>(LOGIN_KEY)
        } else {
            savedStateHandle[LOGIN_KEY] = safeLogin
        }
    }

    internal fun markPhase(phase: LoginPhase) {
        savedStateHandle[PHASE_KEY] = phase.name
    }

    internal fun clear() {
        savedStateHandle.remove<String>(LOGIN_KEY)
        savedStateHandle.remove<String>(PHASE_KEY)
    }

    private fun sanitizeLogin(login: String): String =
        login
            .filterNot(Char::isISOControl)
            .take(MAX_RESTORED_LOGIN_CHARS)

    private companion object {
        const val LOGIN_KEY = "login.safe_login"
        const val PHASE_KEY = "login.phase"
        const val MAX_RESTORED_LOGIN_CHARS = 320
    }
}
