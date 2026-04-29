package com.reported.shared.api

import com.reported.shared.model.Address
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.UserSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class UserDto(
    val id: Long? = null,
    val objectId: String? = null,
    val email: String? = null,
    val username: String? = null,
    @SerialName("FirstName") val parseFirstName: String? = null,
    @SerialName("LastName") val parseLastName: String? = null,
    @SerialName("Phone") val parsePhone: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val sessionToken: String? = null,
    val testify: Boolean? = null
)

internal fun UserDto.toModel(): UserSession = UserSession(
    id = id ?: 0,
    objectId = objectId.orEmpty(),
    email = email ?: username.orEmpty(),
    firstName = firstName ?: parseFirstName.orEmpty(),
    lastName = lastName ?: parseLastName.orEmpty(),
    phone = phone ?: parsePhone.orEmpty(),
    sessionToken = sessionToken.orEmpty(),
    testify = testify ?: false
)

@Serializable
internal data class ReportDto(
    val id: Long? = null,
    val objectId: String? = null,
    val addressId: Long? = null,
    @SerialName("timeofincident") val timeOfIncident: JsonElement? = null,
    @SerialName("timeofreport") val timeOfReport: JsonElement? = null,
    @SerialName("timeofreported") val timeOfReported: JsonElement? = null,
    val description: String? = null,
    @SerialName("reportDescription") val reportDescription: String? = null,
    val notes: String? = null,
    val plate: String? = null,
    val license: String? = null,
    val state: String? = null,
    @SerialName("Status") val parseStatus: JsonElement? = null,
    val status: JsonElement? = null
    ,
    val reqnumber: String? = null,
    val typeofcomplaint: String? = null,
    val loc1_address: String? = null,
    val location: JsonElement? = null,
    val photoData0: JsonElement? = null,
    val photoData1: JsonElement? = null,
    val photoData2: JsonElement? = null,
    @SerialName("PhotoData2") val upperPhotoData2: JsonElement? = null,
    @SerialName("PhotoData3") val upperPhotoData3: JsonElement? = null,
    val videoData0: JsonElement? = null,
    val videoData1: JsonElement? = null,
    val videoData2: JsonElement? = null
)

@Serializable
internal data class AddressDto(
    val id: Long? = null,
    val street: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

internal fun AddressDto.toModel(): Address = Address(
    id = id ?: 0,
    street = street.orEmpty(),
    city = city.orEmpty(),
    state = state.orEmpty(),
    zip = zip.orEmpty(),
    lat = lat,
    lng = lng
)

internal fun ReportDto.toModel(addresses: Map<Long, Address>): ReportSummary {
    val address = addressId?.let(addresses::get)
    return ReportSummary(
        id = id ?: 0,
        objectId = objectId.orEmpty(),
        addressId = addressId,
        incidentAt = timeOfIncident.asParseDate() ?: timeOfReport.asParseDate() ?: timeOfReported.asParseDate().orEmpty(),
        description = reportDescription ?: description.orEmpty(),
        notes = notes.orEmpty(),
        plate = license ?: plate.orEmpty(),
        plateRegion = state.orEmpty(),
        status = parseStatus.asStatusText().ifBlank { status.asStatusText() },
        statusCode = parseStatus.asStatusCode() ?: status.asStatusCode(),
        street = loc1_address ?: address?.street.orEmpty(),
        complaint = typeofcomplaint.orEmpty(),
        requestNumber = reqnumber.orEmpty(),
        mediaUrls = listOfNotNull(
            photoData0.asParseFileUrl(),
            photoData1.asParseFileUrl(),
            photoData2.asParseFileUrl(),
            upperPhotoData2.asParseFileUrl(),
            upperPhotoData3.asParseFileUrl()
        ).distinct(),
        videoUrls = listOfNotNull(
            videoData0.asParseFileUrl(),
            videoData1.asParseFileUrl(),
            videoData2.asParseFileUrl()
        ).distinct()
    )
}

private fun JsonElement?.asStatusText(): String {
    return when (this) {
        null -> ""
        is JsonPrimitive -> contentOrNull?.asStatusLabel().orEmpty()
        is JsonObject -> this["name"]?.jsonPrimitive?.contentOrNull
            ?: this["key"]?.jsonPrimitive?.contentOrNull
            ?: this["text"]?.jsonPrimitive?.contentOrNull
            ?: this["sId"]?.jsonPrimitive?.contentOrNull?.asStatusLabel()
            ?: this.toString()
        else -> toString()
    }
}

private fun JsonElement?.asStatusCode(): Int? =
    when (this) {
        null -> null
        is JsonPrimitive -> intOrNull ?: contentOrNull?.toIntOrNull()
        is JsonObject -> this["sId"]?.jsonPrimitive?.intOrNull
            ?: this["sId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: this["id"]?.jsonPrimitive?.intOrNull
            ?: this["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        else -> null
    }

private fun String.asStatusLabel(): String =
    when (this) {
        "0", "1" -> "Submitted"
        "-3", "-2" -> "Processing"
        "-1" -> "Error Pending"
        "2" -> "Summons Issued"
        "3" -> "Hearing Scheduled"
        "4" -> "Driver Paid Fine / Guilty"
        "5" -> "Unable to ID Driver"
        "6" -> "Driver Not Guilty"
        "7" -> "No Reason / Archive"
        else -> this
    }

private fun JsonElement?.asParseDate(): String? =
    when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["iso"]?.jsonPrimitive?.contentOrNull
        else -> null
    }

private fun JsonElement?.asParseFileUrl(): String? =
    when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["url"]?.jsonPrimitive?.contentOrNull
        else -> null
    }

@Serializable
internal data class ReportsResponseDto(
    val reports: List<ReportDto> = emptyList(),
    val addresses: List<AddressDto> = emptyList()
)

@Serializable
internal data class ParseReportsResponseDto(
    val results: List<ReportDto> = emptyList()
)

@Serializable
internal data class ParseCreateResponseDto(
    val objectId: String = "",
    val createdAt: String? = null
)

@Serializable
internal data class ForgotPasswordRequestDto(val email: String)

@Serializable
internal data class LoginRequestDto(val username: String, val password: String)

@Serializable
internal data class ParseRegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    @SerialName("Phone") val phone: String,
    @SerialName("FirstName") val firstName: String,
    @SerialName("LastName") val lastName: String,
    val testify: Boolean
)

@Serializable
internal data class UpdateProfileRequestDto(
    val email: String,
    val phone: String,
    val firstName: String,
    val lastName: String,
    val testify: Boolean
)

@Serializable
internal data class ParseUpdateProfileRequestDto(
    val email: String,
    val username: String,
    @SerialName("Phone") val phone: String,
    @SerialName("FirstName") val firstName: String,
    @SerialName("LastName") val lastName: String,
    val testify: Boolean
)

@Serializable
internal data class ParseSocialLoginRequestDto(
    val username: String,
    val email: String,
    val authData: Map<String, ParseSocialAuthDataDto>,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("Phone") val phone: String? = null,
    val testify: Boolean? = null
)

@Serializable
internal data class ParseSocialAuthDataDto(
    val id: String,
    @SerialName("id_token") val idToken: String? = null,
    val token: String? = null
)

@Serializable
internal data class SubmitReportRequestDto(
    val complaintIds: List<String>,
    val license: LicenseDto,
    val address: ReportAddressDto,
    @SerialName("timeofincident") val timeOfIncidentIso: String? = null,
    val media: List<String> = emptyList(),
    val plate: String,
    val description: String,
    val notes: String
)

@Serializable
internal data class LicenseDto(
    val plate: String,
    val state: String
)

@Serializable
internal data class ReportAddressDto(
    val formatted_address: String,
    val location: ReportLocationDto
)

@Serializable
internal data class ReportLocationDto(
    val lat: Double,
    val lng: Double
)

@Serializable
internal data class ChangeStatusRequestDto(
    val id: Long,
    val status: String
)
