package com.reported.shared.session

import com.reported.shared.base.AppEnvironment
import com.reported.shared.model.AppThemeMode

class LoadAppThemeModeUseCase(
    private val repository: AppThemePreferencesRepository,
    private val environment: AppEnvironment
) {
    suspend fun execute(): AppThemeMode = repository.read(environment)
}

class SaveAppThemeModeUseCase(
    private val repository: AppThemePreferencesRepository
) {
    suspend fun execute(mode: AppThemeMode) = repository.write(mode)
}
