package com.reported.shared.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleLookupModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLookupAPlateVehicleDetails() {
        val details = decodeVehicleLookupResponse(
            json = json,
            responseBody = """
                {
                  "result": {
                    "licensePlate": "T144594C",
                    "licenseState": "NY",
                    "vehicleYear": "2020",
                    "vehicleMake": "TOYOTA",
                    "vehicleModel": "Sienna",
                    "vehicleBody": "Minivan"
                  }
                }
            """.trimIndent(),
            requestedPlate = "T144594C",
            requestedState = "NY"
        )

        assertEquals("T144594C", details?.licensePlate)
        assertEquals("NY", details?.licenseState)
        assertEquals("2020 TOYOTA Sienna (Minivan)", details?.summary)
    }

    @Test
    fun treatsResponseWithoutVehicleFieldsAsNoMatch() {
        val details = decodeVehicleLookupResponse(
            json = json,
            responseBody = """{"result":{"licensePlate":"UNKNOWN","licenseState":"NY"}}""",
            requestedPlate = "UNKNOWN",
            requestedState = "NY"
        )

        assertNull(details)
    }
}
