package com.reported.nativeandroid.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.reported.nativeandroid.BuildConfig
import com.reported.shared.model.Catalogs
import com.reported.shared.model.ComplaintCategory
import com.reported.shared.model.RemoteConfigDefaults
import com.reported.shared.model.RemoteConfigKeys
import com.reported.shared.model.RemoteConfigOverrides
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteConfigSnapshot(
    val complaintCategories: List<ComplaintCategory> = Catalogs.complaintCategories,
    val showComplaintImages: Boolean = RemoteConfigOverrides.showComplaintImages,
    val enableLive: Boolean = RemoteConfigOverrides.enableLive,
    val enableMediaScanner: Boolean = RemoteConfigOverrides.enableMediaScanner,
    val enableOfflinePhotoProcessing: Boolean = RemoteConfigOverrides.enableOfflinePhotoProcessing,
    val systemNotice: String = RemoteConfigOverrides.systemNotice
)

object ReportedRemoteConfig {
    private val _snapshot = MutableStateFlow(RemoteConfigSnapshot())
    val snapshot: StateFlow<RemoteConfigSnapshot> = _snapshot.asStateFlow()

    fun initialize() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                .build()
        )
        remoteConfig.setDefaultsAsync(defaults())
        apply(remoteConfig)
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            apply(remoteConfig)
        }
    }

    private fun apply(remoteConfig: FirebaseRemoteConfig) {
        RemoteConfigOverrides.apply(
            apiBaseUrl = null,
            parseServerUrl = remoteConfig.getString(RemoteConfigKeys.PARSE_SERVER_URL),
            complaintCategoriesJson = remoteConfig.getString(RemoteConfigKeys.COMPLAINT_CATEGORIES),
            reportStatusesJson = remoteConfig.getString(RemoteConfigKeys.REPORT_STATUSES),
            showComplaintImages = remoteConfig.getBoolean(RemoteConfigKeys.SHOW_COMPLAINT_IMAGES).toString(),
            enableLive = remoteConfig.getBoolean(RemoteConfigKeys.ENABLE_LIVE).toString(),
            enableMediaScanner = remoteConfig.getBoolean(RemoteConfigKeys.ENABLE_MEDIA_SCANNER).toString(),
            enableOfflinePhotoProcessing = remoteConfig.getBoolean(RemoteConfigKeys.ENABLE_OFFLINE_PHOTO_PROCESSING).toString(),
            systemNotice = remoteConfig.getString(RemoteConfigKeys.SYSTEM_NOTICE)
        )
        _snapshot.value = RemoteConfigSnapshot(
            complaintCategories = Catalogs.complaintCategories,
            showComplaintImages = RemoteConfigOverrides.showComplaintImages,
            enableLive = RemoteConfigOverrides.enableLive,
            enableMediaScanner = RemoteConfigOverrides.enableMediaScanner,
            enableOfflinePhotoProcessing = RemoteConfigOverrides.enableOfflinePhotoProcessing,
            systemNotice = RemoteConfigOverrides.systemNotice
        )
    }

    private fun defaults(): Map<String, Any> = mapOf(
        RemoteConfigKeys.PARSE_SERVER_URL to BuildConfig.PARSE_SERVER_URL.ifBlank { RemoteConfigDefaults.PARSE_SERVER_URL },
        RemoteConfigKeys.COMPLAINT_CATEGORIES to RemoteConfigDefaults.complaintCategoriesJson,
        RemoteConfigKeys.REPORT_STATUSES to RemoteConfigDefaults.reportStatusesJson,
        RemoteConfigKeys.SHOW_COMPLAINT_IMAGES to BuildConfig.SHOW_COMPLAINT_IMAGES,
        RemoteConfigKeys.ENABLE_LIVE to BuildConfig.ENABLE_LIVE,
        RemoteConfigKeys.ENABLE_MEDIA_SCANNER to RemoteConfigDefaults.ENABLE_MEDIA_SCANNER,
        RemoteConfigKeys.ENABLE_OFFLINE_PHOTO_PROCESSING to RemoteConfigDefaults.ENABLE_OFFLINE_PHOTO_PROCESSING,
        RemoteConfigKeys.SYSTEM_NOTICE to RemoteConfigDefaults.SYSTEM_NOTICE
    )
}
