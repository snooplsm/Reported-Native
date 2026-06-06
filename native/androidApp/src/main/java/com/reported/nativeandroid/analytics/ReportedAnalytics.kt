package com.reported.nativeandroid.analytics

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

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

    fun logLogin(method: String) {
        logAction(
            FirebaseAnalytics.Event.LOGIN,
            bundleOf(FirebaseAnalytics.Param.METHOD to method)
        )
    }

    fun logLogout() {
        logAction("logout")
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
}
