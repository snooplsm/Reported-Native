package com.reported.nativeandroid.media

import android.content.Context

object AutoReportThresholds {
    private const val PREFS = "reported.auto_report.thresholds"
    private const val KEY_PLATE_CONFIDENCE = "plate_confidence"
    private const val KEY_POST_INFERENCE_PLATE_CONFIDENCE = "post_inference_plate_confidence"
    private const val KEY_STATE_CONFIDENCE = "state_confidence"
    private const val KEY_COMPLAINT_CONFIDENCE = "complaint_confidence"

    const val DEFAULT_PLATE_CONFIDENCE = 0.65f
    const val DEFAULT_POST_INFERENCE_PLATE_CONFIDENCE = 0.65f
    const val DEFAULT_STATE_CONFIDENCE = 0.65f
    const val DEFAULT_COMPLAINT_CONFIDENCE = 0.65f
    const val MIN_CONFIDENCE = 0.5f
    const val MAX_CONFIDENCE = 0.99f

    fun summary(): String =
        "Thresholds: plate ${percent(DEFAULT_PLATE_CONFIDENCE)}+, state ${percent(DEFAULT_STATE_CONFIDENCE)}+, infraction ${percent(DEFAULT_COMPLAINT_CONFIDENCE)}+."

    fun summary(context: Context): String =
        "Thresholds: plate ${percent(plateConfidence(context))}+, state ${percent(stateConfidence(context))}+, infraction ${percent(complaintConfidence(context))}+."

    fun plateConfidence(context: Context): Float =
        get(context, KEY_PLATE_CONFIDENCE, DEFAULT_PLATE_CONFIDENCE)

    fun postInferencePlateConfidence(context: Context): Float =
        get(context, KEY_POST_INFERENCE_PLATE_CONFIDENCE, DEFAULT_POST_INFERENCE_PLATE_CONFIDENCE)

    fun stateConfidence(context: Context): Float =
        get(context, KEY_STATE_CONFIDENCE, DEFAULT_STATE_CONFIDENCE)

    fun complaintConfidence(context: Context): Float =
        get(context, KEY_COMPLAINT_CONFIDENCE, DEFAULT_COMPLAINT_CONFIDENCE)

    fun setPlateConfidence(context: Context, value: Float) {
        set(context, KEY_PLATE_CONFIDENCE, value)
    }

    fun setPostInferencePlateConfidence(context: Context, value: Float) {
        set(context, KEY_POST_INFERENCE_PLATE_CONFIDENCE, value)
    }

    fun setStateConfidence(context: Context, value: Float) {
        set(context, KEY_STATE_CONFIDENCE, value)
    }

    fun setComplaintConfidence(context: Context, value: Float) {
        set(context, KEY_COMPLAINT_CONFIDENCE, value)
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .remove(KEY_PLATE_CONFIDENCE)
            .remove(KEY_POST_INFERENCE_PLATE_CONFIDENCE)
            .remove(KEY_STATE_CONFIDENCE)
            .remove(KEY_COMPLAINT_CONFIDENCE)
            .apply()
    }

    fun percent(value: Float): String =
        value.coerceIn(0f, 0.999f).let { bounded ->
            if (bounded > 0f && bounded < 0.01f) {
                "<1%"
            } else {
                "${(bounded * 100f).toInt()}%"
            }
        }

    private fun get(context: Context, key: String, defaultValue: Float): Float =
        prefs(context).getFloat(key, defaultValue).coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)

    private fun set(context: Context, key: String, value: Float) {
        prefs(context).edit().putFloat(key, value.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
