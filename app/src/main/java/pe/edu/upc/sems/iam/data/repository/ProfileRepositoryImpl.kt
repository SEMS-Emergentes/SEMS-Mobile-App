package pe.edu.upc.sems.iam.data.repository

import javax.inject.Inject
import pe.edu.upc.sems.iam.data.remote.UpdateProfileRequestDto
import pe.edu.upc.sems.iam.data.remote.UserService
import pe.edu.upc.sems.iam.domain.repository.ProfileRepository
import pe.edu.upc.sems.iam.domain.repository.UserSegment
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.safeApiCall
import pe.edu.upc.sems.shared.session.SessionManager

class ProfileRepositoryImpl @Inject constructor(
    private val service: UserService,
    private val sessionManager: SessionManager,
) : ProfileRepository {

    override suspend fun updateProfile(fullName: String, segment: UserSegment): ApiResult<Unit> =
        when (val result = safeApiCall { service.updateMe(UpdateProfileRequestDto(fullName, segment.wire)) }) {
            is ApiResult.Success -> {
                // What the backend saved is what the session keeps (it may trim or normalize).
                sessionManager.updateProfile(result.data.fullName, result.data.segment)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
}
