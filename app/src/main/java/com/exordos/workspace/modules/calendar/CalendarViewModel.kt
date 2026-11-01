package com.exordos.workspace.modules.calendar

import androidx.lifecycle.ViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore

class CalendarViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel()
