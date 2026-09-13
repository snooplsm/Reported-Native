package com.reported.shared.api

import com.reported.shared.base.ParseConfig
import com.reported.shared.model.Catalogs
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.PhiladelphiaMobilityAccessDetails
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportStats
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.ReportsPage
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.PlateType
import com.reported.shared.model.RemoteConfigOverrides
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.UserSession
import com.reported.shared.model.VehicleLookupDetails
import com.reported.shared.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.TimeSource

internal fun buildParseSubmissionBody(
    command: SubmitReportCommand,
    session: UserSession,
    operatingSystem: String,
    versionNumber: Int
): JsonObject {
    val complaintName = Catalogs.complaintCategories
        .firstOrNull { it.id == command.complaintIds.firstOrNull() }
        ?.key
        ?: command.complaintIds.firstOrNull().orEmpty()
    val colorTaxi = if (PlatePatternClassifier.classify(command.plate)?.type in setOf(PlateType.TAXI, PlateType.TLC)) {
        "Black"
    } else {
        "private"
    }

    return buildJsonObject {
        put("license", command.plate)
        put("state", command.plateRegion)
        put("medallionNo", command.plate)
        put("Username", session.email)
        if (session.objectId.isNotBlank()) {
            put("user", buildJsonObject {
                put("__type", "Pointer")
                put("className", "_User")
                put("objectId", session.objectId)
            })
        }
        command.timeOfIncidentIso?.let(::normalizedParseIso)?.let { incidentIso ->
            val incidentDate = parseDateJson(incidentIso)
            put("timeofreport", incidentDate)
        }
        put("LastName", session.lastName)
        put("FirstName", session.firstName)
        put("status", 0)
        command.longitude?.let { put("longitude1", it) }
        command.latitude?.let { put("latitude1", it) }
        command.latitude?.let { put("latitude", it.toString()) }
        command.longitude?.let { put("longitude", it.toString()) }
        if (command.latitude != null && command.longitude != null) {
            put("location", buildJsonObject {
                put("__type", "GeoPoint")
                put("latitude", command.latitude)
                put("longitude", command.longitude)
            })
        }
        put("can_be_shared_publicly", true)
        put("typeofreport", "complaint")
        put("colorTaxi", colorTaxi)
        put("loc1_address", command.address)
        put("reportDescription", command.description)
        command.vehicleImageDescription?.takeIf { it.isNotBlank() }?.let { put("vehicleImageDescription", it) }
        command.vehicleColor?.takeIf { it.isNotBlank() }?.let { put("vehicleColor", it) }
        command.vehicleMake?.takeIf { it.isNotBlank() }?.let { put("vehicleMake", it) }
        command.vehicleModel?.takeIf { it.isNotBlank() }?.let { put("vehicleModel", it) }
        command.vehicleYear?.takeIf { it.isNotBlank() }?.let { put("vehicleYear", it) }
        command.vehicleBodyClass?.takeIf { it.isNotBlank() }?.let { put("vehicleBodyClass", it) }
        if (command.notes.isNotBlank()) {
            put("notes", command.notes)
        }
        put("testify", session.testify)
        put("operating_system", operatingSystem)
        put("version_number", versionNumber)
        if (session.phone.isNotBlank()) {
            put("Phone", session.phone)
        }
        put("Passenger", false)
        put("passenger", false)
        if (complaintName.isNotBlank()) {
            put("typeofcomplaint", complaintName)
        }
        val typedMedia = command.mediaFiles.ifEmpty {
            command.mediaUrls.map { url ->
                com.reported.shared.model.SubmitReportMediaFile(url = url, isVideo = false)
            }
        }
        var photoIndex = 0
        var videoIndex = 0
        typedMedia.forEach { media ->
            val fieldName = if (media.isVideo) {
                "videoData${videoIndex++}"
            } else {
                "photoData${photoIndex++}"
            }
            put(fieldName, buildJsonObject {
                put("__type", "File")
                put("name", media.url.substringAfterLast('/').substringBefore('?'))
                put("url", media.url)
            })
        }
    }
}


internal fun buildPhiladelphiaSubmissionBody(
    details: PhiladelphiaMobilityAccessDetails,
    submissionId: String,
    submissionClassName: String
): JsonObject = buildJsonObject {
    put("submissionId", submissionId)
    put("submission", parsePointer(className = submissionClassName, objectId = submissionId))
    put("jurisdiction", CityReportingRules.PHILADELPHIA_CITY_ID)
    put("form", "philadelphia_mobility_access")
    put("blockNumber", details.blockNumber)
    put("streetName", details.streetName)
    put("zipCode", details.zipCode)
    put("vehicleMake", details.vehicleMake)
    put("vehicleModel", details.vehicleModel)
    put("bodyStyle", details.bodyStyle)
    put("vehicleColor", details.vehicleColor)
    put("violationObserved", details.violationObserved)
    put("frequency", details.frequency)
}


internal fun buildVehicleClassificationPayload(
    lookupPlate: String,
    tlcVehicle: TlcVehicleDto,
    decodedVin: VpicVehicleDto?
): VehicleClassificationPayload =
    VehicleClassificationPayload(
        license = lookupPlate,
        dmvLicensePlateNumber = tlcVehicle.dmvLicensePlateNumber.normalizedPlateOrFallback(lookupPlate),
        vehicleVinNumber = tlcVehicle.vehicleVinNumber.normalizedVin(),
        vehicleLicenseNumber = tlcVehicle.vehicleLicenseNumber.normalizedText()
            ?: tlcVehicle.licenseNumber.normalizedText(),
        baseAddress = tlcVehicle.baseAddress.normalizedText(),
        active = tlcVehicle.active.asActiveBoolean() ?: tlcVehicle.currentStatus.asActiveBoolean(),
        baseType = tlcVehicle.baseType.normalizedText() ?: tlcVehicle.vehicleType.normalizedText(),
        name = tlcVehicle.name.normalizedText(),
        make = decodedVin?.make.normalizedText(),
        model = decodedVin?.model.normalizedText(),
        bodyClass = decodedVin?.bodyClass.normalizedText(),
        baseTelephoneNumber = tlcVehicle.baseTelephoneNumber.normalizedText(),
        baseNumber = tlcVehicle.baseNumber.normalizedText() ?: tlcVehicle.agentNumber.normalizedText(),
        licenseType = tlcVehicle.licenseType.normalizedText() ?: tlcVehicle.medallionType.normalizedText(),
        year = (
            decodedVin?.modelYear.normalizedText()
                ?: tlcVehicle.vehicleYear?.trim()?.takeIf { it.isNotBlank() }
                ?: tlcVehicle.modelYear?.trim()?.takeIf { it.isNotBlank() }
            )?.toIntOrNull()
    )


internal fun buildVehicleClassificationBody(
    payload: VehicleClassificationPayload,
    submissionId: String,
    submissionClassName: String
): JsonObject {
    return buildJsonObject {
        put("submissionId", submissionId)
        put("submission", parsePointer(className = submissionClassName, objectId = submissionId))
        put("license", payload.license)
        put("dmv_license_plate_number", payload.dmvLicensePlateNumber)
        put("vehicle_vin_number", payload.vehicleVinNumber)
        payload.vehicleLicenseNumber?.let { put("vehicle_license_number", it) }
        payload.baseAddress?.let { put("base_address", it) }
        payload.active?.let { put("active", it) }
        payload.baseType?.let { put("base_type", it) }
        payload.name?.let { put("name", it) }
        payload.make?.let { put("make", it) }
        payload.model?.let { put("model", it) }
        payload.bodyClass?.let { put("vehicle_class", it) }
        payload.baseTelephoneNumber?.let { put("base_telephone_number", it) }
        payload.baseNumber?.let { put("base_number", it) }
        payload.licenseType?.let { put("license_type", it) }
        payload.year?.let { put("year", it) }
    }
}


internal fun Throwable.analyticsReason(): String =
    when (this) {
        is ResponseException -> "http_${response.status.value}"
        else -> this::class.simpleName ?: "error"
    }

internal fun String?.normalizedVin(): String =
    orEmpty()
        .filter { it.isLetterOrDigit() }
        .uppercase()

internal fun String?.normalizedPlateOrFallback(fallback: String): String =
    normalizedText()?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.isNotBlank() } ?: fallback

internal fun String?.normalizedText(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

internal fun String?.asActiveBoolean(): Boolean? =
    when (this?.trim()?.uppercase()) {
        "YES", "TRUE", "1", "ACTIVE", "CUR", "CURRENT" -> true
        "NO", "FALSE", "0", "INACTIVE", "INACT", "EXPIRED" -> false
        else -> null
    }

internal fun vehicleEnrichmentDebugNote(note: String): String =
    "[DEBUG] $note"

internal fun PhiladelphiaMobilityAccessDetails.toParseJson(): JsonObject = buildJsonObject {
    put("blockNumber", blockNumber)
    put("streetName", streetName)
    put("zipCode", zipCode)
    put("vehicleMake", vehicleMake)
    put("vehicleModel", vehicleModel)
    put("bodyStyle", bodyStyle)
    put("vehicleColor", vehicleColor)
    put("violationObserved", violationObserved)
    put("frequency", frequency)
}

internal fun SubmitReportCommand.withVehicleEnrichmentDebugNote(note: String): SubmitReportCommand {
    val debugNote = vehicleEnrichmentDebugNote(note)
    val nextNotes = listOf(notes.trim(), debugNote)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    return copy(notes = nextNotes)
}

internal fun VehicleClassificationPayload.debugSummary(): String {
    val parts = buildList {
        add("Vehicle classification")
        add("license=$license")
        add("vin=$vehicleVinNumber")
        year?.let { add("year=$it") }
        make?.let { add("make=$it") }
        model?.let { add("model=$it") }
        bodyClass?.let { add("vehicle_class=$it") }
        baseType?.let { add("base_type=$it") }
        baseNumber?.let { add("base_number=$it") }
        licenseType?.let { add("license_type=$it") }
        active?.let { add("active=$it") }
        vehicleLicenseNumber?.let { add("vehicle_license_number=$it") }
    }
    return parts.joinToString(", ")
}

internal fun VehicleClassificationPayload.hasDecodedVehicle(): Boolean =
    make != null || model != null || bodyClass != null || year != null

internal fun parseDateJson(value: String): JsonObject = buildJsonObject {
    put("__type", "Date")
    put("iso", value)
}

internal fun parsePointer(className: String, objectId: String): JsonObject = buildJsonObject {
    put("__type", "Pointer")
    put("className", className)
    put("objectId", objectId)
}

internal fun normalizedParseIso(value: String): String? =
    runCatching { Instant.parse(value.trim()).toString() }.getOrNull()

internal fun ReportFilter.toApiFilter(): JsonObject = buildJsonObject {
    if (keywords.isNotBlank()) put("keywords", keywords)
    if (srid.isNotBlank()) put("srid", srid)
    if (complaints.isNotEmpty()) {
        putJsonArray("complaints") {
            complaints.forEach { add(JsonPrimitive(it)) }
        }
    }
    if (whenDescription.isNotBlank()) put("when", whenDescription)
    if (locationDescription.isNotBlank()) put("location", locationDescription)
}


internal data class VehicleEnrichmentResult(
val command: SubmitReportCommand,
val vehicleClassification: VehicleClassificationPayload? = null
)

internal data class VehicleClassificationPayload(
val license: String,
val dmvLicensePlateNumber: String,
val vehicleVinNumber: String,
val vehicleLicenseNumber: String?,
val baseAddress: String?,
val active: Boolean?,
val baseType: String?,
val name: String?,
val make: String?,
val model: String?,
val bodyClass: String?,
val baseTelephoneNumber: String?,
val baseNumber: String?,
val licenseType: String?,
val year: Int?
)

@Serializable
internal data class TlcVehicleDto(
@SerialName("dmv_license_plate_number")
val dmvLicensePlateNumber: String? = null,
@SerialName("vehicle_vin_number")
val vehicleVinNumber: String? = null,
@SerialName("vehicle_year")
val vehicleYear: String? = null,
@SerialName("vehicle_license_number")
val vehicleLicenseNumber: String? = null,
@SerialName("license_number")
val licenseNumber: String? = null,
@SerialName("base_address")
val baseAddress: String? = null,
@SerialName("active")
val active: String? = null,
@SerialName("current_status")
val currentStatus: String? = null,
@SerialName("base_type")
val baseType: String? = null,
@SerialName("vehicle_type")
val vehicleType: String? = null,
@SerialName("name")
val name: String? = null,
@SerialName("base_telephone_number")
val baseTelephoneNumber: String? = null,
@SerialName("base_number")
val baseNumber: String? = null,
@SerialName("license_type")
val licenseType: String? = null,
@SerialName("model_year")
val modelYear: String? = null,
@SerialName("medallion_type")
val medallionType: String? = null,
@SerialName("agent_number")
val agentNumber: String? = null,
@SerialName("agent_name")
val agentName: String? = null
)

@Serializable
internal data class VpicDecodeResponseDto(
@SerialName("Results")
val results: List<VpicVehicleDto> = emptyList()
)

@Serializable
internal data class VpicVehicleDto(
@SerialName("Make")
val make: String? = null,
@SerialName("Model")
val model: String? = null,
@SerialName("ModelYear")
val modelYear: String? = null,
@SerialName("BodyClass")
val bodyClass: String? = null
)
