package ru.genesiscorporation.workspace.beta.modules.activity

enum class MyActivityDestination(val title: String) {
    INBOX("Входящие"),
    STARRED("Избранное"),
    PINNED("Отмеченные сообщения"),
    MENTIONS("Упоминания"),
    REACTIONS("Реакции"),
    DRAFTS("Черновики"),
    FEED("Лента"),
}

internal fun supportedMyActivityDestinations(
    query: String,
): List<MyActivityDestination> {
    val normalizedQuery = query.trim().lowercase()
    return MyActivityDestination.entries.filter { destination ->
        normalizedQuery.isEmpty() ||
            destination.title.lowercase().contains(normalizedQuery)
    }
}
