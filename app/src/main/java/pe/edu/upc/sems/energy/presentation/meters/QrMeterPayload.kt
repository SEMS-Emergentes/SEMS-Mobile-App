package pe.edu.upc.sems.energy.presentation.meters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Web and IoT labels carry either a serial or an object with serial/meter_serial. */
data class QrMeterPayload(val serial: String, val model: String?)

private val SERIAL_PATTERN = Regex("[A-Za-z0-9._:-]{1,120}")

fun parseMeterQr(raw: String): QrMeterPayload? {
    val text = raw.trim()
    if (text.isBlank()) return null
    if (!text.startsWith("{")) return text.takeIf(SERIAL_PATTERN::matches)?.let { QrMeterPayload(it, null) }

    val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    val serial = ((json["serial"] ?: json["meter_serial"]) as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    if (!SERIAL_PATTERN.matches(serial)) return null
    val model = (json["model"] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
    return QrMeterPayload(serial, model)
}
