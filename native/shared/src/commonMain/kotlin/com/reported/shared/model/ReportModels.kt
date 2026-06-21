package com.reported.shared.model

import kotlinx.serialization.Serializable

data class Address(
    val id: Long,
    val street: String,
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val lat: Double? = null,
    val lng: Double? = null
)

data class ReportSummary(
    val id: Long,
    val objectId: String = "",
    val addressId: Long?,
    val incidentAt: String,
    val description: String,
    val notes: String,
    val plate: String,
    val plateRegion: String = "",
    val status: String,
    val statusCode: Int? = null,
    val street: String,
    val complaint: String = "",
    val requestNumber: String = "",
    val mediaUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList()
) {
    val canDelete: Boolean
        get() = statusCode == 0
}

data class ReportsPage(
    val reports: List<ReportSummary>,
    val hasMore: Boolean
)

data class ReportStats(
    val raw: Map<String, String>
)

data class ReportFilter(
    val keywords: String = "",
    val srid: String = "",
    val complaints: List<String> = emptyList(),
    val whenDescription: String = "",
    val locationDescription: String = "",
    val license: String = "",
    val startDateIso: String = "",
    val endDateIso: String = ""
)

data class ComplaintCategory(
    val id: String,
    val name: String,
    val audience: String,
    val key: String = name
)

object CityReportingRules {
    const val NYC_CITY_ID = "nyc"
    const val PHILADELPHIA_CITY_ID = "philadelphia"
    const val PHILADELPHIA_ADDRESS_PROVIDER_ID = "philadelphia_ais"
    const val NYC_ADDRESS_PROVIDER_ID = "nyc_geosearch"

    val philadelphiaZipCodes = setOf(
        "19102", "19103", "19104", "19106", "19107", "19111", "19112", "19114",
        "19115", "19116", "19118", "19119", "19120", "19121", "19122", "19123",
        "19124", "19125", "19126", "19127", "19128", "19129", "19130", "19131",
        "19132", "19133", "19134", "19135", "19136", "19137", "19138", "19139",
        "19140", "19141", "19142", "19143", "19144", "19145", "19146", "19147",
        "19148", "19149", "19150", "19151", "19152", "19153", "19154"
    )

    fun isCoordinateInsidePhiladelphia(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude < 39.867 || latitude > 40.138) return false
        if (longitude < -75.280 || longitude > -74.955) return false
        return true
    }

    fun isPhiladelphiaAddressText(address: String): Boolean {
        val value = address.trim().lowercase()
        if (value.isBlank()) return false
        if ("philadelphia" in value || "phila" in value || "philly" in value) return true
        return philadelphiaZipCodes.any { zip -> Regex("""\b$zip\b""").containsMatchIn(value) }
    }

    fun isPhiladelphiaReport(latitude: Double?, longitude: Double?, address: String): Boolean {
        if (latitude != null && longitude != null && isCoordinateInsidePhiladelphia(latitude, longitude)) {
            return true
        }
        return isPhiladelphiaAddressText(address)
    }
}

object PhiladelphiaMobilityAccessCatalogs {
    val vehicleMakes = listOf(
        "Acura", "Audi", "BMW", "Buick", "Cadillac", "Chevrolet", "Chrysler", "Dodge",
        "Ford", "Genesis", "GMC", "Harley Davidson", "Honda", "Hummer", "Hyundai",
        "Infiniti", "Jeep", "KIA", "Lexus", "Lincoln", "Mazda", "Mercedes", "Mercury",
        "Mini Cooper", "Mitsubishi", "Nissan", "Pontiac", "Ram", "Rivian", "Saab",
        "Subaru", "Suzuki", "Tesla", "Toyota", "Volkswagen", "Volvo",
        "Construction Equipment", "Multiple Vehicles", "Off Road Vehicle", "Unknown"
    )

    val vehicleModels = listOf(
        "3 Series", "5 Series", "Accord", "Acadia", "Altima", "Atlas", "Bolt", "Bronco",
        "Camaro", "Camry", "Canyon", "Civic", "Colorado", "Corolla", "CR-V", "CX-5",
        "CX-30", "CX-50", "Edge", "Elantra", "Equinox", "Escape", "Explorer", "F-150",
        "Focus", "Forester", "Forte", "Frontier", "Grand Caravan", "Grand Cherokee",
        "Highlander", "HR-V", "Impala", "Jetta", "K5", "Leaf", "Malibu", "Maxima",
        "Model 3", "Model S", "Model X", "Model Y", "Murano", "Odyssey", "Optima",
        "Outback", "Palisade", "Pathfinder", "Pilot", "Prius", "RAV4", "Rogue",
        "Santa Fe", "Savana", "Sentra", "Sienna", "Sierra", "Silverado", "Sonata",
        "Soul", "Sportage", "Suburban", "Tacoma", "Tahoe", "Telluride", "Terrain",
        "Tiguan", "Transit", "Traverse", "Tucson", "Tundra", "Versa", "Wrangler",
        "X1", "X3", "X5", "XC40", "XC60", "XC90", "Yukon"
    )

    val bodyStyles = listOf(
        "Sedan (4 door car)", "Coupe (2 door car)", "SUV", "Minivan", "Van",
        "Pickup Truck", "Box-Truck", "Flatbed", "Bus", "RV", "Camper", "Trailer",
        "Boat", "Motorcycle", "Construction Equipment", "Unknown"
    )

    val vehicleColors = listOf(
        "Beige", "Black", "Blue", "Brown", "Gold", "Gray", "Green", "Maroon",
        "Orange", "Purple", "Red", "Silver", "Teal", "White", "Yellow", "Unknown"
    )

    val violationObservedOptions = listOf(
        "Crosswalk (vehicle on crosswalk)",
        "Bike Lane (vehicle parked in bike lane)",
        "Sidewalk",
        "Corner Clearance (vehicle parked on corner)",
        "Handicap Ramp (vehicle blocking handicap ramp)"
    )

    val frequencyOptions = listOf(
        "Unsure",
        "Frequently",
        "Not Frequently",
        "Somewhat Often"
    )

    fun canonicalVehicleMake(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val direct = vehicleMakes.firstOrNull { it.equals(normalized, ignoreCase = true) }
        if (direct != null) return direct

        return when (normalized.lowercase()) {
            "chevy" -> "Chevrolet"
            "harley", "harley-davidson", "harley davidson" -> "Harley Davidson"
            "kia" -> "KIA"
            "mercedes-benz", "mercedes benz" -> "Mercedes"
            "mini" -> "Mini Cooper"
            "vw" -> "Volkswagen"
            else -> null
        }
    }
}

@Serializable
data class PhiladelphiaMobilityAccessDetails(
    val blockNumber: String = "",
    val streetName: String = "",
    val zipCode: String = "",
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val bodyStyle: String = "",
    val vehicleColor: String = "",
    val violationObserved: String = "",
    val frequency: String = ""
) {
    val hasAnyValue: Boolean
        get() = listOf(
            blockNumber,
            streetName,
            zipCode,
            vehicleMake,
            vehicleModel,
            bodyStyle,
            vehicleColor,
            violationObserved,
            frequency
        ).any { it.isNotBlank() }
}

data class ReportStatus(
    val id: Int,
    val key: String,
    val text: String
)

@Serializable
data class DraftMedia(
    val uri: String,
    val displayName: String = "",
    val mimeType: String = "",
    val isVideo: Boolean = false
)

@Serializable
data class DraftPlateCandidate(
    val plate: String,
    val confidence: Float,
    val rawPlateText: String? = null,
    val wasPlateCorrected: Boolean = false,
    val state: String? = null,
    val stateConfidence: Float? = null,
    val plateType: String? = null,
    val plateTypeLabel: String? = null,
    val focalPointX: Float? = null,
    val focalPointY: Float? = null,
    val boundsLeft: Float? = null,
    val boundsTop: Float? = null,
    val boundsRight: Float? = null,
    val boundsBottom: Float? = null,
    val rotationDegrees: Float = 0f,
    val cornerPoints: List<Float> = emptyList(),
    val sourceImageWidth: Int? = null,
    val sourceImageHeight: Int? = null,
    val thumbnailUri: String? = null,
    val videoFramePreviewUri: String? = null,
    val videoFrameTimeMs: Long? = null
)

@Serializable
data class ReportDraft(
    val plate: String = "",
    val plateRegion: String = "",
    val address: String = "",
    val description: String = "",
    val notes: String = "",
    val complaintIds: List<String> = emptyList(),
    val occurredAtIso: String = "",
    val selectedComplaintId: String? = null,
    val stage: String = "PICK_MEDIA",
    val primaryMedia: DraftMedia? = null,
    val extraMedia: List<DraftMedia> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val plateCandidates: List<DraftPlateCandidate> = emptyList(),
    val selectedPlateCandidate: String? = null,
    val vehicleImageDescription: String? = null,
    val vehicleColor: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val philadelphiaMobilityAccessDetails: PhiladelphiaMobilityAccessDetails? = null
)

data class SubmitReportCommand(
    val plate: String,
    val plateRegion: String,
    val description: String,
    val notes: String,
    val address: String,
    val complaintIds: List<String>,
    val timeOfIncidentIso: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val vehicleImageDescription: String? = null,
    val vehicleColor: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val mediaFiles: List<SubmitReportMediaFile> = emptyList(),
    val vehicleVin: String? = null,
    val vehicleYear: String? = null,
    val vehicleBodyClass: String? = null,
    val philadelphiaMobilityAccessDetails: PhiladelphiaMobilityAccessDetails? = null
)

data class SubmitReportMediaFile(
    val url: String,
    val isVideo: Boolean = false
)
