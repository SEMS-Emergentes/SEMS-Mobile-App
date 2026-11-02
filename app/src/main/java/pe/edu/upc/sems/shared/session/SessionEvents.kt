package pe.edu.upc.sems.shared.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface SessionEvent {
    /** The refresh token was rejected; the session was cleared. Show "Tu sesión expiró" on the login screen. */
    data object Expired : SessionEvent
}

/**
 * One-off session events. Buffered, so an event emitted while no screen is collecting
 * is delivered when one starts. Meant for a single collector (the app's navigation host).
 */
@Singleton
class SessionEvents @Inject constructor() {
    private val channel = Channel<SessionEvent>(Channel.BUFFERED)

    val events: Flow<SessionEvent> = channel.receiveAsFlow()

    fun emit(event: SessionEvent) {
        channel.trySend(event)
    }
}
