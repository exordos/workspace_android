package ru.genesiscorporation.workspace.beta.modules.login

internal object LoginErrorClassifier {
    private val otpChallengePattern =
        Regex("""\b(?:otp|totp|one[-\s]?time|2fa|mfa)\b""", RegexOption.IGNORE_CASE)
    private val invalidClientPattern =
        Regex(
            """"(?:message|detail|description|error_description|error|msg)"\s*:\s*"invalid_client"""",
            RegexOption.IGNORE_CASE,
        )

    fun isOtpChallenge(
        statusCode: String,
        responseBody: String,
        otpProvided: Boolean,
    ): Boolean {
        if (statusCode != "401" || otpProvided) {
            return false
        }
        return otpChallengePattern.containsMatchIn(responseBody) ||
            invalidClientPattern.containsMatchIn(responseBody)
    }

    fun publicMessage(
        statusCode: String,
        responseBody: String,
        otpProvided: Boolean,
    ): String =
        when {
            statusCode == "REQUEST_FAILED" ->
                "Не удалось подключиться к серверу. Проверьте соединение"

            statusCode == "401" && otpProvided ->
                "Неверный код OTP. Проверьте код в приложении-аутентификаторе"

            statusCode == "401" ->
                "Неверное имя пользователя или пароль"

            responseBody.contains("timeout", ignoreCase = true) ->
                "Сервер не ответил вовремя. Попробуйте ещё раз"

            else ->
                "Не удалось выполнить вход. Попробуйте ещё раз"
        }
}
