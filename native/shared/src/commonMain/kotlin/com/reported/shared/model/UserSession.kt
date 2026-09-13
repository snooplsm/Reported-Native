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
    val testify: Boolean = false
) {
    val isAuthorized: Boolean
        get() = (id > 0 || objectId.isNotBlank()) && sessionToken.isNotBlank()
}
