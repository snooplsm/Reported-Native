package com.reported.shared

import com.reported.shared.api.ReportedApi
import com.reported.shared.auth.AuthRepository
import com.reported.shared.auth.ForgotPasswordUseCase
import com.reported.shared.auth.LoadSessionUseCase
import com.reported.shared.auth.LoginUseCase
import com.reported.shared.auth.LogoutUseCase
import com.reported.shared.auth.RegisterUseCase
import com.reported.shared.auth.SocialLoginUseCase
import com.reported.shared.auth.UpdateProfileUseCase
import com.reported.shared.base.AppEnvironment
import com.reported.shared.base.ReportedConfig
import com.reported.shared.reports.ChangeReportStatusUseCase
import com.reported.shared.reports.ClearDraftUseCase
import com.reported.shared.reports.DraftRepository
import com.reported.shared.reports.DeleteReportUseCase
import com.reported.shared.reports.FetchReportDetailUseCase
import com.reported.shared.reports.FetchReportStatsUseCase
import com.reported.shared.reports.FetchReportsUseCase
import com.reported.shared.reports.LoadDraftUseCase
import com.reported.shared.reports.ReportsRepository
import com.reported.shared.reports.SaveDraftUseCase
import com.reported.shared.reports.SubmitReportUseCase
import com.reported.shared.reports.SubmittedPlateRepository
import com.reported.shared.session.AppThemePreferencesRepository
import com.reported.shared.session.GuestModePreferencesRepository
import com.reported.shared.session.LoadGuestModeUseCase
import com.reported.shared.session.LoadAppThemeModeUseCase
import com.reported.shared.session.SaveGuestModeUseCase
import com.reported.shared.session.SaveAppThemeModeUseCase
import com.reported.shared.session.SettingsSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ReportedShared(
    val config: ReportedConfig = ReportedConfig()
) {
    val environment: AppEnvironment = config.environment
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val sessionStore = SettingsSessionStore(json)
    private val client = HttpClient(platformEngineFactory()) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    println(message)
                }
            }
        }
    }
    private val api = ReportedApi(
        baseUrl = config.apiBaseUrl,
        parseConfig = config.parse,
        client = client,
        sessionStore = sessionStore,
        json = json
    )
    private val authRepository = AuthRepository(api = api, sessionStore = sessionStore)
    private val reportsRepository = ReportsRepository(api = api)
    private val draftRepository = DraftRepository(json = json)
    private val submittedPlateRepository = SubmittedPlateRepository()
    private val appThemePreferencesRepository = AppThemePreferencesRepository()
    private val guestModePreferencesRepository = GuestModePreferencesRepository()

    val loadSessionUseCase = LoadSessionUseCase(authRepository)
    val loginUseCase = LoginUseCase(authRepository)
    val registerUseCase = RegisterUseCase(authRepository)
    val socialLoginUseCase = SocialLoginUseCase(authRepository)
    val forgotPasswordUseCase = ForgotPasswordUseCase(authRepository)
    val updateProfileUseCase = UpdateProfileUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)

    val fetchReportsUseCase = FetchReportsUseCase(reportsRepository)
    val fetchReportDetailUseCase = FetchReportDetailUseCase(reportsRepository)
    val fetchReportStatsUseCase = FetchReportStatsUseCase(reportsRepository)
    val submitReportUseCase = SubmitReportUseCase(reportsRepository, submittedPlateRepository)
    val changeReportStatusUseCase = ChangeReportStatusUseCase(reportsRepository)
    val deleteReportUseCase = DeleteReportUseCase(reportsRepository, submittedPlateRepository)
    val loadDraftUseCase = LoadDraftUseCase(draftRepository)
    val saveDraftUseCase = SaveDraftUseCase(draftRepository)
    val clearDraftUseCase = ClearDraftUseCase(draftRepository)
    val loadAppThemeModeUseCase = LoadAppThemeModeUseCase(appThemePreferencesRepository, environment)
    val saveAppThemeModeUseCase = SaveAppThemeModeUseCase(appThemePreferencesRepository)
    val loadGuestModeUseCase = LoadGuestModeUseCase(guestModePreferencesRepository)
    val saveGuestModeUseCase = SaveGuestModeUseCase(guestModePreferencesRepository)
}

expect fun platformEngineFactory(): HttpClientEngineFactory<*>
