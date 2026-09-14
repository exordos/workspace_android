package ru.genesiscorporation.workspace.beta.modules.calendar

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore

class CalendarViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel()