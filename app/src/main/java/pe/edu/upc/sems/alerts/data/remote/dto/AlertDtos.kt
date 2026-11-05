package pe.edu.upc.sems.alerts.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Alerts uses snake_case (backend AlertResources, @JsonNaming(SnakeCase)). Requests: no default values on
// the fields the backend needs, because kotlinx does not encode defaults. Null optionals are omitted.

@Serializable
data class AlertDto(
    @SerialName("alert_id") val alertId: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("alert_type") val alertType: String? = null,
    val title: String? = null,
    val message: String? = null,
    val severity: String? = null,
    val status: String? = null,
    @SerialName("triggered_at") val triggeredAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
)

@Serializable
data class UpdateAlertStatusRequestDto(
    val status: String,
    @SerialName("resolved_at") val resolvedAt: String?,
)
