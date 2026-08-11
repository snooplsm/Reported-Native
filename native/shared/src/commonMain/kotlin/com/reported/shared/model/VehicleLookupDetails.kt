package com.reported.shared.model

data class VehicleLookupDetails(
    val licensePlate: String,
    val licenseState: String,
    val vehicleYear: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleBody: String? = null
) {
    val summary: String
        get() = listOfNotNull(
            vehicleYear,
            vehicleMake,
            vehicleModel,
            vehicleBody?.let { "($it)" }
        ).joinToString(" ")
}
