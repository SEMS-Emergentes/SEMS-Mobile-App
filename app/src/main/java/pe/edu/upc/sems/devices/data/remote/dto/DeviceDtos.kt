package pe.edu.upc.sems.devices.data.remote.dto

import kotlinx.serialization.Serializable

// Device Management uses camelCase (backend DeviceResources). Requests: no default values on the fields the
// backend needs, because kotlinx does not encode defaults. Null optional fields are omitted (explicitNulls = false).

@Serializable
data class DeviceDto(
    val deviceId: String,
    val externalDeviceCode: String = "",
    val siteId: String,
    val zoneId: String? = null,
    val deviceName: String,
    val deviceType: String = "",
    val brand: String? = null,
    val model: String? = null,
    val connectionProtocol: String? = null,
    val status: String? = null,
)

@Serializable
data class CreateDeviceRequestDto(
    val externalDeviceCode: String,
    val userId: String,
    val siteId: String,
    val zoneId: String?,
    val deviceName: String,
    val deviceType: String,
    val brand: String?,
    val model: String?,
    val connectionProtocol: String,
)

@Serializable
data class UpdateDeviceRequestDto(
    val deviceName: String,
    val deviceType: String,
    val brand: String?,
    val model: String?,
    val connectionProtocol: String,
    val zoneId: String?,
)
