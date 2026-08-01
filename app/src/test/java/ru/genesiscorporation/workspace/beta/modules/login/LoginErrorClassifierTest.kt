package ru.genesiscorporation.workspace.beta.modules.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind

class LoginErrorClassifierTest {

    @Test
    fun `recognizes explicit otp challenge`() {
        assertTrue(
            LoginErrorClassifier.isOtpChallenge(
                httpStatus = 401,
                errorCode = "OTPInvalidCodeError",
                safeMessage = "The provided otp code is invalid",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `recognizes legacy invalid client challenge before otp`() {
        assertTrue(
            LoginErrorClassifier.isOtpChallenge(
                httpStatus = 401,
                errorCode = "invalid_client",
                safeMessage = "Client authentication failed",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `does not reopen challenge after an otp attempt`() {
        assertFalse(
            LoginErrorClassifier.isOtpChallenge(
                httpStatus = 401,
                errorCode = "OTPInvalidCodeError",
                safeMessage = "Invalid OTP",
                otpProvided = true,
            ),
        )
    }

    @Test
    fun `does not classify ordinary invalid credentials as otp`() {
        assertFalse(
            LoginErrorClassifier.isOtpChallenge(
                httpStatus = 401,
                errorCode = "invalid_credentials",
                safeMessage = "Invalid credentials",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `maps a rejected otp to an actionable message`() {
        assertEquals(
            "Неверный код OTP. Проверьте код в приложении-аутентификаторе",
            LoginErrorClassifier.publicMessage(
                httpStatus = 401,
                errorCode = "OTPInvalidCodeError",
                errorKind = ApiErrorKind.UNAUTHORIZED,
                safeMessage = "Invalid OTP",
                otpProvided = true,
            ),
        )
    }

    @Test
    fun `maps typed timeout without relying on transport text`() {
        assertEquals(
            "Сервер не ответил вовремя. Попробуйте ещё раз",
            LoginErrorClassifier.publicMessage(
                httpStatus = null,
                errorCode = "REQUEST_FAILED",
                errorKind = ApiErrorKind.TIMEOUT,
                safeMessage = "Operation was cancelled",
                otpProvided = false,
            ),
        )
    }
}
