package pe.edu.upc.sems.iam.presentation.password

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.iam.FakeAuthRepository
import pe.edu.upc.sems.iam.presentation.common.AuthValidation
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordViewModelsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    // region Token extraction

    @Test
    fun `token is read from the full email link`() {
        assertEquals(
            "abc-123",
            AuthValidation.extractResetToken("https://sems-web-application.vercel.app/reset-password?token=abc-123"),
        )
        assertEquals("a b", AuthValidation.extractResetToken("  https://x.app/reset-password?foo=1&token=a%20b#top "))
    }

    @Test
    fun `a bare token is accepted, other text is not`() {
        assertEquals("abc-123", AuthValidation.extractResetToken(" abc-123 "))
        assertNull(AuthValidation.extractResetToken("https://sems-web-application.vercel.app/login"))
        assertNull(AuthValidation.extractResetToken("hola que tal"))
        assertNull(AuthValidation.extractResetToken(""))
    }

    // endregion

    // region M03

    @Test
    fun `forgot password validates the email and opens M04 on success`() = runTest(mainRule.dispatcher) {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange("ana@")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(FieldError.INVALID_EMAIL, viewModel.uiState.value.emailError)
        assertTrue(repository.resetRequests.isEmpty())

        viewModel.onEmailChange(" ana@example.com ")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(listOf("ana@example.com"), repository.resetRequests)
        assertEquals("ana@example.com", viewModel.uiState.value.sentTo)

        viewModel.onSentHandled()
        assertNull(viewModel.uiState.value.sentTo)
    }

    @Test
    fun `forgot password prefills the login email only once`() {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.prefill(" ana@example.com ")
        viewModel.onEmailChange("otro@example.com")
        viewModel.prefill("ana@example.com")
        assertEquals("otro@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `forgot password shows network failures`() = runTest(mainRule.dispatcher) {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange("ana@example.com")
        repository.resetRequestResult = ApiResult.Failure(ApiError.Timeout)
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(PasswordError.TIMEOUT, viewModel.uiState.value.error)

        repository.resetRequestResult = ApiResult.Failure(ApiError.Http(400, "must be a valid email"))
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(PasswordError.GENERIC, viewModel.uiState.value.error)
    }

    // endregion

    // region M04

    @Test
    fun `resend is locked for 60 seconds, then sends again and restarts the countdown`() = runTest(mainRule.dispatcher) {
        val viewModel = ResetLinkSentViewModel(repository)
        runCurrent()
        assertEquals(60, viewModel.uiState.value.resendSecondsLeft)

        viewModel.onResend("ana@example.com")
        assertTrue(repository.resetRequests.isEmpty())

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.resendSecondsLeft)

        viewModel.onResend("ana@example.com")
        runCurrent()
        assertEquals(listOf("ana@example.com"), repository.resetRequests)
        assertTrue(viewModel.uiState.value.resent)
        assertEquals(60, viewModel.uiState.value.resendSecondsLeft)
    }

    // endregion

    // region M30

    private fun resetViewModel() = ResetPasswordViewModel(repository)

    @Test
    fun `M30 requires a usable link, 8 characters and matching passwords`() = runTest(mainRule.dispatcher) {
        val viewModel = resetViewModel()
        viewModel.onLinkChange("https://sems-web-application.vercel.app/login")
        viewModel.onPasswordChange("1234567")
        viewModel.onConfirmationChange("1234568")
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FieldError.INVALID_RESET_LINK, state.linkError)
        assertEquals(FieldError.RESET_PASSWORD_TOO_SHORT, state.passwordError)
        assertEquals(FieldError.PASSWORDS_DO_NOT_MATCH, state.confirmationError)
        assertTrue(repository.resetCalls.isEmpty())
    }

    @Test
    fun `M30 rules follow what the user types`() {
        val viewModel = resetViewModel()
        viewModel.onPasswordChange("12345678")
        assertTrue(viewModel.uiState.value.hasMinLength)
        assertFalse(viewModel.uiState.value.passwordsMatch)
        viewModel.onConfirmationChange("12345678")
        assertTrue(viewModel.uiState.value.passwordsMatch)
    }

    @Test
    fun `M30 sends the token from the pasted link`() = runTest(mainRule.dispatcher) {
        val viewModel = resetViewModel()
        viewModel.onLinkChange("https://sems-web-application.vercel.app/reset-password?token=tok-1")
        viewModel.onPasswordChange("NuevaClave8")
        viewModel.onConfirmationChange("NuevaClave8")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("tok-1" to "NuevaClave8"), repository.resetCalls)
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `M30 with an App Link token hides the paste field`() = runTest(mainRule.dispatcher) {
        val viewModel = resetViewModel()
        viewModel.setTokenFromLink("tok-from-link")
        assertFalse(viewModel.uiState.value.needsLink)

        viewModel.onPasswordChange("NuevaClave8")
        viewModel.onConfirmationChange("NuevaClave8")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("tok-from-link" to "NuevaClave8"), repository.resetCalls)
    }

    @Test
    fun `M30 maps a rejected token to an invalid link`() = runTest(mainRule.dispatcher) {
        val viewModel = resetViewModel()
        viewModel.onLinkChange("tok-old")
        viewModel.onPasswordChange("NuevaClave8")
        viewModel.onConfirmationChange("NuevaClave8")

        repository.resetResult = ApiResult.Failure(ApiError.Http(400, "Invalid or expired token"))
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(PasswordError.INVALID_LINK, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.done)

        repository.resetResult = ApiResult.Failure(ApiError.Http(503, null))
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(PasswordError.GENERIC, viewModel.uiState.value.error)
    }

    // endregion
}
