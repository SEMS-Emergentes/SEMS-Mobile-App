package pe.edu.upc.sems.devices.domain.model

/**
 * The backend accepts any non-blank text as the device type. These are the values the web app offers
 * (DeviceModal.vue), so a device created on one client looks the same on the other.
 */
object DeviceTypes {
    const val LEGACY_METER = "meter"
    const val DEFAULT = "REFRIGERATOR"

    val all: List<String> = listOf(
        "AIR_CONDITIONER", "REFRIGERATOR", "WATER_HEATER", "TV",
        "WASHING_MACHINE", "MICROWAVE", "THERMOSTAT", "SENSOR", "smart_plug", "Mobile",
    )

    /**
     * New equipment never offers the legacy `meter` type. An existing record keeps its raw type
     * in the edit selector so opening and saving it does not silently rewrite backend data.
     */
    fun optionsFor(current: String?): List<String> =
        if (current.isNullOrBlank() || current in all) all else all + current
}
