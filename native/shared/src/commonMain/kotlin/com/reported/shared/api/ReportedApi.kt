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
import kotlinx.datetime.Instant
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

class ReportedApi(
    private val baseUrl: String,
    private val parseConfig: ParseConfig,
    private val operatingSystem: String,
    private val client: HttpClient,
    private val sessionStore: SessionStore,
    private val vehicleEnrichmentTracker: VehicleEnrichmentTracker = NoOpVehicleEnrichmentTracker,
    private val vehicleEnrichmentPolicy: VehicleEnrichmentPolicy = AlwaysAttemptVehicleEnrichmentPolicy,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun login(email: String, password: String): UserSession {
        val dto = client.get("${parseBaseUrl()}/login") {
            headers {
                appendParseHeaders()
            }
            parameter("username", email)
            parameter("password", password)
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
        val dto = client.post("${parseBaseUrl()}/users") {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders()
            }
            setBody(
                ParseRegisterRequestDto(
                    username = email,
                    email = email,
                    password = password,
                    firstName = firstName,
                    lastName = lastName,
                    phone = phone,
                    testify = testify
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
        lastName: String,
        phone: String = "",
        testify: Boolean = false
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
                    phone = phone.ifBlank { null },
                    testify = testify,
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
        client.post("${parseBaseUrl()}/requestPasswordReset") {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders()
            }
            setBody(ForgotPasswordRequestDto(email))
        }
    }

    suspend fun updateProfile(
        email: String,
        phone: String,
        firstName: String,
        lastName: String,
        testify: Boolean
    ): UserSession {
        val session = sessionStore.read()
        return if (session?.objectId?.isNotBlank() == true) {
            client.put("${parseBaseUrl()}/users/${session.objectId}") {
                contentType(ContentType.Application.Json)
                headers {
                    appendParseHeaders(sessionToken = session.sessionToken)
                }
                setBody(
                    ParseUpdateProfileRequestDto(
                        email = email,
                        username = email,
                        phone = phone,
                        firstName = firstName,
                        lastName = lastName,
                        testify = testify
                    )
                )
            }
            session.copy(
                email = email,
                phone = phone,
                firstName = firstName,
                lastName = lastName,
                testify = testify
            )
        } else {
            val dto = client.put("${apiBaseUrl()}user/update") {
                contentType(ContentType.Application.Json)
                withSessionHeaders()
                setBody(
                    UpdateProfileRequestDto(
                        email = email,
                        phone = phone,
                        firstName = firstName,
                        lastName = lastName,
                        testify = testify
                    )
                )
            }.body<UserDto>()
            dto.toModel()
        }
    }

    suspend fun fetchReports(
        filter: ReportFilter?,
        skip: Int = 0,
        forCurrentUser: Boolean = true
    ): ReportsPage {
        val session = sessionStore.read()
        val where = buildParseReportsWhere(filter = filter, forCurrentUser = forCurrentUser, session = session)
        println("ReportedReports: GET submissions skip=$skip filter=$filter where=$where")
        val response = client.get("${parseBaseUrl()}/classes/submission") {
            headers { appendParseHeaders(sessionToken = session?.sessionToken) }
            parameter("skip", skip)
            parameter("limit", parseReportsPageSize)
            parameter("order", "-timeofincident,-createdAt")
            parameter("keys", parseReportListKeys)
            if (where.isNotEmpty()) {
                parameter("where", where.toString())
            }
        }.body<ParseReportsResponseDto>()

        val reports = response.results.map { it.toModel(emptyMap()) }
        println("ReportedReports: fetched ${reports.size} submission(s); hasMore=${reports.size >= parseReportsPageSize}")
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
        val raw = client.get("${apiBaseUrl()}reports/aggregate") {
            withSessionHeaders()
        }.body<JsonObject>()
        return ReportStats(raw = raw.mapValues { (_, value) -> value.toString() })
    }

    suspend fun submitReport(command: SubmitReportCommand): String {
        val session = sessionStore.read() ?: error("Not logged in")
        val enrichedReport = command.enrichForHireVehicleDetails()
        val endpoint = "${parseBaseUrl()}/classes/$parseDefaultSubmissionClassName"
        println("ReportedSubmit: POST $endpoint")
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders(sessionToken = session.sessionToken)
            }
            setBody(buildParseSubmissionBody(enrichedReport.command, session, operatingSystem, nativeVersionNumber))
        }
        val body = response.bodyAsText()
        println("ReportedSubmit: POST submission completed status=${response.status} body=$body")
        val created = json.decodeFromString<ParseCreateResponseDto>(body)
        val submissionId = created.objectId.ifBlank { error("Submission succeeded but no report id was returned.") }
        enrichedReport.command.philadelphiaMobilityAccessDetails
            ?.takeIf { it.hasAnyValue }
            ?.let { details ->
                savePhiladelphiaSubmissionDetails(details, submissionId, session.sessionToken)
            }
        enrichedReport.vehicleClassification?.let {
            saveVehicleClassification(it, submissionId, session.sessionToken)
        }
        return submissionId
    }

    suspend fun lookupVehicleDetails(plate: String, licenseState: String): VehicleLookupDetails? {
        val normalizedPlate = PlatePatternClassifier.normalizePlateInput(plate)
            .takeIf { it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH }
            ?: return null
        val normalizedState = licenseState.trim().uppercase()
            .takeIf { it.matches(Regex("^[A-Z]{2}$")) }
            ?: return null
        val start = TimeSource.Monotonic.markNow()
        return try {
            val responseBody = withTimeoutOrNull(vehicleLookupTimeoutMillis) {
                client.get("$reportedWebVehicleLookupUrl/$normalizedPlate/$normalizedState") {
                    headers {
                        append("Accept", "application/json")
                    }
                }.bodyAsText()
            } ?: error("Vehicle lookup timed out")
            val details = decodeVehicleLookupResponse(
                json = json,
                responseBody = responseBody,
                requestedPlate = normalizedPlate,
                requestedState = normalizedState
            )
            trackVehicleEnrichmentEndpoint(
                provider = lookupAPlateProvider,
                success = details != null,
                reason = if (details == null) "no_match" else "ok",
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            details
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            trackVehicleEnrichmentEndpoint(
                provider = lookupAPlateProvider,
                success = false,
                reason = error.analyticsReason(),
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            throw error
        }
    }

    suspend fun previewVehicleEnrichmentDebugNote(plate: String): String? {
        if (!vehicleEnrichmentPolicy.includeDebugSummaryInNotes()) {
            return null
        }
        val lookupPlate = vehicleEnrichmentLookupPlate(plate) ?: return null

        if (!vehicleEnrichmentPolicy.canAttemptVehicleEnrichment()) {
            trackVehicleEnrichmentEndpoint(
                provider = vehicleEnrichmentProvider,
                success = false,
                reason = "wifi_unavailable",
                durationMillis = 0
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationPreviewSurface,
                stage = vehicleClassificationLookupStage,
                success = false,
                reason = "wifi_unavailable",
                durationMillis = 0,
                lookupPlate = lookupPlate
            )
            return vehicleEnrichmentDebugNote("Vehicle classification skipped: Wi-Fi unavailable.")
        }

        val enrichmentStart = TimeSource.Monotonic.markNow()
        val debugNote = withTimeoutOrNull(vehicleEnrichmentTimeoutMillis) {
            runCatching {
                val tlcVehicle = fetchTlcVehicle(lookupPlate)
                    ?: return@runCatching vehicleEnrichmentDebugNote(
                        "Vehicle classification skipped: no TLC active vehicle match for $lookupPlate."
                    ).also {
                        trackVehicleEnrichmentEndpoint(
                            provider = vehicleEnrichmentProvider,
                            success = false,
                            reason = "no_tlc_match",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                        )
                        trackVehicleClassificationResult(
                            surface = vehicleClassificationPreviewSurface,
                            stage = vehicleClassificationLookupStage,
                            success = false,
                            reason = "no_tlc_match",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                            lookupPlate = lookupPlate
                        )
                    }
                val vin = tlcVehicle.vehicleVinNumber.normalizedVin()
                if (vin.isBlank()) {
                    return@runCatching vehicleEnrichmentDebugNote(
                        "Vehicle classification skipped: TLC active vehicle match for $lookupPlate has no VIN."
                    ).also {
                        trackVehicleEnrichmentEndpoint(
                            provider = vehicleEnrichmentProvider,
                            success = false,
                            reason = "no_vin",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                        )
                        trackVehicleClassificationResult(
                            surface = vehicleClassificationPreviewSurface,
                            stage = vehicleClassificationLookupStage,
                            success = false,
                            reason = "no_vin",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                            lookupPlate = lookupPlate
                        )
                    }
                }
                val decodedVin = runCatching {
                    decodeVehicleVin(vin)
                }.onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    println("ReportedSubmit: vPIC vehicle enrichment preview failed: ${error.message}")
                }.getOrNull()
                val classification = buildVehicleClassificationPayload(
                    lookupPlate = lookupPlate,
                    tlcVehicle = tlcVehicle,
                    decodedVin = decodedVin
                )
                vehicleEnrichmentDebugNote(classification.debugSummary()).also {
                    trackVehicleEnrichmentEndpoint(
                        provider = vehicleEnrichmentProvider,
                        success = true,
                        reason = "ok",
                        durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                    )
                    trackVehicleClassificationResult(
                        surface = vehicleClassificationPreviewSurface,
                        stage = vehicleClassificationLookupStage,
                        success = true,
                        reason = "ok",
                        durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                        lookupPlate = lookupPlate,
                        hasVin = true,
                        hasDecodedVin = decodedVin != null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                println("ReportedSubmit: vehicle enrichment preview failed: ${error.message}")
                trackVehicleEnrichmentEndpoint(
                    provider = vehicleEnrichmentProvider,
                    success = false,
                    reason = error.analyticsReason(),
                    durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                )
                trackVehicleClassificationResult(
                    surface = vehicleClassificationPreviewSurface,
                    stage = vehicleClassificationLookupStage,
                    success = false,
                    reason = error.analyticsReason(),
                    durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                    lookupPlate = lookupPlate
                )
            }.getOrElse {
                vehicleEnrichmentDebugNote("Vehicle classification failed: ${it.analyticsReason()}.")
            }
        }
        if (debugNote == null) {
            trackVehicleEnrichmentEndpoint(
                provider = vehicleEnrichmentProvider,
                success = false,
                reason = "timeout",
                durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationPreviewSurface,
                stage = vehicleClassificationLookupStage,
                success = false,
                reason = "timeout",
                durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                lookupPlate = lookupPlate
            )
        }
        return debugNote ?: vehicleEnrichmentDebugNote("Vehicle classification skipped: timed out.")
    }

    suspend fun deleteParseReport(objectId: String) {
        val session = sessionStore.read() ?: error("Not logged in")
        val endpoint = "${parseBaseUrl()}/classes/submission/$objectId"
        println("ReportedReports: DELETE $endpoint")
        try {
            val response = client.delete(endpoint) {
                headers { appendParseHeaders(sessionToken = session.sessionToken) }
            }
            val body = response.bodyAsText()
            println("ReportedReports: DELETE submission completed status=${response.status} body=$body")
        } catch (error: ResponseException) {
            val body = error.response.bodyAsText()
            println("ReportedReports: DELETE submission failed status=${error.response.status} body=$body")
            if (
                error.response.status.value == 404 ||
                body.contains("\"code\":101") ||
                body.contains("Object not found", ignoreCase = true)
            ) {
                return
            }
            if (error.response.status.value >= 500 && !parseSubmissionExists(objectId, session.sessionToken)) {
                println("ReportedReports: DELETE returned ${error.response.status}, but submission no longer exists; treating as deleted")
                return
            }
            throw IllegalStateException("Unable to delete report: ${error.response.status}. $body", error)
        }
    }

    private suspend fun parseSubmissionExists(objectId: String, sessionToken: String): Boolean {
        return try {
            val response = client.get("${parseBaseUrl()}/classes/submission/$objectId") {
                headers { appendParseHeaders(sessionToken = sessionToken) }
                parameter("keys", "objectId")
            }
            println("ReportedReports: DELETE verification found submission status=${response.status}")
            true
        } catch (error: ResponseException) {
            val body = error.response.bodyAsText()
            println("ReportedReports: DELETE verification status=${error.response.status} body=$body")
            !(
                error.response.status.value == 404 ||
                    body.contains("\"code\":101") ||
                    body.contains("Object not found", ignoreCase = true)
            )
        }
    }

    suspend fun changeStatus(reportId: Long, status: String) {
        client.put("${apiBaseUrl()}report/change_status") {
            contentType(ContentType.Application.Json)
            withSessionHeaders()
            setBody(ChangeStatusRequestDto(id = reportId, status = status))
        }
    }

    suspend fun deleteReport(reportId: Long) {
        client.delete("${apiBaseUrl()}report/delete/$reportId") {
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

    private fun apiBaseUrl(): String =
        RemoteConfigOverrides.apiBaseUrl(baseUrl)

    private fun parseBaseUrl(): String {
        val trimmed = RemoteConfigOverrides.parseServerUrl(parseConfig.serverUrl).trimEnd('/')
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

    private suspend fun savePhiladelphiaSubmissionDetails(
        details: PhiladelphiaMobilityAccessDetails,
        submissionId: String,
        sessionToken: String
    ) {
        val endpoint = "${parseBaseUrl()}/classes/$parsePhiladelphiaSubmissionClassName"
        println("ReportedSubmit: POST $endpoint")
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            headers {
                appendParseHeaders(sessionToken = sessionToken)
            }
            setBody(buildPhiladelphiaSubmissionBody(details, submissionId, parseDefaultSubmissionClassName))
        }
        val body = response.bodyAsText()
        println("ReportedSubmit: POST $parsePhiladelphiaSubmissionClassName completed status=${response.status} body=$body")
    }

    private suspend fun SubmitReportCommand.enrichForHireVehicleDetails(): VehicleEnrichmentResult {
        val lookupPlate = vehicleEnrichmentLookupPlate(plate)
        if (lookupPlate == null) {
            return VehicleEnrichmentResult(command = this)
        }
        val originalCommand = this

        if (!vehicleEnrichmentPolicy.canAttemptVehicleEnrichment()) {
            trackVehicleEnrichmentEndpoint(
                provider = vehicleEnrichmentProvider,
                success = false,
                reason = "wifi_unavailable",
                durationMillis = 0
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationSubmissionSurface,
                stage = vehicleClassificationLookupStage,
                success = false,
                reason = "wifi_unavailable",
                durationMillis = 0,
                lookupPlate = lookupPlate
            )
            return VehicleEnrichmentResult(
                command = if (vehicleEnrichmentPolicy.includeDebugSummaryInNotes()) {
                    originalCommand.withVehicleEnrichmentDebugNote("Vehicle classification skipped: Wi-Fi unavailable.")
                } else {
                    originalCommand
                }
            )
        }

        val enrichmentStart = TimeSource.Monotonic.markNow()
        val enrichmentResult = withTimeoutOrNull<VehicleEnrichmentResult>(vehicleEnrichmentTimeoutMillis) {
            runCatching<VehicleEnrichmentResult> {
                val tlcVehicle = fetchTlcVehicle(lookupPlate)
                    ?: return@runCatching VehicleEnrichmentResult(
                        command = if (vehicleEnrichmentPolicy.includeDebugSummaryInNotes()) {
                            originalCommand.withVehicleEnrichmentDebugNote(
                                "Vehicle classification skipped: no TLC active vehicle match for $lookupPlate."
                            )
                        } else {
                            originalCommand
                        }
                    ).also {
                        trackVehicleEnrichmentEndpoint(
                            provider = vehicleEnrichmentProvider,
                            success = false,
                            reason = "no_tlc_match",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                        )
                        trackVehicleClassificationResult(
                            surface = vehicleClassificationSubmissionSurface,
                            stage = vehicleClassificationLookupStage,
                            success = false,
                            reason = "no_tlc_match",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                            lookupPlate = lookupPlate
                        )
                    }
                val vin = tlcVehicle.vehicleVinNumber.normalizedVin()
                if (vin.isBlank()) {
                    return@runCatching VehicleEnrichmentResult(
                        command = if (vehicleEnrichmentPolicy.includeDebugSummaryInNotes()) {
                            originalCommand.withVehicleEnrichmentDebugNote(
                                "Vehicle classification skipped: TLC active vehicle match for $lookupPlate has no VIN."
                            )
                        } else {
                            originalCommand
                        }
                    ).also {
                        trackVehicleEnrichmentEndpoint(
                            provider = vehicleEnrichmentProvider,
                            success = false,
                            reason = "no_vin",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                        )
                        trackVehicleClassificationResult(
                            surface = vehicleClassificationSubmissionSurface,
                            stage = vehicleClassificationLookupStage,
                            success = false,
                            reason = "no_vin",
                            durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                            lookupPlate = lookupPlate
                        )
                    }
                }

                val decodedVin = runCatching {
                    decodeVehicleVin(vin)
                }.onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    println("ReportedSubmit: vPIC vehicle enrichment failed: ${error.message}")
                }.getOrNull()
                val classification = buildVehicleClassificationPayload(
                    lookupPlate = lookupPlate,
                    tlcVehicle = tlcVehicle,
                    decodedVin = decodedVin
                )
                VehicleEnrichmentResult(
                    command = if (vehicleEnrichmentPolicy.includeDebugSummaryInNotes()) {
                        originalCommand.withVehicleEnrichmentDebugNote(classification.debugSummary())
                    } else {
                        originalCommand
                    },
                    vehicleClassification = classification
                ).also {
                    trackVehicleEnrichmentEndpoint(
                        provider = vehicleEnrichmentProvider,
                        success = true,
                        reason = "ok",
                        durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
                    )
                    trackVehicleClassificationResult(
                        surface = vehicleClassificationSubmissionSurface,
                        stage = vehicleClassificationLookupStage,
                        success = true,
                        reason = "ok",
                        durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                        lookupPlate = lookupPlate,
                        hasVin = true,
                        hasDecodedVin = decodedVin != null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                println("ReportedSubmit: TLC vehicle enrichment failed: ${error.message}")
                trackVehicleClassificationResult(
                    surface = vehicleClassificationSubmissionSurface,
                    stage = vehicleClassificationLookupStage,
                    success = false,
                    reason = error.analyticsReason(),
                    durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                    lookupPlate = lookupPlate
                )
            }.getOrElse { VehicleEnrichmentResult(command = originalCommand) }
        }
        if (enrichmentResult == null) {
            trackVehicleEnrichmentEndpoint(
                provider = vehicleEnrichmentProvider,
                success = false,
                reason = "timeout",
                durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationSubmissionSurface,
                stage = vehicleClassificationLookupStage,
                success = false,
                reason = "timeout",
                durationMillis = enrichmentStart.elapsedNow().inWholeMilliseconds,
                lookupPlate = lookupPlate
            )
        }
        return enrichmentResult ?: VehicleEnrichmentResult(command = originalCommand)
    }

    private fun vehicleEnrichmentLookupPlate(rawPlate: String): String? {
        val plateMatch = PlatePatternClassifier.classify(rawPlate)
        if (plateMatch != null && plateMatch.type in setOf(PlateType.TAXI, PlateType.TLC)) {
            return plateMatch.normalizedPlate
        }
        val normalizedPlate = PlatePatternClassifier.normalizePlateInput(rawPlate)
        return normalizedPlate.takeIf {
            it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH &&
                (it.startsWith("T") || it.startsWith("Y"))
        }
    }

    private suspend fun saveVehicleClassification(
        payload: VehicleClassificationPayload,
        submissionId: String,
        sessionToken: String
    ) {
        val endpoint = "${parseBaseUrl()}/classes/vehicle_classification"
        val start = TimeSource.Monotonic.markNow()
        try {
            println("ReportedSubmit: POST $endpoint")
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                headers {
                    appendParseHeaders(sessionToken = sessionToken)
                }
                setBody(buildVehicleClassificationBody(payload, submissionId, parseDefaultSubmissionClassName))
            }
            val body = response.bodyAsText()
            println("ReportedSubmit: POST vehicle_classification completed status=${response.status} body=$body")
            trackVehicleEnrichmentEndpoint(
                provider = vehicleClassificationProvider,
                success = true,
                reason = "ok",
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationSubmissionSurface,
                stage = vehicleClassificationSaveStage,
                success = true,
                reason = "ok",
                durationMillis = start.elapsedNow().inWholeMilliseconds,
                lookupPlate = payload.license,
                hasVin = payload.vehicleVinNumber.isNotBlank(),
                hasDecodedVin = payload.hasDecodedVehicle()
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            println("ReportedSubmit: vehicle_classification save failed: ${error.message}")
            trackVehicleEnrichmentEndpoint(
                provider = vehicleClassificationProvider,
                success = false,
                reason = error.analyticsReason(),
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            trackVehicleClassificationResult(
                surface = vehicleClassificationSubmissionSurface,
                stage = vehicleClassificationSaveStage,
                success = false,
                reason = error.analyticsReason(),
                durationMillis = start.elapsedNow().inWholeMilliseconds,
                lookupPlate = payload.license,
                hasVin = payload.vehicleVinNumber.isNotBlank(),
                hasDecodedVin = payload.hasDecodedVehicle()
            )
        }
    }

    private suspend fun fetchTlcVehicle(plate: String): TlcVehicleDto? {
        return fetchTlcVehicleFromDataset(
            plate = plate,
            url = tlcActiveVehiclesUrl,
            provider = tlcActiveVehiclesProvider,
            select = "dmv_license_plate_number,vehicle_vin_number,vehicle_license_number,vehicle_year,base_address,active,base_type,name,base_telephone_number,base_number,license_type"
        ) ?: fetchTlcVehicleFromDataset(
            plate = plate,
            url = tlcMedallionVehiclesUrl,
            provider = tlcMedallionVehiclesProvider,
            select = "license_number,name,current_status,dmv_license_plate_number,vehicle_vin_number,vehicle_type,model_year,medallion_type,agent_number,agent_name"
        )
    }

    private suspend fun fetchTlcVehicleFromDataset(
        plate: String,
        url: String,
        provider: String,
        select: String
    ): TlcVehicleDto? {
        val start = TimeSource.Monotonic.markNow()
        return try {
            val responseBody = client.get(url) {
                parameter("dmv_license_plate_number", plate)
                parameter("\$select", select)
                parameter("\$limit", "5")
            }.bodyAsText()
            val vehicle = json.decodeFromString<List<TlcVehicleDto>>(responseBody)
                .firstOrNull { it.dmvLicensePlateNumber.equals(plate, ignoreCase = true) }
            trackVehicleEnrichmentEndpoint(
                provider = provider,
                success = vehicle != null,
                reason = if (vehicle == null) "no_match" else "ok",
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            vehicle
        } catch (error: Throwable) {
            trackVehicleEnrichmentEndpoint(
                provider = provider,
                success = false,
                reason = error.analyticsReason(),
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            throw error
        }
    }

    private suspend fun decodeVehicleVin(vin: String): VpicVehicleDto? {
        val start = TimeSource.Monotonic.markNow()
        return try {
            val responseBody = client.get("$vpicDecodeVinValuesExtendedUrl/$vin") {
                parameter("format", "json")
            }.bodyAsText()
            val vehicle = json.decodeFromString<VpicDecodeResponseDto>(responseBody).results.firstOrNull()
            trackVehicleEnrichmentEndpoint(
                provider = vpicDecodeVinProvider,
                success = vehicle != null,
                reason = if (vehicle == null) "no_result" else "ok",
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            vehicle
        } catch (error: Throwable) {
            trackVehicleEnrichmentEndpoint(
                provider = vpicDecodeVinProvider,
                success = false,
                reason = error.analyticsReason(),
                durationMillis = start.elapsedNow().inWholeMilliseconds
            )
            throw error
        }
    }

    private fun trackVehicleEnrichmentEndpoint(
        provider: String,
        success: Boolean,
        reason: String,
        durationMillis: Long
    ) {
        vehicleEnrichmentTracker.endpointCompleted(
            provider = provider,
            success = success,
            reason = reason.take(64),
            durationMillis = durationMillis,
            operatingSystem = operatingSystem
        )
    }

    private fun trackVehicleClassificationResult(
        surface: String,
        stage: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        lookupPlate: String,
        hasVin: Boolean = false,
        hasDecodedVin: Boolean = false
    ) {
        vehicleEnrichmentTracker.classificationCompleted(
            surface = surface,
            stage = stage,
            success = success,
            reason = reason.take(64),
            durationMillis = durationMillis,
            platePrefix = lookupPlate.firstOrNull()?.uppercaseChar()?.toString() ?: "unknown",
            hasVin = hasVin,
            hasDecodedVin = hasDecodedVin,
            operatingSystem = operatingSystem
        )
    }

    private companion object {
        const val parseDefaultSubmissionClassName = "submission"
        const val parsePhiladelphiaSubmissionClassName = "submissions_philly"
        const val parseReportListKeys =
            "objectId,createdAt,updatedAt,license,state,timeofreport,timeofreported,timeofincident,status,reqnumber,typeofcomplaint,loc1_address,reportDescription,notes"
        const val parseReportDetailKeys =
            "$parseReportListKeys,photoData0,photoData1,photoData2,PhotoData2,PhotoData3,videoData0,videoData1,videoData2"
        const val parseReportsPageSize = 100
        const val nativeVersionNumber = 90
        const val vehicleEnrichmentTimeoutMillis = 5_000L
        const val vehicleLookupTimeoutMillis = 5_000L
        const val vehicleEnrichmentProvider = "vehicle_enrichment"
        const val lookupAPlateProvider = "lookup_a_plate"
        const val tlcActiveVehiclesProvider = "tlc_active_vehicles"
        const val tlcMedallionVehiclesProvider = "tlc_medallion_vehicles"
        const val vpicDecodeVinProvider = "vpic_decode_vin"
        const val vehicleClassificationProvider = "vehicle_classification"
        const val vehicleClassificationPreviewSurface = "preview"
        const val vehicleClassificationSubmissionSurface = "submission"
        const val vehicleClassificationLookupStage = "lookup"
        const val vehicleClassificationSaveStage = "save"
        const val tlcActiveVehiclesUrl = "https://data.cityofnewyork.us/resource/8wbx-tsch.json"
        const val tlcMedallionVehiclesUrl = "https://data.cityofnewyork.us/resource/rhe8-mgbb.json"
        const val vpicDecodeVinValuesExtendedUrl = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValuesExtended"
        const val reportedWebVehicleLookupUrl = "https://reported-web.herokuapp.com/getVehicleType"
    }
}
