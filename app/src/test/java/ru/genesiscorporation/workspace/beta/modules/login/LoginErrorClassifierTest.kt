package ru.genesiscorporation.workspace.beta.modules.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorClassifierTest {

    @Test
    fun `recognizes explicit otp challenge`() {
        assertTrue(
            LoginErrorClassifier.isOtpChallenge(
                statusCode = "401",
                responseBody = """{"error_description":"OTP code is required"}""",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `recognizes legacy invalid client challenge before otp`() {
        assertTrue(
            LoginErrorClassifier.isOtpChallenge(
                statusCode = "401",
                responseBody = """{"error":"invalid_client"}""",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `does not reopen challenge after an otp attempt`() {
        assertFalse(
            LoginErrorClassifier.isOtpChallenge(
                statusCode = "401",
                responseBody = """{"detail":"Invalid OTP"}""",
                otpProvided = true,
            ),
        )
    }

    @Test
    fun `does not classify ordinary invalid credentials as otp`() {
        assertFalse(
            LoginErrorClassifier.isOtpChallenge(
                statusCode = "401",
                responseBody = """{"error":"invalid_credentials"}""",
                otpProvided = false,
            ),
        )
    }

    @Test
    fun `maps a rejected otp to an actionable message`() {
        assertEquals(
            "Неверный код OTP. Проверьте код в приложении-аутентификаторе",
            LoginErrorClassifier.publicMessage(
                statusCode = "401",
                responseBody = """{"detail":"Invalid OTP"}""",
                otpProvided = true,
            ),
        )
    }
}
