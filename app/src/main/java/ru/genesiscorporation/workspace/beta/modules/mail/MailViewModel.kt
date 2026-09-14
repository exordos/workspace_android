package ru.genesiscorporation.workspace.beta.modules.mail

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore

class MailViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel()