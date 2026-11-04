package pe.edu.upc.sems.energy.data.remote.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyDtosTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test fun `meter request includes required fields and optional zone`() {
        val encoded = json.encodeToString(CreateMeterRequestDto.serializer(), CreateMeterRequestDto("user-1", "site-1", "MED-002"))
        val body = json.parseToJsonElement(encoded).jsonObject
        assertEquals("MED-002", body.getValue("meter_serial").jsonPrimitive.content)
        assertEquals("site-1", body.getValue("site_id").jsonPrimitive.content)
        assertNull(body["zone_id"])
    }

    @Test fun `meter and reading follow backend field names`() {
        val meter = json.decodeFromString(EnergyMeterDto.serializer(), """{"id":"m-1","meter_serial":"MED-002","status":"active","site_id":"site-1"}""")
        val reading = json.decodeFromString(MeterReadingDto.serializer(), """{"meter_id":"m-1","power_watts":1240.0,"energy_kwh":2.5}""")
        assertEquals("MED-002", meter.meterSerial)
        assertEquals(1240.0, reading.powerWatts!!, 0.0)
    }

    @Test fun `zone aggregate keeps official total and unassigned meters`() {
        val aggregate = json.decodeFromString(
            ZoneBreakdownDto.serializer(),
            """{"site_id":"site-1","days":30,"total_kwh":42.5,"zones":[],"unassigned":{"zone_name":"Sin zona","meter_count":2,"kwh":5.5,"estimated_energy_cost":3.2}}""",
        )
        assertEquals(42.5, aggregate.totalKwh, 0.0)
        assertNull(aggregate.unassigned?.zoneId)
        assertEquals(2, aggregate.unassigned?.meterCount)
        assertEquals(3.2, aggregate.unassigned?.estimatedEnergyCost ?: 0.0, 0.0)
    }
}
