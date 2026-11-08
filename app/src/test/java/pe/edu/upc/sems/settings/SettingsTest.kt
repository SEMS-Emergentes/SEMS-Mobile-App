package pe.edu.upc.sems.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.alerts.FakeAlertRepository
import pe.edu.upc.sems.alerts.domain.model.NotificationPreference
import pe.edu.upc.sems.iam.domain.repository.ProfileRepository
import pe.edu.upc.sems.iam.domain.repository.UserSegment
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.settings.domain.AppearanceStore
import pe.edu.upc.sems.settings.domain.ThemeMode
import pe.edu.upc.sems.settings.presentation.EditProfileViewModel
import pe.edu.upc.sems.settings.presentation.MAX_NAME_LENGTH
import pe.edu.upc.sems.settings.presentation.SettingsViewModel
import pe.edu.upc.sems.settings.presentation.initials
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

class FakeAppearanceStore : AppearanceStore {
    val mode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode = mode
    override suspend fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
    }
}

class FakeProfileRepository(private val sessionManager: FakeSessionManager) : ProfileRepository {
    /** (name, segment) of every save. */
    val calls = mutableListOf<Pair<String, UserSegment>>()
    var failure: ApiResult.Failure? = null

    override suspend fun updateProfile(fullName: String, segment: UserSegment): ApiResult<Unit> {
        calls += fullName to segment
        failure?.let { return it }
        sessionManager.updateProfile(fullName, segment.wire)
        return ApiResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "valeria@example.com", "Valeria Rojas", setOf("ADMIN"), "RETAIL_CHAIN")
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val alerts = FakeAlertRepository()
    private val appearance = FakeAppearanceStore()
    private val languages = FakeLanguageStore()

    // region small helpers

    @Test
    fun `the avatar shows the first letters of the name, or of the email without one`() {
        assertEquals("VR", initials(user))
        assertEquals("V", initials(user.copy(fullName = "valeria")))
        assertEquals("AB", initials(user.copy(fullName = "  ana   bea   cruz ")))
        assertEquals("V", initials(user.copy(fullName = null)))
        assertEquals("V", initials(user.copy(fullName = "")))
    }

    @Test
    fun `segments are read from what the backend sends`() {
        assertEquals(UserSegment.RETAIL_CHAIN, UserSegment.from("RETAIL_CHAIN"))
        assertEquals(UserSegment.INDEPENDENT, UserSegment.from("independent"))
        assertNull(UserSegment.from(null))
        assertNull(UserSegment.from("OTHER"))
    }

    // endregion

    // region Configuración

    private fun settings() = SettingsViewModel(alerts, appearance, languages, sessionManager)

    @Test
    fun `shows the user and, without a saved preference, the email switch on`() = runTest(mainRule.dispatcher) {
        val viewModel = settings()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Valeria Rojas", state.user!!.fullName)
        assertEquals(true, state.emailEnabled) // the backend sends its emails anyway
        assertFalse(state.emailFailed)
    }

    @Test
    fun `the email switch shows what the backend has saved`() = runTest(mainRule.dispatcher) {
        alerts.preferencesResult = ApiResult.Success(
            listOf(NotificationPreference("sms", true, "low"), NotificationPreference("email", false, "high")),
        )
        val viewModel = settings()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.emailEnabled)
    }

    @Test
    fun `turning the switch saves it keeping the severity the user already had`() = runTest(mainRule.dispatcher) {
        alerts.preferencesResult = ApiResult.Success(listOf(NotificationPreference("email", false, "high")))
        val viewModel = settings()
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.onEmailToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(Triple("email", true, "high")), alerts.preferenceCalls)
        assertEquals(true, viewModel.uiState.value.emailEnabled)
        assertFalse(viewModel.uiState.value.emailSaving)
    }

    @Test
    fun `a switch that could not be saved goes back and says so`() = runTest(mainRule.dispatcher) {
        alerts.preferenceFailure = ApiResult.Failure(ApiError.Http(500, "boom"))
        val viewModel = settings()
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.onEmailToggle(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.emailEnabled)
        assertTrue(state.emailFailed)
        assertFalse(state.emailSaving)
    }

    @Test
    fun `if the preference cannot be read the switch stays unavailable`() = runTest(mainRule.dispatcher) {
        alerts.preferencesResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = settings()

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.onEmailToggle(false)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.emailEnabled)
        assertTrue(viewModel.uiState.value.emailFailed)
        assertTrue(alerts.preferenceCalls.isEmpty())
    }

    @Test
    fun `the chosen theme is stored and shown`() = runTest(mainRule.dispatcher) {
        val viewModel = settings()

        viewModel.onThemeChange(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, appearance.themeMode.first())
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `English is the default language and the chosen one is saved`() = runTest(mainRule.dispatcher) {
        val viewModel = settings()
        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.ENGLISH, viewModel.uiState.value.language)

        viewModel.onLanguageChange(pe.edu.upc.sems.settings.domain.AppLanguage.SPANISH)

        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.SPANISH, languages.language)
        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.SPANISH, viewModel.uiState.value.language)
    }

    @Test
    fun `a language tag that is not known falls back to English`() {
        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.ENGLISH, pe.edu.upc.sems.settings.domain.AppLanguage.fromTag(null))
        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.ENGLISH, pe.edu.upc.sems.settings.domain.AppLanguage.fromTag("fr"))
        assertEquals(pe.edu.upc.sems.settings.domain.AppLanguage.SPANISH, pe.edu.upc.sems.settings.domain.AppLanguage.fromTag("es"))
    }

    @Test
    fun `numbers are written the English way when the app is in English`() {
        val before = pe.edu.upc.sems.shared.util.Formatters.useEnglishFormat
        try {
            pe.edu.upc.sems.shared.util.Formatters.useEnglishFormat = true
            assertEquals("4,820 kWh", pe.edu.upc.sems.shared.util.Formatters.kilowattHours(4820.0).replace(' ', ' '))
            assertEquals("S/ 52.40", pe.edu.upc.sems.shared.util.Formatters.soles(52.4).replace(' ', ' '))
        } finally {
            pe.edu.upc.sems.shared.util.Formatters.useEnglishFormat = before
        }
    }

    @Test
    fun `a new name saved elsewhere reaches the screen`() = runTest(mainRule.dispatcher) {
        val viewModel = settings()
        advanceUntilIdle()

        sessionManager.updateProfile("Valeria R.", "INDEPENDENT")
        advanceUntilIdle()

        assertEquals("Valeria R.", viewModel.uiState.value.user!!.fullName)
    }

    // endregion

    // region Editar perfil

    private val profile = FakeProfileRepository(sessionManager)

    private fun edit() = EditProfileViewModel(profile, sessionManager)

    @Test
    fun `the form starts from the saved profile and has nothing to save yet`() = runTest(mainRule.dispatcher) {
        val viewModel = edit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Valeria Rojas", state.name)
        assertEquals("valeria@example.com", state.email)
        assertEquals(UserSegment.RETAIL_CHAIN, state.segment)
        assertFalse(state.canSave)
    }

    @Test
    fun `without a saved segment the form starts from the web's default`() = runTest(mainRule.dispatcher) {
        sessionManager.updateProfile("Valeria Rojas", null)
        val viewModel = edit()
        advanceUntilIdle()

        assertEquals(UserSegment.RETAIL_CHAIN, viewModel.uiState.value.segment)
    }

    @Test
    fun `changing the name or the segment allows saving, and the name is trimmed`() = runTest(mainRule.dispatcher) {
        val viewModel = edit()
        advanceUntilIdle()

        viewModel.onNameChange("  Valeria R.  ")
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.onSegmentChange(UserSegment.INDEPENDENT)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf("Valeria R." to UserSegment.INDEPENDENT), profile.calls)
        assertTrue(viewModel.uiState.value.saved)
        // The session, and so the drawer and Configuración, already show the new name.
        assertEquals("Valeria R.", sessionManager.session.first()!!.user.fullName)
        assertEquals("INDEPENDENT", sessionManager.session.first()!!.user.segment)
    }

    @Test
    fun `an empty name is not saved`() = runTest(mainRule.dispatcher) {
        val viewModel = edit()
        advanceUntilIdle()

        viewModel.onNameChange("   ")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nameError)
        assertTrue(profile.calls.isEmpty())
    }

    @Test
    fun `the name is cut at the length the backend accepts`() = runTest(mainRule.dispatcher) {
        val viewModel = edit()
        advanceUntilIdle()

        viewModel.onNameChange("a".repeat(MAX_NAME_LENGTH + 30))

        assertEquals(MAX_NAME_LENGTH, viewModel.uiState.value.name.length)
    }

    @Test
    fun `a save that fails reports it and keeps the form`() = runTest(mainRule.dispatcher) {
        profile.failure = ApiResult.Failure(ApiError.Timeout)
        val viewModel = edit()
        advanceUntilIdle()
        viewModel.onNameChange("Otro nombre")

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FormError.TIMEOUT, state.error)
        assertFalse(state.saved)
        assertFalse(state.isSaving)
        assertEquals("Otro nombre", state.name)
    }

    // endregion
}

class FakeLanguageStore(var language: pe.edu.upc.sems.settings.domain.AppLanguage = pe.edu.upc.sems.settings.domain.AppLanguage.DEFAULT) :
    pe.edu.upc.sems.settings.domain.LanguageStore {
    override fun get() = language
    override fun set(language: pe.edu.upc.sems.settings.domain.AppLanguage) {
        this.language = language
    }
}
