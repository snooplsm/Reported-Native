package com.reported.shared.api

import com.reported.shared.model.VehicleLookupDetails
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
internal data class VehicleLookupEnvelopeDto(
    val result: VehicleLookupResultDto? = null
)

@Serializable
internal data class VehicleLookupResultDto(
    val licensePlate: String? = null,
    val licenseState: String? = null,
    val vehicleYear: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleBody: String? = null
)

internal fun decodeVehicleLookupResponse(
    json: Json,
    responseBody: String,
    requestedPlate: String,
    requestedState: String
): VehicleLookupDetails? {
    val result = json.decodeFromString<VehicleLookupEnvelopeDto>(responseBody).result ?: return null
    val vehicleYear = result.vehicleYear.normalizedLookupText()
    val vehicleMake = result.vehicleMake.normalizedLookupText()
    val vehicleModel = result.vehicleModel.normalizedLookupText()
    val vehicleBody = result.vehicleBody.normalizedLookupText()
    if (listOf(vehicleYear, vehicleMake, vehicleModel, vehicleBody).all { it == null }) {
        return null
    }
    return VehicleLookupDetails(
        licensePlate = result.licensePlate.normalizedLookupText() ?: requestedPlate,
        licenseState = result.licenseState.normalizedLookupText() ?: requestedState,
        vehicleYear = vehicleYear,
        vehicleMake = vehicleMake,
        vehicleModel = vehicleModel,
        vehicleBody = vehicleBody
    )
}

private fun String?.normalizedLookupText(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
