package com.reported.nativeandroid.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.reported.nativeandroid.BuildConfig
import com.reported.shared.model.RemoteConfigOverrides

object MediaScannerSettings {
    private const val PREFS = "reported.media.scanner"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_OFFLINE_PROCESSING_ENABLED = "offline_processing_enabled"
    private const val KEY_LAST_SCAN_SECONDS = "last_scan_seconds"
    private const val KEY_SEEN_MEDIA = "seen_media"
    private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

    fun supportsBroadLibraryAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun supportsAutoReportLibraryScan(): Boolean =
        supportsBroadLibraryAccess()

    fun supportsBackgroundLibraryScanning(): Boolean =
        supportsBroadLibraryAccess() && BuildConfig.DEBUG && RemoteConfigOverrides.enableMediaScanner

    fun isEnabled(context: Context): Boolean =
        supportsBackgroundLibraryScanning() && prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val supportedEnabled = enabled && supportsBackgroundLibraryScanning()
        prefs(context).edit().putBoolean(KEY_ENABLED, supportedEnabled).apply()
        if (supportedEnabled) {
            MediaScannerScheduler.schedule(context)
        } else {
            MediaScannerScheduler.cancel(context)
        }
    }

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isOfflineProcessingEnabled(context: Context): Boolean =
        RemoteConfigOverrides.enableOfflinePhotoProcessing &&
            prefs(context).getBoolean(KEY_OFFLINE_PROCESSING_ENABLED, false)

    fun setOfflineProcessingEnabled(context: Context, enabled: Boolean) {
        val supportedEnabled = enabled && RemoteConfigOverrides.enableOfflinePhotoProcessing
        prefs(context).edit().putBoolean(KEY_OFFLINE_PROCESSING_ENABLED, supportedEnabled).apply()
        if (supportedEnabled && isEnabled(context)) {
            MediaScannerScheduler.schedule(context)
        } else if (!supportedEnabled) {
            MediaScannerScheduler.cancel(context)
        }
    }

    fun lastScanSeconds(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SCAN_SECONDS, 0L)

    fun setLastScanSeconds(context: Context, seconds: Long) {
        prefs(context).edit().putLong(KEY_LAST_SCAN_SECONDS, seconds).apply()
    }

    fun markSeen(context: Context, key: String) {
        val seen = seenMedia(context).toMutableSet()
        seen += key
        prefs(context).edit().putStringSet(KEY_SEEN_MEDIA, seen.toList().takeLast(500).toSet()).apply()
    }

    fun hasSeen(context: Context, key: String): Boolean =
        seenMedia(context).contains(key)

    fun hasAskedNotificationPermission(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)

    fun markNotificationPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
    }

    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun hasNotificationPermission(context: Context): Boolean {
        val permission = notificationPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermissions(): Array<String> = if (!supportsBackgroundLibraryScanning()) {
        emptyArray()
    } else buildList {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun hasRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun autoReportPermissions(): Array<String> = if (!supportsAutoReportLibraryScan()) {
        emptyArray()
    } else buildList {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun hasAutoReportPermissions(context: Context): Boolean =
        supportsAutoReportLibraryScan() &&
        autoReportPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun seenMedia(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SEEN_MEDIA, emptySet()).orEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
