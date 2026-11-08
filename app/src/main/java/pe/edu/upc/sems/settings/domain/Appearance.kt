package pe.edu.upc.sems.settings.domain

import kotlinx.coroutines.flow.Flow

/** The "Tema" choices: follow the phone, or force light or dark (the web only toggles light and dark). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What the user chose for how the app looks. It lives on the phone only: the backend has no such setting. */
interface AppearanceStore {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
