package pe.edu.upc.sems.devices.presentation.deviceform

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.edu.upc.sems.R
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.domain.model.DeviceTypes

@StringRes
fun ConnectionProtocol.label(): Int = when (this) {
    ConnectionProtocol.WIFI -> R.string.device_connection_wifi
    ConnectionProtocol.BLUETOOTH -> R.string.device_connection_bluetooth
}

/** Spanish label of a device type. The value sent to the backend stays the same one the web app uses. */
@Composable
fun deviceTypeLabel(type: String): String {
    val label = when (type) {
        DeviceTypes.LEGACY_METER -> R.string.device_type_legacy
        "AIR_CONDITIONER" -> R.string.device_type_air_conditioner
        "REFRIGERATOR" -> R.string.device_type_refrigerator
        "WATER_HEATER" -> R.string.device_type_water_heater
        "TV" -> R.string.device_type_tv
        "WASHING_MACHINE" -> R.string.device_type_washing_machine
        "MICROWAVE" -> R.string.device_type_microwave
        "THERMOSTAT" -> R.string.device_type_thermostat
        "SENSOR" -> R.string.device_type_sensor
        "smart_plug" -> R.string.device_type_smart_plug
        "Mobile" -> R.string.device_type_mobile
        else -> null
    }
    // A device created elsewhere with another type: show it as it is.
    return label?.let { stringResource(it) } ?: type
}
