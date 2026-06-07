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
    val label: String,
    val normalizedPlate: String
)

object PlatePatternClassifier {
    const val MAX_LICENSE_PLATE_LENGTH = 10

    private val nyTaxiPattern = Regex("^Y\\d{6}C$")
    private val nyTlcPattern = Regex("^T\\d{6}C$")

    fun classify(rawPlate: String): PlatePatternMatch? {
        val plate = normalizePlateInput(rawPlate)
        val correctedPlate = correctNyForHirePlate(plate)

        return when {
            nyTaxiPattern.matches(correctedPlate) -> PlatePatternMatch(
                state = "NY",
                type = PlateType.TAXI,
                confidence = 1f,
                label = "NY taxi",
                normalizedPlate = correctedPlate
            )
            nyTlcPattern.matches(correctedPlate) -> PlatePatternMatch(
                state = "NY",
                type = PlateType.TLC,
                confidence = 1f,
                label = "NY TLC",
                normalizedPlate = correctedPlate
            )
            else -> null
        }
    }

    fun suggestedCorrection(rawPlate: String): PlatePatternMatch? {
        val plate = normalizePlateInput(rawPlate)
        if (plate.isBlank()) return null
        val match = classify(plate) ?: return null
        return match.takeIf { it.normalizedPlate != plate }
    }

    fun isValidForSubmission(rawPlate: String): Boolean {
        val plate = normalizePlateInput(rawPlate)
        if (plate.length <= MAX_LICENSE_PLATE_LENGTH) return true
        return false
    }

    fun normalizePlateInput(rawPlate: String): String =
        rawPlate
            .filter { it.isLetterOrDigit() }
            .uppercase()

    private fun correctNyForHirePlate(plate: String): String {
        if (plate.isBlank()) return plate
        val prefix = plate.first().toLikelyForHirePrefix() ?: return plate

        val hasTrailingC = plate.length == 8 && plate.last() == 'C'
        val missingTrailingC = plate.length == 7
        if (!hasTrailingC && !missingTrailingC) {
            return plate
        }

        val digits = plate.substring(1, 7).map { it.toLikelyDigit() }
        if (digits.any { it == null }) return plate

        return "$prefix${digits.joinToString("")}C"
    }

    private fun Char.toLikelyDigit(): Char? =
        when (this) {
            in '0'..'9' -> this
            'O', 'Q', 'D' -> '0'
            'I', 'L' -> '1'
            'Z' -> '2'
            'E' -> '3'
            'A' -> '4'
            'S' -> '5'
            'G' -> '6'
            'T' -> '7'
            'B' -> '8'
            else -> null
        }

    private fun Char.toLikelyForHirePrefix(): Char? =
        when (this) {
            'T', 'Y' -> this
            '1', 'I', 'L' -> 'T'
            else -> null
        }
}
