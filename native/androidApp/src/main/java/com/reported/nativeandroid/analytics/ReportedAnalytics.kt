package com.reported.nativeandroid.analytics

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
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
        analytics?.setUserId(session?.analyticsUserId())
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

    fun logSubmitReportTapped(stage: String, isAuthorized: Boolean) {
        logAction(
            "submit_report_tap",
            bundleOf(
                "stage" to stage,
                "is_authorized" to isAuthorized.toLongParam()
            )
        )
    }

    fun logAiSparkleTapped(surface: String) {
        logAction("ai_sparkle_tap", bundleOf("surface" to surface))
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

    fun logSubmitReport(
        county: String,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Boolean
    ) {
        logAction(
            "submit_report",
            bundleOf(
                "county" to county,
                "plate_region" to plateRegion,
                "complaint_count" to complaintCount.toLong(),
                "media_count" to mediaCount.toLong(),
                "has_video" to hasVideo.toLongParam()
            )
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

    private fun UserSession.analyticsUserId(): String? =
        objectId.takeIf { it.isNotBlank() } ?: id.takeIf { it > 0L }?.toString()
}
