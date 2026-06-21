package com.reported.nativeandroid.analytics

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.reported.shared.api.VehicleEnrichmentTracker
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.UserSession

object ReportedAnalytics {
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun logAppOpen() {
        analytics?.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
    }

    fun logScreenView(name: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to name,
                FirebaseAnalytics.Param.SCREEN_CLASS to name
            )
        )
    }

    fun logAction(name: String, params: Bundle = Bundle.EMPTY) {
        analytics?.logEvent(name, params)
    }

    fun setUser(session: UserSession?) {
        val userId = session?.analyticsUserId()
        val email = session?.safeEmail()
        val emailDomain = session?.emailDomain()
        analytics?.setUserId(userId)
        analytics?.setUserProperty("reported_user_id", userId)
        analytics?.setUserProperty("email_domain", emailDomain)

        FirebaseCrashlytics.getInstance().apply {
            setUserId(userId.orEmpty())
            setCustomKey("reported_user_id", userId.orEmpty())
            setCustomKey("reported_user_email", email.orEmpty())
            setCustomKey("email_domain", emailDomain.orEmpty())
        }
    }

    fun logLogin(method: String, session: UserSession?) {
        setUser(session)
        logAction(
            FirebaseAnalytics.Event.LOGIN,
            bundleOf(FirebaseAnalytics.Param.METHOD to method)
        )
    }

    fun logLogout() {
        logAction("logout")
        setUser(null)
    }

    fun logReportsList() {
        logAction("reports_list")
    }

    fun logReportsSearch(hasLicense: Boolean, hasStartDate: Boolean, hasEndDate: Boolean) {
        logAction(
            "reports_search",
            bundleOf(
                "has_license" to hasLicense.toLongParam(),
                "has_start_date" to hasStartDate.toLongParam(),
                "has_end_date" to hasEndDate.toLongParam()
            )
        )
    }

    fun logSubmitReportTapped(
        stage: String,
        isAuthorized: Boolean,
        mediaToSubmitMillis: Long? = null
    ) {
        val params = Bundle().apply {
            putString("stage", stage)
            putLong("is_authorized", isAuthorized.toLongParam())
            mediaToSubmitMillis?.let {
                putLong("media_to_submit_ms", it)
                putDouble("media_to_submit_sec", it / 1000.0)
            }
        }
        logAction("submit_report_tap", params)
    }

    fun logReportMediaAdded(surface: String, mediaCount: Int, hasVideo: Boolean) {
        logAction(
            "report_media_added",
            bundleOf(
                "surface" to surface.firebaseSafeAnalyticsValue(),
                "media_count" to mediaCount.toLong(),
                "has_video" to hasVideo.toLongParam()
            )
        )
    }

    fun logAiSparkleTapped(surface: String) {
        logAction("ai_sparkle_tap", bundleOf("surface" to surface))
    }

    fun logReportedAiBulkSubmit(
        surface: String,
        reportCount: Int,
        mediaCount: Int,
        complaintCount: Int,
        scanToSubmitMillis: Long? = null
    ) {
        val params = Bundle().apply {
            putString("surface", surface.firebaseSafeAnalyticsValue())
            putLong("report_count", reportCount.toLong())
            putLong("media_count", mediaCount.toLong())
            putLong("complaint_count", complaintCount.toLong())
            scanToSubmitMillis?.let {
                putLong("scan_to_submit_ms", it)
                putDouble("scan_to_submit_sec", it / 1000.0)
            }
        }
        logAction("reported_ai_bulk_submit", params)
    }

    fun logAutoReportScanStarted(scanWindow: String) {
        logAction(
            "auto_report_scan_start",
            bundleOf("scan_window" to scanWindow.firebaseSafeAnalyticsValue())
        )
    }

    fun logAutoReportSummary(
        count: Int,
        keptCount: Int,
        discardedCount: Int,
        invalidCount: Int,
        mediaCount: Int,
        processedPhotoCount: Int
    ) {
        logAction(
            "auto_report_summary",
            bundleOf(
                "count" to count.toLong(),
                "report_count" to count.toLong(),
                "kept_count" to keptCount.toLong(),
                "discarded_count" to discardedCount.toLong(),
                "invalid_count" to invalidCount.toLong(),
                "media_count" to mediaCount.toLong(),
                "processed_photo_count" to processedPhotoCount.toLong()
            )
        )
    }

    fun logAutoReportDecision(
        keep: Boolean,
        reportIndex: Int,
        reportCount: Int,
        keptCount: Int,
        mediaCount: Int
    ) {
        logAction(
            if (keep) "auto_report_keep" else "auto_report_discard",
            bundleOf(
                "report_index" to reportIndex.toLong(),
                "report_count" to reportCount.toLong(),
                "kept_count" to keptCount.toLong(),
                "media_count" to mediaCount.toLong()
            )
        )
    }

    fun logAutoReportSummarySubmitTapped(
        reportCount: Int,
        mediaCount: Int,
        complaintCount: Int,
        invalidCount: Int,
        isAuthorized: Boolean,
        scanToSubmitMillis: Long? = null
    ) {
        val params = Bundle().apply {
            putLong("count", reportCount.toLong())
            putLong("report_count", reportCount.toLong())
            putLong("media_count", mediaCount.toLong())
            putLong("complaint_count", complaintCount.toLong())
            putLong("invalid_count", invalidCount.toLong())
            putLong("is_authorized", isAuthorized.toLongParam())
            scanToSubmitMillis?.let {
                putLong("scan_to_submit_ms", it)
                putDouble("scan_to_submit_sec", it / 1000.0)
            }
        }
        logAction("auto_report_summary_submit_tap", params)
    }

    fun logPlateChooserTapped(candidateCount: Int, hasPlate: Boolean) {
        logAction(
            "plate_chooser_tap",
            bundleOf(
                "candidate_count" to candidateCount.toLong(),
                "has_plate" to hasPlate.toLongParam()
            )
        )
    }

    fun logStateChooserTapped(currentRegion: String) {
        logAction("state_chooser_tap", bundleOf("current_state" to currentRegion))
    }

    fun logStateSelected(region: String) {
        logAction("state_selected", bundleOf("plate_region" to region))
    }

    fun logComplaintChooserTapped(surface: String, selectedComplaintId: String?) {
        val params = Bundle().apply {
            putString("surface", surface)
            if (!selectedComplaintId.isNullOrBlank()) {
                putString("complaint_id", selectedComplaintId)
            }
        }
        logAction("complaint_chooser_tap", params)
    }

    fun logComplaintSelected(complaintId: String, surface: String) {
        logAction(
            "complaint_selected",
            bundleOf(
                "complaint_id" to complaintId,
                "surface" to surface
            )
        )
    }

    fun logSettingsTapped(surface: String) {
        logAction("settings_tap", bundleOf("surface" to surface))
    }

    fun logBuyMeCoffeeTapped(surface: String) {
        logAction(
            "buy_me_coffee_tap",
            bundleOf("surface" to surface.firebaseSafeAnalyticsValue())
        )
    }

    fun logBuyMeCoffeeInfoTapped(surface: String) {
        logAction(
            "buy_me_coffee_info_tap",
            bundleOf("surface" to surface.firebaseSafeAnalyticsValue())
        )
    }

    fun logBuyMeCoffeeOpen(surface: String, source: String) {
        logAction(
            "buy_me_coffee_open",
            bundleOf(
                "surface" to surface.firebaseSafeAnalyticsValue(),
                "source" to source.firebaseSafeAnalyticsValue()
            )
        )
    }

    fun logSubmitReport(
        county: String,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Boolean,
        mediaToSubmitMillis: Long? = null
    ) {
        val params = Bundle().apply {
            putString("county", county.firebaseSafeAnalyticsValue())
            putString("plate_region", plateRegion.firebaseSafeAnalyticsValue())
            putLong("complaint_count", complaintCount.toLong())
            putLong("media_count", mediaCount.toLong())
            putLong("has_video", hasVideo.toLongParam())
            mediaToSubmitMillis?.let {
                putLong("media_to_submit_ms", it)
                putDouble("media_to_submit_sec", it / 1000.0)
            }
        }
        logAction("submit_report", params)
    }

    fun logVehicleEnrichmentEndpoint(
        provider: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        operatingSystem: String
    ) {
        logAction(
            "vehicle_enrichment_endpoint",
            bundleOf(
                "provider" to provider.firebaseSafeAnalyticsValue(),
                "success" to success.toLongParam(),
                "reason" to reason.firebaseSafeAnalyticsValue(),
                "duration_ms" to durationMillis,
                "operating_system" to operatingSystem.firebaseSafeAnalyticsValue()
            )
        )
    }

    fun logVehicleClassificationResult(
        surface: String,
        stage: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        platePrefix: String,
        hasVin: Boolean,
        hasDecodedVin: Boolean,
        operatingSystem: String
    ) {
        logAction(
            "vehicle_classification_result",
            bundleOf(
                "surface" to surface.firebaseSafeAnalyticsValue(),
                "stage" to stage.firebaseSafeAnalyticsValue(),
                "success" to success.toLongParam(),
                "reason" to reason.firebaseSafeAnalyticsValue(),
                "duration_ms" to durationMillis,
                "plate_prefix" to platePrefix.firebaseSafeAnalyticsValue(),
                "has_vin" to hasVin.toLongParam(),
                "has_decoded_vin" to hasDecodedVin.toLongParam(),
                "operating_system" to operatingSystem.firebaseSafeAnalyticsValue()
            )
        )
    }

    fun logSubmitReportFailed(
        surface: String,
        stage: String,
        error: Throwable,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Boolean,
        reportCount: Int = 1,
        session: UserSession? = null,
        command: SubmitReportCommand? = null
    ) {
        logAction(
            "submit_report_failed",
            bundleOf(
                "surface" to surface.firebaseSafeAnalyticsValue(),
                "stage" to stage.firebaseSafeAnalyticsValue(),
                "error_type" to error.javaClass.simpleName.firebaseSafeAnalyticsValue(),
                "error_message" to (error.message ?: error.toString()).firebaseSafeAnalyticsValue(),
                "plate_region" to plateRegion.firebaseSafeAnalyticsValue(),
                "complaint_count" to complaintCount.toLong(),
                "media_count" to mediaCount.toLong(),
                "has_video" to hasVideo.toLongParam(),
                "report_count" to reportCount.toLong()
            )
        )
        ReportSubmissionFailureLogger.log(
            surface = surface,
            stage = stage,
            error = error,
            plateRegion = plateRegion,
            complaintCount = complaintCount,
            mediaCount = mediaCount,
            hasVideo = hasVideo,
            reportCount = reportCount,
            session = session,
            command = command
        )
    }

    fun logAlprResultSelected(
        plateRegion: String?,
        candidateCount: Int,
        selectedRank: Int,
        confidence: Double?,
        wasCorrected: Boolean
    ) {
        val params = Bundle().apply {
            putString("plate_region", plateRegion ?: "unknown")
            putLong("candidate_count", candidateCount.toLong())
            putLong("selected_rank", selectedRank.toLong())
            putLong("was_corrected", wasCorrected.toLongParam())
            confidence?.let { putDouble("confidence", it) }
        }
        logAction("alpr_result_selected", params)
    }

    private fun Boolean.toLongParam(): Long = if (this) 1L else 0L

    private fun String.firebaseSafeAnalyticsValue(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(96)

    private fun UserSession.analyticsUserId(): String? =
        objectId.takeIf { it.isNotBlank() } ?: id.takeIf { it > 0L }?.toString()

    private fun UserSession.emailDomain(): String? =
        safeEmail()
            .substringAfterLast('@', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?.firebaseSafeAnalyticsValue()

    private fun UserSession.safeEmail(): String =
        email.trim().lowercase().firebaseSafeAnalyticsValue()
}

class FirebaseVehicleEnrichmentTracker : VehicleEnrichmentTracker {
    override fun endpointCompleted(
        provider: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        operatingSystem: String
    ) {
        ReportedAnalytics.logVehicleEnrichmentEndpoint(
            provider = provider,
            success = success,
            reason = reason,
            durationMillis = durationMillis,
            operatingSystem = operatingSystem
        )
    }

    override fun classificationCompleted(
        surface: String,
        stage: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        platePrefix: String,
        hasVin: Boolean,
        hasDecodedVin: Boolean,
        operatingSystem: String
    ) {
        ReportedAnalytics.logVehicleClassificationResult(
            surface = surface,
            stage = stage,
            success = success,
            reason = reason,
            durationMillis = durationMillis,
            platePrefix = platePrefix,
            hasVin = hasVin,
            hasDecodedVin = hasDecodedVin,
            operatingSystem = operatingSystem
        )
    }
}
