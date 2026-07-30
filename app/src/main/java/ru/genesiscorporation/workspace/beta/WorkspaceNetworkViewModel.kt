package ru.genesiscorporation.workspace.beta

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.push.FirebasePushRegistrationTokenProvider
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import ru.genesiscorporation.workspace.beta.data.push.TinkPushDeviceIdentityStore
import ru.genesiscorporation.workspace.beta.data.push.WorkspacePushDeviceRemoteDataSource
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class WorkspaceNetworkViewModel(
    userViewModel: UserViewModel,
    appContext: Context,
) : ViewModel() {
    private val sessionCookieStore = SessionCookieStore()
    private val httpClient = HttpClient {
        install(WebSockets) {
            // Ktor closes a session when a pong is not received within twice
            // this interval. That turns a half-open mobile transport into the
            // normal bounded reconnect + REST catch-up path instead of leaving
            // the catalog silently stale indefinitely.
            pingIntervalMillis = REALTIME_PING_INTERVAL_MILLIS
            maxFrameSize = MAX_REALTIME_FRAME_BYTES
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 45_000
        }
        install(createSessionCapturePlugin(sessionCookieStore))
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    val apiClient = WorkspaceAPIClient(
        client = httpClient,
        userViewModel = userViewModel,
        sessionCookieStore = sessionCookieStore,
    )
    val eventsRepository = EventsRepository(
        cursorStore = userViewModel.realtimeCursorStore,
    ).also { repository ->
        repository.client = apiClient
    }
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START ->
                eventsRepository.setAppForeground(true)
            Lifecycle.Event.ON_STOP ->
                eventsRepository.setAppForeground(false)
            else -> Unit
        }
    }
    val conversationStateStore = userViewModel.conversationStateStore
    val pushDeviceRegistrationManager = PushDeviceRegistrationManager(
        tokenProvider = FirebasePushRegistrationTokenProvider(),
        identityProvider = TinkPushDeviceIdentityStore(appContext),
        remoteDataSource = WorkspacePushDeviceRemoteDataSource(apiClient),
    )

    init {
        processLifecycle.addObserver(processLifecycleObserver)
        eventsRepository.setAppForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    override fun onCleared() {
        processLifecycle.removeObserver(processLifecycleObserver)
        eventsRepository.setAppForeground(false)
        httpClient.close()
        super.onCleared()
    }
}

internal const val REALTIME_PING_INTERVAL_MILLIS = 20_000L
internal const val MAX_REALTIME_FRAME_BYTES = 2L * 1_024L * 1_024L
