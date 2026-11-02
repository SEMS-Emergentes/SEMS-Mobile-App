package pe.edu.upc.sems.iam.data.mapper

import pe.edu.upc.sems.iam.data.remote.dto.LoginResponseDto
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.SessionUser

fun LoginResponseDto.toAuthTokens() = AuthTokens(token = token, refreshToken = refreshToken)

fun LoginResponseDto.toSessionUser() = SessionUser(
    userId = userId,
    emailAddress = emailAddress,
    fullName = fullName?.takeIf { it.isNotBlank() },
    roles = roles.toSet(),
    segment = segment,
)
