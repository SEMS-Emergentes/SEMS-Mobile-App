package pe.edu.upc.sems.iam.domain.repository

import pe.edu.upc.sems.shared.network.ApiResult

/** The two things a user can say about themselves in Configuración. */
enum class UserSegment(val wire: String) {
    RETAIL_CHAIN("RETAIL_CHAIN"),
    INDEPENDENT("INDEPENDENT"),
    ;

    companion object {
        fun from(raw: String?): UserSegment? = entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) }
    }
}

interface ProfileRepository {
    /**
     * Saves the name and segment (PUT /users/me) and keeps the session in step, so the drawer and the greeting
     * show the new name. The backend answers with the saved profile.
     */
    suspend fun updateProfile(fullName: String, segment: UserSegment): ApiResult<Unit>
}
