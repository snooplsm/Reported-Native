package com.reported.shared.api

import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class ReportPrivacyTest {
    private val command = SubmitReportCommand(
        plate = "ABC1234", plateRegion = "NY", description = "", notes = "",
        address = "New York", complaintIds = listOf("Z8vjWz8uYr")
    )

    @Test fun oldSessionsDefaultToPrivateAndDoNotWithhold() {
        val session = Json.decodeFromString<UserSession>("""{"email":"test@example.com"}""")
        assertFalse(session.canBeSharedPublicly)
        assertFalse(session.withholdContactInfoFromNypd)
        val payload = buildParseSubmissionBody(command, session, "android", 105)
        assertEquals(JsonPrimitive(false), payload["can_be_shared_publicly"])
        assertFalse(payload.containsKey("withhold_contact_info_from_nypd"))
    }

    @Test fun choicesAreIndependentAndWithholdingIsSparse() {
        for (share in listOf(false, true)) for (withhold in listOf(false, true)) {
            val session = UserSession(canBeSharedPublicly = share, withholdContactInfoFromNypd = withhold)
            val payload = buildParseSubmissionBody(command, session, "ios", 105)
            assertEquals(JsonPrimitive(share), payload["can_be_shared_publicly"])
            assertEquals(if (withhold) JsonPrimitive(true) else null, payload["withhold_contact_info_from_nypd"])
            assertEquals(session, Json.decodeFromString<UserSession>(Json.encodeToString(UserSession.serializer(), session)))
        }
    }

    @Test fun contactRequirementsRespectWithholdingAndTlc() {
        val blank = UserSession()
        assertEquals(listOf("first name", "last name", "email", "phone"), blank.missingContactFields)
        assertFailsWith<IllegalArgumentException> { blank.requireReportContact("ABC1234", false) }
        val withheld = blank.copy(withholdContactInfoFromNypd = true)
        withheld.requireReportContact("ABC1234", false)
        assertFailsWith<IllegalArgumentException> { withheld.requireReportContact("T747846C", false) }
        assertFailsWith<IllegalArgumentException> { withheld.requireReportContact("ABC1234", true) }
        blank.copy(firstName = "Test", lastName = "User", email = "test@example.com", phone = "2125551234")
            .requireReportContact("T747846C", false)
    }

    @Test fun explicitFalseProfilePreferencesAreSentToServer() {
        val body = Json.encodeToString(ParseUpdateProfileRequestDto.serializer(), ParseUpdateProfileRequestDto(
            email = "test@example.com", username = "test@example.com", phone = "", firstName = "", lastName = "",
            testify = false, canBeSharedPublicly = false, withholdContactInfoFromNypd = false
        ))
        assertTrue(body.contains("\"can_be_shared_publicly\":false"))
        assertTrue(body.contains("\"withhold_contact_info_from_nypd\":false"))
    }

    @Test fun profilePreferencesAreRestoredFromServer() {
        val user = Json.decodeFromString<UserDto>("""{"can_be_shared_publicly":true,"withhold_contact_info_from_nypd":true}""").toModel()
        assertTrue(user.canBeSharedPublicly)
        assertTrue(user.withholdContactInfoFromNypd)
    }
}
