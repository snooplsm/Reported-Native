package com.reported.shared.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.*
import com.reported.shared.api.ReportedApi
import com.reported.shared.model.UserSession
import com.reported.shared.session.SessionStore

class AuthRepository(
    private val api: ReportedApi,
    private val sessionStore: SessionStore
) {
    suspend fun currentSession(): UserSession? = sessionStore.read()

    suspend fun login(email: String, password: String): UserSession {
        val session = api.login(email, password)
        sessionStore.write(session)
        return session
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        phone: String,
        testify: Boolean,
        email: String,
        password: String
    ): UserSession {
        val session = api.register(firstName, lastName, phone, testify, email, password)
        sessionStore.write(session)
        return session
    }

    suspend fun socialLogin(
        provider: String,
        providerUserId: String,
        idToken: String,
        email: String,
        firstName: String,
        lastName: String,
        phone: String = "",
        testify: Boolean = false
    ): UserSession {
        val session = api.socialLogin(provider, providerUserId, idToken, email, firstName, lastName, phone, testify)
            .copy(avatarUrl = if (provider == "google") googleAvatar(idToken) else null)
        sessionStore.write(session)
        return session
    }

    suspend fun forgotPassword(email: String) {
        api.forgotPassword(email)
    }

    suspend fun updateProfile(
        email: String,
        phone: String,
        firstName: String,
        lastName: String,
        testify: Boolean
    ): UserSession {
        val session = api.updateProfile(email, phone, firstName, lastName, testify)
            .copy(avatarUrl = sessionStore.read()?.avatarUrl)
        sessionStore.write(session)
        return session
    }

    suspend fun logout() {
        sessionStore.clear()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun googleAvatar(idToken: String): String? = runCatching {
    val payload = idToken.split('.')[1]
    val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
    Json.parseToJsonElement(Base64.UrlSafe.decode(padded).decodeToString())
        .jsonObject["picture"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.startsWith("https://") }
}.getOrNull()
