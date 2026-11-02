package pe.edu.upc.sems.iam.presentation.verify

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.iam.FakeAuthRepository
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyAccountViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = VerifyAccountViewModel(repository)

    @Test
    fun `empty code is required`() = runTest(mainRule.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FieldError.REQUIRED, viewModel.uiState.value.codeError)
        assertTrue(repository.verifyCalls.isEmpty())
    }

    @Test
    fun `valid code shows M29 without storing the session yet`() = runTest(mainRule.dispatcher) {
        viewModel.onCodeChange(" code-123 ")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("code-123"), repository.verifyCalls)
        assertEquals(FakeAuthRepository.ACCOUNT, viewModel.uiState.value.verifiedAccount)
        assertTrue(repository.startedSessions.isEmpty())
    }

    @Test
    fun `continue on M29 stores the session`() = runTest(mainRule.dispatcher) {
        viewModel.onCodeChange("code-123")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(listOf(FakeAuthRepository.ACCOUNT), repository.startedSessions)
    }

    @Test
    fun `rejected code shows invalid code`() = runTest(mainRule.dispatcher) {
        listOf(ApiError.Http(400, "Invalid token"), ApiError.Unauthorized(null), ApiError.Http(404, null)).forEach {
            repository.verifyResult = ApiResult.Failure(it)
            viewModel.onCodeChange("bad")
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(VerifyError.INVALID_CODE, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.verifiedAccount)
        }
    }

    @Test
    fun `server and network failures are not shown as an invalid code`() = runTest(mainRule.dispatcher) {
        val cases = mapOf(
            ApiError.Http(500, null) to VerifyError.GENERIC,
            ApiError.Timeout to VerifyError.TIMEOUT,
            ApiError.NoConnection to VerifyError.NO_CONNECTION,
        )
        viewModel.onCodeChange("code")
        cases.forEach { (apiError, expected) ->
            repository.verifyResult = ApiResult.Failure(apiError)
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
        }
    }
}
