package pe.edu.upc.sems.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pe.edu.upc.sems.settings.domain.AppearanceStore
import pe.edu.upc.sems.settings.domain.ThemeMode

/** Gives MainActivity the theme the user chose, so every screen follows it. */
@HiltViewModel
class ThemeViewModel @Inject constructor(store: AppearanceStore) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = store.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
