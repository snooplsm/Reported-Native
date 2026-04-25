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
    val audience: String
)

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
    val selectedPlateCandidate: String? = null
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
    val mediaUrls: List<String> = emptyList()
)
