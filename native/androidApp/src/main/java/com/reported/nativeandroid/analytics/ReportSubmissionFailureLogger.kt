package com.reported.nativeandroid.analytics

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.reported.nativeandroid.BuildConfig
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.UserSession

object ReportSubmissionFailureLogger {
    private const val TAG = "ReportedSubmitFailure"
    private const val COLLECTION = "report_submission_errors"
    private const val MAX_ERROR_MESSAGE_LENGTH = 1_000
    private const val MAX_STACK_TRACE_LENGTH = 8_000

    fun log(
        surface: String,
        stage: String,
        error: Throwable,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Boolean,
        reportCount: Int,
        session: UserSession? = null,
        command: SubmitReportCommand? = null
    ) {
        val payload = linkedMapOf<String, Any?>(
            "createdAt" to FieldValue.serverTimestamp(),
            "clientCreatedAtMs" to System.currentTimeMillis(),
            "platform" to "android",
            "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "surface" to surface.cleanValue(96),
            "stage" to stage.cleanValue(96),
            "userId" to session?.analyticsUserId(),
            "userEmail" to session?.safeEmail(),
            "error" to mapOf(
                "type" to error.javaClass.name.cleanValue(200),
                "message" to (error.message ?: error.toString()).cleanValue(MAX_ERROR_MESSAGE_LENGTH),
                "causeType" to error.cause?.javaClass?.name?.cleanValue(200),
                "causeMessage" to error.cause?.message?.cleanValue(MAX_ERROR_MESSAGE_LENGTH),
                "stackTrace" to Log.getStackTraceString(error).cleanValue(MAX_STACK_TRACE_LENGTH)
            ),
            "summary" to mapOf(
                "plateRegion" to plateRegion.cleanValue(16),
                "complaintCount" to complaintCount,
                "mediaCount" to mediaCount,
                "hasVideo" to hasVideo,
                "reportCount" to reportCount
            ),
            "report" to command?.toFailureLogMap()
        ).filterValues { it != null }

        runCatching {
            FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .add(payload)
                .addOnFailureListener { firestoreError ->
                    Log.w(TAG, "Failed to save submit failure to Firestore", firestoreError)
                }
        }.onFailure { firestoreError ->
            Log.w(TAG, "Unable to enqueue submit failure Firestore write", firestoreError)
        }
    }

    private fun SubmitReportCommand.toFailureLogMap(): Map<String, Any?> =
        linkedMapOf(
            "plate" to plate.cleanValue(32),
            "plateRegion" to plateRegion.cleanValue(16),
            "address" to address.cleanValue(500),
            "complaintIds" to complaintIds.map { it.cleanValue(100) },
            "timeOfIncidentIso" to timeOfIncidentIso?.cleanValue(64),
            "latitude" to latitude,
            "longitude" to longitude,
            "descriptionLength" to description.length,
            "notesLength" to notes.length,
            "mediaUrlCount" to mediaUrls.size,
            "mediaFileCount" to mediaFiles.size,
            "mediaFiles" to mediaFiles.map { media ->
                mapOf(
                    "urlPresent" to media.url.isNotBlank(),
                    "isVideo" to media.isVideo
                )
            },
            "hasVehicleImageDescription" to !vehicleImageDescription.isNullOrBlank(),
            "vehicleColor" to vehicleColor?.cleanValue(64),
            "vehicleMake" to vehicleMake?.cleanValue(64),
            "vehicleModel" to vehicleModel?.cleanValue(64)
        ).filterValues { it != null }

    private fun String.cleanValue(maxLength: Int): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(maxLength)

    private fun UserSession.analyticsUserId(): String? =
        objectId.takeIf { it.isNotBlank() } ?: id.takeIf { it > 0L }?.toString()

    private fun UserSession.safeEmail(): String? =
        email.trim()
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?.cleanValue(200)
}
