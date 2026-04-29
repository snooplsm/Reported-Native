package com.reported.nativeandroid.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.app.SocialAuthProfile
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class SocialAuthCancelledException(cause: Throwable? = null) : Exception("Sign-in was cancelled.", cause)

suspend fun signInWithGoogle(activity: Activity): SocialAuthProfile {
    val clientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
    require(clientId.isNotBlank()) { "Set REPORTED_GOOGLE_SERVER_CLIENT_ID to enable Google sign-in." }

    val credentialManager = CredentialManager.create(activity)
    val googleIdOption = GetSignInWithGoogleOption.Builder(clientId)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val result = try {
        credentialManager.getCredential(activity, request)
    } catch (error: GetCredentialException) {
        if (error.isCredentialCancellation()) {
            throw SocialAuthCancelledException(error)
        }
        throw IllegalStateException(
            "Google sign-in failed (${error.type}): ${error.message ?: "check OAuth client ID, package name, and SHA-1 fingerprint."}",
            error
        )
    }
    val credential = result.credential
    if (credential !is CustomCredential ||
        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        error("Google sign-in returned an unsupported credential.")
    }

    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
    val jwt = decodeJwtPayload(googleCredential.idToken)
    return SocialAuthProfile(
        provider = "google",
        providerUserId = jwt.optString("sub").ifBlank { googleCredential.id },
        idToken = googleCredential.idToken,
        email = jwt.optString("email").ifBlank { googleCredential.id },
        firstName = googleCredential.givenName.orEmpty().ifBlank { jwt.optString("given_name") },
        lastName = googleCredential.familyName.orEmpty().ifBlank { jwt.optString("family_name") }
    )
}

fun launchAppleSignIn(context: Context) {
    val clientId = BuildConfig.APPLE_CLIENT_ID
    val redirectUri = BuildConfig.APPLE_REDIRECT_URI
    require(clientId.isNotBlank()) { "Set REPORTED_APPLE_CLIENT_ID to enable Apple sign-in." }
    require(redirectUri.isNotBlank()) { "Set REPORTED_APPLE_REDIRECT_URI to enable Apple sign-in." }

    val authUri = Uri.Builder()
        .scheme("https")
        .authority("appleid.apple.com")
        .path("/auth/authorize")
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", redirectUri)
        .appendQueryParameter("response_type", "code id_token")
        .appendQueryParameter("response_mode", "fragment")
        .appendQueryParameter("scope", "name email")
        .appendQueryParameter("state", UUID.randomUUID().toString())
        .build()

    val intent = Intent(Intent.ACTION_VIEW, authUri)
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        throw IllegalStateException("No browser is available for Apple sign-in.", error)
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Throwable.isSocialAuthCancellation(): Boolean =
    this is SocialAuthCancelledException ||
        (this as? GetCredentialException)?.isCredentialCancellation() == true ||
        cause?.isSocialAuthCancellation() == true

private fun GetCredentialException.isCredentialCancellation(): Boolean {
    val marker = listOfNotNull(type, message, javaClass.simpleName).joinToString(" ").lowercase()
    return "cancel" in marker || "canceled" in marker
}

private fun decodeJwtPayload(token: String): JSONObject {
    return runCatching {
        val payload = token.split(".").getOrNull(1).orEmpty()
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded, StandardCharsets.UTF_8))
    }.getOrDefault(JSONObject())
}
