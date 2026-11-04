package pe.edu.upc.sems.energy.presentation.meters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.R
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.Formatters

sealed interface MeterHistoryState {
    data object Loading : MeterHistoryState
    data class Error(val error: FormError) : MeterHistoryState
    data class Ready(val serial: String, val days: Int, val readings: List<EnergyReading>) : MeterHistoryState
}

@HiltViewModel
class MeterHistoryViewModel @Inject constructor(
    private val energy: EnergyRepository,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<MeterHistoryState>(MeterHistoryState.Loading)
    val state: StateFlow<MeterHistoryState> = _state.asStateFlow()
    private var selectedDays = 7
    private var request: Job? = null

    fun load(meterId: String, days: Int = selectedDays) {
        if (days !in listOf(7, 14, 30)) return
        selectedDays = days
        request?.cancel()
        request = viewModelScope.launch {
            _state.value = MeterHistoryState.Loading
            val session = sessions.session.first()
            val siteId = session?.selectedSite?.siteId
            if (session == null || siteId == null) {
                _state.value = MeterHistoryState.Error(FormError.NOT_FOUND)
                return@launch
            }
            val meters = when (val result = energy.listMetersBySite(siteId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    _state.value = MeterHistoryState.Error(result.error.toFormError())
                    return@launch
                }
            }
            val meter = meters.firstOrNull { it.id == meterId }
            if (meter == null) {
                _state.value = MeterHistoryState.Error(FormError.NOT_FOUND)
                return@launch
            }
            val to = Instant.now()
            when (val result = energy.meterReadingHistory(
                session.user.userId, meterId, to.minus(Duration.ofDays(days.toLong())), to,
            )) {
                is ApiResult.Success -> _state.value = MeterHistoryState.Ready(
                    meter.serial, days, result.data.sortedByDescending { it.measuredAt },
                )
                is ApiResult.Failure -> _state.value = MeterHistoryState.Error(result.error.toFormError())
            }
        }
    }
}

@Composable
fun MeterHistoryScreen(
    meterId: String,
    onBack: () -> Unit,
    viewModel: MeterHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(meterId) {
        viewModel.load(meterId)
        onPauseOrDispose { }
    }
    FormScaffold(stringResource(R.string.meter_history_title), onBack) {
        when (val value = state) {
            MeterHistoryState.Loading -> LoadingScreen()
            is MeterHistoryState.Error -> FormErrorBanner(
                value.error, R.string.form_error_generic_title, R.string.form_error_generic_body,
                { viewModel.load(meterId) }, offerRetry = true,
            )
            is MeterHistoryState.Ready -> {
                Text(value.serial, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = value.days == days,
                            onClick = { viewModel.load(meterId, days) },
                            label = { Text(stringResource(R.string.monitoring_days, days)) },
                        )
                    }
                }
                if (value.readings.isEmpty()) {
                    Text(stringResource(R.string.meter_history_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    value.readings.forEach { reading ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
                                reading.measuredAt?.let {
                                    Text(
                                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                            .withZone(ZoneId.systemDefault()).format(it),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                reading.powerKw?.let {
                                    Text(stringResource(R.string.meter_history_power, Formatters.kilowatts(it)))
                                }
                                reading.energyKwh?.let {
                                    Text(stringResource(R.string.meter_history_energy, Formatters.kilowattHours(it)))
                                }
                                if (reading.powerKw == null && reading.energyKwh == null) {
                                    Text(stringResource(R.string.meter_history_no_value))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
