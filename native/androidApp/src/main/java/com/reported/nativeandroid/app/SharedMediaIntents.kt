package com.reported.nativeandroid.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class SharedMediaRequest(
    val id: Long,
    val uris: List<Uri>
)

object SharedMediaIntents {
    private val nextId = AtomicLong(0)
    private val _requests = MutableStateFlow<SharedMediaRequest?>(null)
    val requests: StateFlow<SharedMediaRequest?> = _requests.asStateFlow()

    fun publish(uris: List<Uri>) {
        val mediaUris = uris.distinct().takeIf { it.isNotEmpty() } ?: return
        _requests.value = SharedMediaRequest(
            id = nextId.incrementAndGet(),
            uris = mediaUris
        )
    }

    fun consume(id: Long) {
        if (_requests.value?.id == id) {
            _requests.value = null
        }
    }
}
