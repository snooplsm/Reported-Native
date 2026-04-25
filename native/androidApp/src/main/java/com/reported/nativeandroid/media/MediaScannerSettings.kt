package com.reported.nativeandroid.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object MediaScannerSettings {
    private const val PREFS = "reported.media.scanner"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_OFFLINE_PROCESSING_ENABLED = "offline_processing_enabled"
    private const val KEY_LAST_SCAN_SECONDS = "last_scan_seconds"
    private const val KEY_SEEN_MEDIA = "seen_media"
    private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            MediaScannerScheduler.schedule(context)
        } else {
            MediaScannerScheduler.cancel(context)
        }
    }

    fun isOfflineProcessingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OFFLINE_PROCESSING_ENABLED, true)

    fun setOfflineProcessingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OFFLINE_PROCESSING_ENABLED, enabled).apply()
        if (enabled && isEnabled(context)) {
            MediaScannerScheduler.schedule(context)
        } else if (!enabled) {
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

    fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }.toTypedArray()

    fun hasRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun seenMedia(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SEEN_MEDIA, emptySet()).orEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
