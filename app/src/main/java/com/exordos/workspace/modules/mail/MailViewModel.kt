package com.exordos.workspace.modules.mail

import androidx.lifecycle.ViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore

class MailViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel()
