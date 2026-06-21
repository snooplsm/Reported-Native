package com.reported.nativeandroid.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.reported.nativeandroid.BuildConfig
import com.reported.shared.api.VehicleEnrichmentPolicy

class AndroidVehicleEnrichmentPolicy(context: Context) : VehicleEnrichmentPolicy {
    private val appContext = context.applicationContext

    override fun canAttemptVehicleEnrichment(): Boolean {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
    }

    override fun includeDebugSummaryInNotes(): Boolean = BuildConfig.DEBUG
}
