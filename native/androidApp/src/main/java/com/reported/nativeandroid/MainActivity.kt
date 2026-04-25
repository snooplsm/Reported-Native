package com.reported.nativeandroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.reported.nativeandroid.app.ReportedAndroidApp
import com.reported.nativeandroid.app.SharedMediaIntents
import com.reported.nativeandroid.auth.SocialAuthDeepLinks
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.DetectedInfractionNotifications
import com.reported.nativeandroid.media.DetectedInfractionStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            ReportedAndroidApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == DetectedInfractionNotifications.ACTION_EDIT) {
            val candidateId = intent.getStringExtra(DetectedInfractionNotifications.EXTRA_CANDIDATE_ID)
            if (candidateId != null) {
                lifecycleScope.launch {
                    DetectedInfractionStore.load(this@MainActivity, candidateId)?.let { candidate ->
                        AppGraph.shared.saveDraftUseCase.execute(candidate.toDraft())
                        DetectedInfractionNotifications.cancel(this@MainActivity, candidateId)
                    }
                }
            }
        }
        SocialAuthDeepLinks.handle(intent?.data)
        SharedMediaIntents.publish(extractSharedMediaUris(intent))
    }

    private fun extractSharedMediaUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamExtras()
            else -> emptyList()
        }.filter { uri ->
            val type = contentResolver.getType(uri) ?: intent.type.orEmpty()
            type.startsWith("image/") || type.startsWith("video/")
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

    @Suppress("DEPRECATION")
    private fun Intent.streamExtras(): List<Uri> {
        val fromExtra = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }.orEmpty()
        val fromClip = buildList {
            val clip = clipData ?: return@buildList
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
        return (fromExtra + fromClip).distinct()
    }
}
