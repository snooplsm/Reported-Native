package com.reported.shared.auth

import com.reported.shared.model.UserSession

class LoadSessionUseCase(private val repository: AuthRepository) {
    suspend fun execute(): UserSession? = repository.currentSession()
}

class LoginUseCase(private val repository: AuthRepository) {
    suspend fun execute(email: String, password: String): UserSession =
        repository.login(email, password)
}

class RegisterUseCase(private val repository: AuthRepository) {
    suspend fun execute(
        firstName: String,
        lastName: String,
        phone: String,
        testify: Boolean,
        email: String,
        password: String
    ): UserSession = repository.register(firstName, lastName, phone, testify, email, password)
}

class SocialLoginUseCase(private val repository: AuthRepository) {
    suspend fun execute(
        provider: String,
        providerUserId: String,
        idToken: String,
        email: String,
        firstName: String = "",
        lastName: String = ""
    ): UserSession = repository.socialLogin(provider, providerUserId, idToken, email, firstName, lastName)
}

class ForgotPasswordUseCase(private val repository: AuthRepository) {
    suspend fun execute(email: String) = repository.forgotPassword(email)
}

class UpdateProfileUseCase(private val repository: AuthRepository) {
    suspend fun execute(
        email: String,
        phone: String,
        firstName: String,
        lastName: String
    ): UserSession = repository.updateProfile(email, phone, firstName, lastName)
}

class LogoutUseCase(private val repository: AuthRepository) {
    suspend fun execute() = repository.logout()
}
