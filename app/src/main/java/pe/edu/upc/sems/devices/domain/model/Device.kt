package pe.edu.upc.sems.devices.domain.model

/** Status of a device (backend DeviceStatus). REMOVED devices are not shown in the app. */
enum class DeviceStatus {
    ACTIVE, INACTIVE, DISCONNECTED, REMOVED, UNKNOWN;

    companion object {
        fun from(value: String?): DeviceStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** How the device talks to the platform (backend ConnectionProtocol). */
enum class ConnectionProtocol {
    WIFI, BLUETOOTH;

    companion object {
        fun from(value: String?): ConnectionProtocol =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: WIFI
    }
}

/** A piece of equipment (cold room, air conditioning...) of a site. Not the same as an energy meter. */
data class Device(
    val id: String,
    val externalCode: String,
    val siteId: String,
    val zoneId: String?,
    val name: String,
    val type: String,
    val brand: String?,
    val model: String?,
    val protocol: ConnectionProtocol,
    val status: DeviceStatus,
)

/** Values of the create / edit form. The site is only used when creating: a device never changes site. */
data class DeviceDraft(
    val externalCode: String,
    val siteId: String,
    val zoneId: String?,
    val name: String,
    val type: String,
    val brand: String,
    val model: String,
    val protocol: ConnectionProtocol,
)
