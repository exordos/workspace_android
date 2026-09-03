package ru.genesiscorporation.workspace.beta.modules.mail

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository

class MailViewModel(
    val eventsRepository: EventsRepository
): ViewModel()