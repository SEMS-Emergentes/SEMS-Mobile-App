package pe.edu.upc.sems.iam.domain.model

import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.SessionUser

/**
 * Credentials returned by the backend but not stored yet.
 * Used after verifying an account, so the success screen (M29) is shown before entering the app.
 */
data class AuthenticatedAccount(
    val tokens: AuthTokens,
    val user: SessionUser,
)
