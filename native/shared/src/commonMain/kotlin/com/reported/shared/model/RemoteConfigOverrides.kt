package com.reported.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object RemoteConfigKeys {
    const val API_BASE_URL = "reported_api_base_url"
    const val PARSE_SERVER_URL = "reported_parse_server_url"
    const val COMPLAINT_CATEGORIES = "reported_complaint_categories"
    const val REPORT_STATUSES = "reported_report_statuses"
    const val SHOW_COMPLAINT_IMAGES = "reported_show_complaint_images"
    const val ENABLE_LIVE = "reported_enable_live"
    const val ENABLE_MEDIA_SCANNER = "reported_enable_media_scanner"
    const val ENABLE_OFFLINE_PHOTO_PROCESSING = "reported_enable_offline_photo_processing"
}

object RemoteConfigDefaults {
    const val API_BASE_URL = "https://reported-stats.herokuapp.com/prod/"
    const val PARSE_SERVER_URL = "https://parseapi.back4app.com"
    const val SHOW_COMPLAINT_IMAGES = true
    const val ENABLE_LIVE = false
    const val ENABLE_MEDIA_SCANNER = false
    const val ENABLE_OFFLINE_PHOTO_PROCESSING = false

    val complaintCategoriesJson: String
        get() = Json.encodeToString(
            ListSerializer(RemoteComplaintCategoryDto.serializer()),
            Catalogs.defaultComplaintCategories.map {
                RemoteComplaintCategoryDto(
                    objectId = it.id,
                    text = it.name,
                    audience = it.audience
                )
            }
        )

    val reportStatusesJson: String
        get() = Json.encodeToString(
            ListSerializer(RemoteReportStatusDto.serializer()),
            Catalogs.defaultReportStatuses
                .filterNot { it.id == 0 || it.id == 1 }
                .map {
                    RemoteReportStatusDto(
                        id = it.id,
                        key = it.key,
                        text = it.text
                    )
                }
        )
}

object RemoteConfigOverrides {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var apiBaseUrlOverride: String? = null
    private var parseServerUrlOverride: String? = null
    private var showComplaintImagesOverride: Boolean? = null
    private var enableLiveOverride: Boolean? = null
    private var enableMediaScannerOverride: Boolean? = null
    private var enableOfflinePhotoProcessingOverride: Boolean? = null

    var complaintCategories: List<ComplaintCategory>? = null
        private set

    var reportStatuses: List<ReportStatus>? = null
        private set

    val showComplaintImages: Boolean
        get() = showComplaintImagesOverride ?: RemoteConfigDefaults.SHOW_COMPLAINT_IMAGES

    val enableLive: Boolean
        get() = enableLiveOverride ?: RemoteConfigDefaults.ENABLE_LIVE

    val enableMediaScanner: Boolean
        get() = enableMediaScannerOverride ?: RemoteConfigDefaults.ENABLE_MEDIA_SCANNER

    val enableOfflinePhotoProcessing: Boolean
        get() = enableOfflinePhotoProcessingOverride ?: RemoteConfigDefaults.ENABLE_OFFLINE_PHOTO_PROCESSING

    fun apply(
        apiBaseUrl: String?,
        parseServerUrl: String?,
        complaintCategoriesJson: String?,
        reportStatusesJson: String?,
        showComplaintImages: String?,
        enableLive: String? = null,
        enableMediaScanner: String? = null,
        enableOfflinePhotoProcessing: String? = null
    ) {
        apiBaseUrlOverride = apiBaseUrl?.trim()?.takeIf { it.isNotBlank() }
        parseServerUrlOverride = parseServerUrl?.trim()?.takeIf { it.isNotBlank() }
        showComplaintImagesOverride = parseBoolean(showComplaintImages)
        enableLiveOverride = parseBoolean(enableLive)
        enableMediaScannerOverride = parseBoolean(enableMediaScanner)
        enableOfflinePhotoProcessingOverride = parseBoolean(enableOfflinePhotoProcessing)
        complaintCategories = parseComplaintCategories(complaintCategoriesJson)
        reportStatuses = parseReportStatuses(reportStatusesJson)
    }

    fun apiBaseUrl(fallback: String): String =
        ensureTrailingSlash(apiBaseUrlOverride ?: fallback)

    fun parseServerUrl(fallback: String): String =
        parseServerUrlOverride ?: fallback

    private fun parseComplaintCategories(value: String?): List<ComplaintCategory>? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString(
                ListSerializer(RemoteComplaintCategoryDto.serializer()),
                raw
            ).mapNotNull { dto ->
                val id = (dto.objectId ?: dto.id)?.trim().orEmpty()
                val text = (dto.text ?: dto.name)?.trim().orEmpty()
                if (id.isBlank() || text.isBlank()) {
                    null
                } else {
                    ComplaintCategory(
                        id = id,
                        name = text,
                        audience = dto.audience?.trim().orEmpty()
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parseReportStatuses(value: String?): List<ReportStatus>? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val remoteStatuses = json.decodeFromString(
                ListSerializer(RemoteReportStatusDto.serializer()),
                raw
            ).mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                val text = dto.text?.trim().orEmpty()
                if (id == 0 || id == 1 || text.isBlank()) {
                    null
                } else {
                    ReportStatus(
                        id = id,
                        key = dto.key?.trim().takeUnless { it.isNullOrBlank() } ?: text.uppercase().replace(" ", "_"),
                        text = text
                    )
                }
            }
            (Catalogs.protectedReportStatuses + remoteStatuses).takeIf { remoteStatuses.isNotEmpty() }
        }.getOrNull()
    }

    private fun ensureTrailingSlash(value: String): String =
        if (value.endsWith('/')) value else "$value/"

    private fun parseBoolean(value: String?): Boolean? =
        value?.trim()?.lowercase()?.let {
            when (it) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        }
}

@Serializable
private data class RemoteComplaintCategoryDto(
    val objectId: String? = null,
    val id: String? = null,
    val text: String? = null,
    val name: String? = null,
    val audience: String? = null
)

@Serializable
private data class RemoteReportStatusDto(
    val id: Int? = null,
    val key: String? = null,
    val text: String? = null
)
