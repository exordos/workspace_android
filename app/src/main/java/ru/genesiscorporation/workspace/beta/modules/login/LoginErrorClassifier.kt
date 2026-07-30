package ru.genesiscorporation.workspace.beta.modules.login

internal object LoginErrorClassifier {
    private val otpChallengePattern =
        Regex("""\b(?:otp|totp|one[-\s]?time|2fa|mfa)\b""", RegexOption.IGNORE_CASE)

    fun isOtpChallenge(
        httpStatus: Int?,
        errorCode: String,
        safeMessage: String,
        otpProvided: Boolean,
    ): Boolean {
        if (otpProvided || httpStatus !in setOf(400, 401, 403)) {
            return false
        }
        return otpChallengePattern.containsMatchIn(errorCode) ||
            otpChallengePattern.containsMatchIn(safeMessage) ||
            (httpStatus == 401 && errorCode.equals("invalid_client", ignoreCase = true))
    }

    fun publicMessage(
        httpStatus: Int?,
        errorCode: String,
        safeMessage: String,
        otpProvided: Boolean,
    ): String =
        when {
            errorCode == "REQUEST_FAILED" ->
                "Не удалось подключиться к серверу. Проверьте соединение"

            otpProvided && (
                otpChallengePattern.containsMatchIn(errorCode) ||
                    otpChallengePattern.containsMatchIn(safeMessage)
                ) ->
                "Неверный код OTP. Проверьте код в приложении-аутентификаторе"

            httpStatus == 401 ->
                "Неверное имя пользователя или пароль"

            safeMessage.contains("timeout", ignoreCase = true) ->
                "Сервер не ответил вовремя. Попробуйте ещё раз"

            else ->
                "Не удалось выполнить вход. Попробуйте ещё раз"
        }
}
