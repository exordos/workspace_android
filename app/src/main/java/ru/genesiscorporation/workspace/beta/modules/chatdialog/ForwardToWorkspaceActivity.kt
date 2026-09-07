package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import ru.genesiscorporation.workspace.beta.R

internal object ForwardShareSessions {
    const val EXTRA_SESSION = "workspace.forward.session"
    private val sessions = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    fun create(): Pair<String, MutableStateFlow<Boolean>> = UUID.randomUUID().toString().let { id ->
        id to MutableStateFlow(false).also { sessions[id] = it }
    }
    fun chooseInternal(id: String?): Boolean = id?.let(sessions::get)?.let { it.value = true; true } ?: false
    fun remove(id: String) { sessions.remove(id) }
}

/** An explicit chooser target; message content stays in the originating authenticated screen. */
class ForwardToWorkspaceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ForwardShareSessions.chooseInternal(intent.getStringExtra(ForwardShareSessions.EXTRA_SESSION))) {
            Toast.makeText(this, R.string.forward_return_to_messages, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
