package com.reported.shared.base

interface MediaPickingService {
    suspend fun pickImage(): String?
}

interface LocationService {
    suspend fun currentAddressLabel(): String?
}

interface NotificationService {
    suspend fun requestPermissions(): Boolean
}

interface UploadService {
    suspend fun uploadFile(localPath: String): String?
}

interface AnalyticsService {
    fun screenViewed(name: String)
    fun action(name: String)
}

