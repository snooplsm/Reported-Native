package com.reported.shared.session

class LoadGuestModeUseCase(
    private val repository: GuestModePreferencesRepository
) {
    @Throws(Exception::class)
    suspend fun execute(): Boolean = repository.read()
}

class SaveGuestModeUseCase(
    private val repository: GuestModePreferencesRepository
) {
    @Throws(Exception::class)
    suspend fun execute(enabled: Boolean) = repository.write(enabled)
}
