package com.reported.shared.api

import com.reported.shared.base.ParseConfig
import com.reported.shared.model.Catalogs
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportStats
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.ReportsPage
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.UserSession
import com.reported.shared.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ReportedApi(
    private val baseUrl: String,
    private val parseConfig: ParseConfig,
    private val client: HttpClient,
    private val sessionStore: SessionStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun login(email: String, password: String): UserSession {
        val dto = client.post("${baseUrl}login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(username = email, password = password))
        }.body<UserDto>()
        return dto.toModel()
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        phone: String,
        testify: Boolean,
        email: String,
        password: String
    ): UserSession {
        val dto = client.post("${baseUrl}register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    firstName = firstName,
                    lastName = lastName,
                    phone = phone,
                    testify = testify,
                    email = email,
                    password = password
                )
            )
        }.body<UserDto>()
        return dto.toModel()
    }

    suspend fun socialLogin(
        provider: String,
        providerUserId: String,
        idToken: String,
        email: String,
        firstName: String,
        lastName: String
    ): UserSession {
        val dto = client.post("${parseBaseUrl()}/users") {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders()
            }
            setBody(
                ParseSocialLoginRequestDto(
                    username = email,
                    email = email,
                    firstName = firstName.ifBlank { null },
                    lastName = lastName.ifBlank { null },
                    authData = mapOf(
                        provider to ParseSocialAuthDataDto(
                            id = providerUserId,
                            idToken = idToken,
                            token = idToken
                        )
                    )
                )
            )
        }.body<UserDto>()
        return dto.toModel()
    }

    suspend fun forgotPassword(email: String) {
        client.post("${baseUrl}forgot_password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequestDto(email))
        }
    }

    suspend fun updateProfile(
        email: String,
        phone: String,
        firstName: String,
        lastName: String
    ): UserSession {
        val dto = client.put("${baseUrl}user/update") {
            contentType(ContentType.Application.Json)
            withSessionHeaders()
            setBody(
                UpdateProfileRequestDto(
                    email = email,
                    phone = phone,
                    firstName = firstName,
                    lastName = lastName
                )
            )
        }.body<UserDto>()
        return dto.toModel()
    }

    suspend fun fetchReports(
        filter: ReportFilter?,
        skip: Int = 0,
        forCurrentUser: Boolean = true
    ): ReportsPage {
        val session = sessionStore.read()
        val response = client.get("${parseBaseUrl()}/classes/submission") {
            headers { appendParseHeaders(sessionToken = session?.sessionToken) }
            parameter("skip", skip)
            parameter("limit", parseReportsPageSize)
            parameter("order", "-timeofincident,-createdAt")
            parameter("keys", parseReportListKeys)
            val where = buildParseReportsWhere(filter = filter, forCurrentUser = forCurrentUser, session = session)
            if (where.isNotEmpty()) {
                parameter("where", where.toString())
            }
        }.body<ParseReportsResponseDto>()

        val reports = response.results.map { it.toModel(emptyMap()) }
        return ReportsPage(
            reports = reports,
            hasMore = reports.size >= parseReportsPageSize
        )
    }

    suspend fun fetchReportDetail(objectId: String): ReportSummary {
        val session = sessionStore.read()
        return client.get("${parseBaseUrl()}/classes/submission/$objectId") {
            headers { appendParseHeaders(sessionToken = session?.sessionToken) }
            parameter("keys", parseReportDetailKeys)
        }.body<ReportDto>().toModel(emptyMap())
    }

    suspend fun fetchReportStats(): ReportStats {
        val raw = client.get("${baseUrl}reports/aggregate") {
            withSessionHeaders()
        }.body<JsonObject>()
        return ReportStats(raw = raw.mapValues { (_, value) -> value.toString() })
    }

    suspend fun submitReport(command: SubmitReportCommand) {
        val session = sessionStore.read() ?: error("Not logged in")
        client.post("${parseBaseUrl()}/classes/submission") {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders(sessionToken = session.sessionToken)
            }
            setBody(buildParseSubmissionBody(command, session))
        }
    }

    suspend fun deleteParseReport(objectId: String) {
        val session = sessionStore.read()
        client.delete("${parseBaseUrl()}/classes/submission/$objectId") {
            headers { appendParseHeaders(sessionToken = session?.sessionToken) }
        }
    }

    suspend fun changeStatus(reportId: Long, status: String) {
        client.put("${baseUrl}report/change_status") {
            contentType(ContentType.Application.Json)
            withSessionHeaders()
            setBody(ChangeStatusRequestDto(id = reportId, status = status))
        }
    }

    suspend fun deleteReport(reportId: Long) {
        client.delete("${baseUrl}report/delete/$reportId") {
            withSessionHeaders()
        }
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.withSessionHeaders() {
        val session = sessionStore.read() ?: return
        headers {
            append("X-User-Id", session.id.toString())
            append("X-Session-Token", session.sessionToken)
            append("X-Operating-System", "native")
        }
    }

    private fun parseBaseUrl(): String {
        val trimmed = parseConfig.serverUrl.trimEnd('/')
        return when {
            trimmed.endsWith("/parse") -> trimmed
            trimmed.contains("parseapi.back4app.com") -> trimmed
            else -> "$trimmed/parse"
        }
    }

    private fun HeadersBuilder.appendParseHeaders(sessionToken: String? = null) {
        append("X-Parse-Application-Id", parseConfig.applicationId)
        append("X-Parse-JavaScript-Key", parseConfig.javascriptKey)
        if (!sessionToken.isNullOrBlank()) {
            append("X-Parse-Session-Token", sessionToken)
        }
    }

    private fun buildParseReportsWhere(
        filter: ReportFilter?,
        forCurrentUser: Boolean,
        session: UserSession?
    ): JsonObject {
        return buildJsonObject {
            if (forCurrentUser && session != null) {
                putJsonArray("\$or") {
                    if (session.objectId.isNotBlank()) {
                        add(buildJsonObject {
                            put("user", buildJsonObject {
                                put("__type", "Pointer")
                                put("className", "_User")
                                put("objectId", session.objectId)
                            })
                        })
                    }
                    if (session.email.isNotBlank()) {
                        add(buildJsonObject { put("Username", session.email) })
                    }
                }
            }
            val license = filter?.license?.trim().orEmpty()
            if (license.isNotBlank()) {
                put("license", buildJsonObject {
                    put("\$regex", Regex.escape(license))
                    put("\$options", "i")
                })
            }
            val start = filter?.startDateIso?.trim().orEmpty()
            val end = filter?.endDateIso?.trim().orEmpty()
            if (start.isNotBlank() || end.isNotBlank()) {
                put("timeofincident", buildJsonObject {
                    if (start.isNotBlank()) put("\$gte", parseDateJson(start.asParseDateStart()))
                    if (end.isNotBlank()) put("\$lte", parseDateJson(end.asParseDateEnd()))
                })
            }
        }
    }

    private fun String.asParseDateStart(): String =
        if (contains("T")) this else "${trim()}T00:00:00.000Z"

    private fun String.asParseDateEnd(): String =
        if (contains("T")) this else "${trim()}T23:59:59.999Z"

    private fun buildParseSubmissionBody(
        command: SubmitReportCommand,
        session: UserSession
    ): JsonObject {
        val complaintName = Catalogs.complaintCategories
            .firstOrNull { it.id == command.complaintIds.firstOrNull() }
            ?.name
            ?: command.complaintIds.firstOrNull().orEmpty()

        return buildJsonObject {
            put("license", command.plate)
            put("state", command.plateRegion)
            put("medallionNo", command.plate)
            put("Username", session.email)
            command.timeOfIncidentIso?.let {
                put("timeofincident", parseDateJson(it))
                put("timeofreported", parseDateJson(it))
                put("timeofreport", parseDateJson(it))
            }
            put("LastName", session.lastName)
            put("FirstName", session.firstName)
            put("Status", 0)
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
            put("loc1_address", command.address)
            put("reportDescription", command.description)
            if (command.notes.isNotBlank()) {
                put("notes", command.notes)
            }
            put("testify", session.testify)
            put("operating_system", "native-kmp")
            put("version_number", "native-kmp")
            if (session.phone.isNotBlank()) {
                put("Phone", session.phone)
            }
            put("Passenger", false)
            if (complaintName.isNotBlank()) {
                put("typeofcomplaint", complaintName)
            }
            command.mediaUrls.forEachIndexed { index, url ->
                put("photoData$index", buildJsonObject {
                    put("__type", "File")
                    put("name", url.substringAfterLast('/').substringBefore('?'))
                    put("url", url)
                })
            }
        }
    }

    private fun parseDateJson(value: String): JsonObject = buildJsonObject {
        put("__type", "Date")
        put("iso", value)
    }

    private fun ReportFilter.toApiFilter(): JsonObject = buildJsonObject {
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

    private companion object {
        const val parseReportListKeys =
            "objectId,createdAt,updatedAt,license,state,timeofreport,timeofreported,timeofincident,Status,status,reqnumber,typeofcomplaint,loc1_address,reportDescription,notes"
        const val parseReportDetailKeys =
            "$parseReportListKeys,photoData0,photoData1,photoData2,PhotoData2,PhotoData3,videoData0,videoData1,videoData2"
        const val parseReportsPageSize = 100
    }
}
