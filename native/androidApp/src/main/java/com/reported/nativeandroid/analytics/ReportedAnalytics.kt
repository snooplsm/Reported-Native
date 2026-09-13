package com.reported.nativeandroid.analytics

import android.content.Context
import android.os.Bundle
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
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            }
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
            Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, method)
            }
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
            Bundle().apply {
                putLong("has_license", hasLicense.toLongParam())
                putLong("has_start_date", hasStartDate.toLongParam())
                putLong("has_end_date", hasEndDate.toLongParam())
            }
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
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
                putLong("media_count", mediaCount.toLong())
                putLong("has_video", hasVideo.toLongParam())
            }
        )
    }

    fun logAiSparkleTapped(surface: String) {
        logAction("ai_sparkle_tap", Bundle().apply {
            putString("surface", surface)
        })
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
            Bundle().apply {
                putString("scan_window", scanWindow.firebaseSafeAnalyticsValue())
            }
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
            Bundle().apply {
                putLong("count", count.toLong())
                putLong("report_count", count.toLong())
                putLong("kept_count", keptCount.toLong())
                putLong("discarded_count", discardedCount.toLong())
                putLong("invalid_count", invalidCount.toLong())
                putLong("media_count", mediaCount.toLong())
                putLong("processed_photo_count", processedPhotoCount.toLong())
            }
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
            Bundle().apply {
                putLong("report_index", reportIndex.toLong())
                putLong("report_count", reportCount.toLong())
                putLong("kept_count", keptCount.toLong())
                putLong("media_count", mediaCount.toLong())
            }
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
            Bundle().apply {
                putLong("candidate_count", candidateCount.toLong())
                putLong("has_plate", hasPlate.toLongParam())
            }
        )
    }

    fun logStateChooserTapped(currentRegion: String) {
        logAction("state_chooser_tap", Bundle().apply {
            putString("current_state", currentRegion)
        })
    }

    fun logStateSelected(region: String) {
        logAction("state_selected", Bundle().apply {
            putString("plate_region", region)
        })
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
            Bundle().apply {
                putString("complaint_id", complaintId)
                putString("surface", surface)
            }
        )
    }

    fun logSettingsTapped(surface: String) {
        logAction("settings_tap", Bundle().apply {
            putString("surface", surface)
        })
    }

    fun logBuyMeCoffeeTapped(surface: String) {
        logAction(
            "buy_me_coffee_tap",
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
            }
        )
    }

    fun logBuyMeCoffeeInfoTapped(surface: String) {
        logAction(
            "buy_me_coffee_info_tap",
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
            }
        )
    }

    fun logBuyMeCoffeeOpen(surface: String, source: String) {
        logAction(
            "buy_me_coffee_open",
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
                putString("source", source.firebaseSafeAnalyticsValue())
            }
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
            Bundle().apply {
                putString("provider", provider.firebaseSafeAnalyticsValue())
                putLong("success", success.toLongParam())
                putString("reason", reason.firebaseSafeAnalyticsValue())
                putLong("duration_ms", durationMillis)
                putString("operating_system", operatingSystem.firebaseSafeAnalyticsValue())
            }
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
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
                putString("stage", stage.firebaseSafeAnalyticsValue())
                putLong("success", success.toLongParam())
                putString("reason", reason.firebaseSafeAnalyticsValue())
                putLong("duration_ms", durationMillis)
                putString("plate_prefix", platePrefix.firebaseSafeAnalyticsValue())
                putLong("has_vin", hasVin.toLongParam())
                putLong("has_decoded_vin", hasDecodedVin.toLongParam())
                putString("operating_system", operatingSystem.firebaseSafeAnalyticsValue())
            }
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
            Bundle().apply {
                putString("surface", surface.firebaseSafeAnalyticsValue())
                putString("stage", stage.firebaseSafeAnalyticsValue())
                putString("error_type", error.javaClass.simpleName.firebaseSafeAnalyticsValue())
                putString("error_message", (error.message ?: error.toString()).firebaseSafeAnalyticsValue())
                putString("plate_region", plateRegion.firebaseSafeAnalyticsValue())
                putLong("complaint_count", complaintCount.toLong())
                putLong("media_count", mediaCount.toLong())
                putLong("has_video", hasVideo.toLongParam())
                putLong("report_count", reportCount.toLong())
            }
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
