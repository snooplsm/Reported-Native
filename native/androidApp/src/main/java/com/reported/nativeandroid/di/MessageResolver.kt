package com.reported.nativeandroid.di

import android.content.res.Resources
import androidx.annotation.StringRes

interface MessageResolver {
    fun resolve(@StringRes id: Int, vararg args: Any): String
}

internal class AndroidMessageResolver(
    private val resources: Resources
) : MessageResolver {
    override fun resolve(@StringRes id: Int, vararg args: Any): String =
        resources.getString(id, *args)
}
