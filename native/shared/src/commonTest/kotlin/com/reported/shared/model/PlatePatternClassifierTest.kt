package com.reported.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlatePatternClassifierTest {
    @Test
    fun correctsNyTlcDigitSlotOcrConfusions() {
        val match = assertNotNull(PlatePatternClassifier.classify("TI0Z3SBC"))

        assertEquals("T102358C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TLC, match.type)
    }

    @Test
    fun correctsNyTlcPrefixWhenOcrReadsTAsOne() {
        val match = assertNotNull(PlatePatternClassifier.classify("1657280C"))

        assertEquals("T657280C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TLC, match.type)
    }

    @Test
    fun correctsNyTaxiDigitSlotOcrConfusions() {
        val match = assertNotNull(PlatePatternClassifier.classify("YI0Z3SBC"))

        assertEquals("Y102358C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TAXI, match.type)
    }

    @Test
    fun infersMissingTrailingCForNyTlcPlate() {
        val match = assertNotNull(PlatePatternClassifier.classify("TI0Z3SB"))

        assertEquals("T102358C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TLC, match.type)
    }

    @Test
    fun suggestsCorrectionForNearNyTlcPlate() {
        val match = assertNotNull(PlatePatternClassifier.suggestedCorrection("T133837"))

        assertEquals("T133837C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TLC, match.type)
    }

    @Test
    fun doesNotSuggestCorrectionForAlreadyCanonicalNyTlcPlate() {
        assertNull(PlatePatternClassifier.suggestedCorrection("T133837C"))
    }

    @Test
    fun infersMissingTrailingCForNyTaxiPlate() {
        val match = assertNotNull(PlatePatternClassifier.classify("YI0Z3SB"))

        assertEquals("Y102358C", match.normalizedPlate)
        assertEquals("NY", match.state)
        assertEquals(PlateType.TAXI, match.type)
    }

    @Test
    fun doesNotRewriteNonForHirePlateShapes() {
        assertNull(PlatePatternClassifier.classify("ABC12IO"))
    }

    @Test
    fun handlesBlankPlate() {
        assertNull(PlatePatternClassifier.classify(""))
        assertNull(PlatePatternClassifier.suggestedCorrection(""))
    }
}
