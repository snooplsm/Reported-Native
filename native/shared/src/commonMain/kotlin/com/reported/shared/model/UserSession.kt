package com.reported.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val id: Long = 0,
    val objectId: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val sessionToken: String = "",
    val avatarUrl: String? = null,
    val testify: Boolean = false,
    val canBeSharedPublicly: Boolean = true,
    val withholdContactInfoFromNypd: Boolean = false
) {
    val isAuthorized: Boolean
        get() = (id > 0 || objectId.isNotBlank()) && sessionToken.isNotBlank()

    val missingContactFields: List<String>
        get() = buildList {
            if (firstName.isBlank()) add("first name")
            if (lastName.isBlank()) add("last name")
            if (email.isBlank()) add("email")
            if (phone.isBlank()) add("phone")
        }

    @Throws(Exception::class)
    fun requireReportContact(plate: String, isPhiladelphia: Boolean) {
        val forHire = PlatePatternClassifier.classify(plate)?.type in setOf(PlateType.TAXI, PlateType.TLC)
        if (withholdContactInfoFromNypd && !forHire && !isPhiladelphia) return
        require(missingContactFields.isEmpty()) {
            "Complete your Profile before submitting: ${missingContactFields.joinToString(", ")}. Your report has been kept."
        }
    }
}
