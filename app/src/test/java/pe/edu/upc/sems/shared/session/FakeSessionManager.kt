package pe.edu.upc.sems.shared.session

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SessionManager] for tests. Mirrors the "no-op when logged out" rules of the real one. */
class FakeSessionManager(initial: Session? = null) : SessionManager {

    private val state = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = state

    val clearCount = AtomicInteger(0)

    override suspend fun saveLogin(tokens: AuthTokens, user: SessionUser) {
        state.update { current ->
            val sameUser = current?.user?.userId == user.userId
            Session(
                tokens = tokens,
                user = user,
                selectedSite = current?.selectedSite?.takeIf { sameUser },
                membership = current?.membership?.takeIf { sameUser },
            )
        }
    }

    override suspend fun updateTokens(tokens: AuthTokens) {
        state.update { it?.copy(tokens = tokens) }
    }

    override suspend fun selectSite(site: SelectedSite?) {
        state.update { it?.copy(selectedSite = site) }
    }

    override suspend fun saveMembership(membership: SessionMembership?) {
        state.update { session ->
            session?.copy(
                membership = membership,
                selectedSite = session.selectedSite?.takeIf { membership != null && it.organizationId == membership.organizationId },
            )
        }
    }

    override suspend fun clear() {
        clearCount.incrementAndGet()
        state.value = null
    }
}
