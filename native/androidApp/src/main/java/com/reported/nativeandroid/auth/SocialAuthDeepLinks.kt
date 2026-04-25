package com.reported.nativeandroid.auth

import android.net.Uri
import android.util.Base64
import com.reported.nativeandroid.app.SocialAuthProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SocialAuthDeepLinks {
    val profiles = MutableSharedFlow<SocialAuthProfile>(extraBufferCapacity = 1)

    fun handle(uri: Uri?) {
        val profile = parseAppleProfile(uri) ?: return
        profiles.tryEmit(profile)
    }

    private fun parseAppleProfile(uri: Uri?): SocialAuthProfile? {
        if (uri == null || uri.scheme != "reported" || uri.host != "oauth" || uri.path != "/apple") return null
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() } +
            parseFragmentParams(uri.fragment)
        val token = params["id_token"].orEmpty().ifBlank { params["token"].orEmpty() }
        if (token.isBlank()) return null
        val jwt = decodeJwtPayload(token)
        val user = params["user"]?.let(::decodeAppleUser)
        val providerUserId = params["user_id"].orEmpty()
            .ifBlank { params["sub"].orEmpty() }
            .ifBlank { jwt.optString("sub") }
        val email = params["email"].orEmpty()
            .ifBlank { jwt.optString("email") }
            .ifBlank { user?.optString("email").orEmpty() }
        val name = user?.optJSONObject("name")
        return SocialAuthProfile(
            provider = "apple",
            providerUserId = providerUserId,
            idToken = token,
            email = email,
            firstName = params["firstName"].orEmpty()
                .ifBlank { params["given_name"].orEmpty() }
                .ifBlank { name?.optString("firstName").orEmpty() },
            lastName = params["lastName"].orEmpty()
                .ifBlank { params["family_name"].orEmpty() }
                .ifBlank { name?.optString("lastName").orEmpty() }
        )
    }

    private fun parseFragmentParams(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank()) return emptyMap()
        return fragment.split('&')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = part.substring(0, index).urlDecode()
                val value = part.substring(index + 1).urlDecode()
                key to value
            }
            .toMap()
    }

    private fun decodeJwtPayload(token: String): JSONObject {
        return runCatching {
            val payload = token.split(".").getOrNull(1).orEmpty()
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decoded, StandardCharsets.UTF_8))
        }.getOrDefault(JSONObject())
    }

    private fun decodeAppleUser(raw: String): JSONObject? {
        return runCatching { JSONObject(raw.urlDecode()) }.getOrNull()
    }

    private fun String.urlDecode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}
