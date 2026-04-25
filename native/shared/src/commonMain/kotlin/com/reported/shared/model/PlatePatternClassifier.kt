package com.reported.shared.model

enum class PlateType {
    STANDARD,
    TAXI,
    TLC
}

data class PlatePatternMatch(
    val state: String,
    val type: PlateType,
    val confidence: Float,
    val label: String
)

object PlatePatternClassifier {
    private val nyTaxiPattern = Regex("^Y\\d{6}C$")
    private val nyTlcPattern = Regex("^T\\d{6}C$")

    fun classify(rawPlate: String): PlatePatternMatch? {
        val plate = rawPlate
            .filter { it.isLetterOrDigit() }
            .uppercase()

        return when {
            nyTaxiPattern.matches(plate) -> PlatePatternMatch(
                state = "NY",
                type = PlateType.TAXI,
                confidence = 1f,
                label = "NY taxi"
            )
            nyTlcPattern.matches(plate) -> PlatePatternMatch(
                state = "NY",
                type = PlateType.TLC,
                confidence = 1f,
                label = "NY TLC"
            )
            else -> null
        }
    }
}
