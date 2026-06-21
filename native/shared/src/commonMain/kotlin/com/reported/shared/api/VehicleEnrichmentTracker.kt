package com.reported.shared.api

interface VehicleEnrichmentTracker {
    fun endpointCompleted(
        provider: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        operatingSystem: String
    )

    fun classificationCompleted(
        surface: String,
        stage: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        platePrefix: String,
        hasVin: Boolean,
        hasDecodedVin: Boolean,
        operatingSystem: String
    )
}

object NoOpVehicleEnrichmentTracker : VehicleEnrichmentTracker {
    override fun endpointCompleted(
        provider: String,
        success: Boolean,
        reason: String,
        durationMillis: Long,
        operatingSystem: String
    ) = Unit

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
    ) = Unit
}

interface VehicleEnrichmentPolicy {
    fun canAttemptVehicleEnrichment(): Boolean
    fun includeDebugSummaryInNotes(): Boolean
}

object AlwaysAttemptVehicleEnrichmentPolicy : VehicleEnrichmentPolicy {
    override fun canAttemptVehicleEnrichment(): Boolean = true
    override fun includeDebugSummaryInNotes(): Boolean = false
}
