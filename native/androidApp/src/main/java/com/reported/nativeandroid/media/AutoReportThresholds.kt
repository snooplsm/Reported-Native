package com.reported.nativeandroid.media

object AutoReportThresholds {
    const val PLATE_CONFIDENCE = 0.65f
    const val POST_INFERENCE_PLATE_CONFIDENCE = 0.65f
    const val STATE_CONFIDENCE = 0.65f
    const val COMPLAINT_CONFIDENCE = 0.65f

    fun summary(): String =
        "Thresholds: plate ${percent(PLATE_CONFIDENCE)}+, state ${percent(STATE_CONFIDENCE)}+, infraction ${percent(COMPLAINT_CONFIDENCE)}+."

    fun percent(value: Float): String =
        value.coerceIn(0f, 0.999f).let { bounded ->
            if (bounded > 0f && bounded < 0.01f) {
                "<1%"
            } else {
                "${(bounded * 100f).toInt()}%"
            }
        }
}
