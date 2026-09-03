package ru.genesiscorporation.workspace.beta.modules.calendar

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository

class CalendarViewModel(
    val eventsRepository: EventsRepository
): ViewModel()