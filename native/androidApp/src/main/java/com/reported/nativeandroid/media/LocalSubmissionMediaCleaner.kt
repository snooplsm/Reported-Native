package com.reported.nativeandroid.media

import android.content.Context
import android.net.Uri
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import java.io.File

object LocalSubmissionMediaCleaner {
    fun cleanupAfterSuccessfulSubmit(
        context: Context,
        media: List<SubmissionMedia>,
        plateCandidates: List<PlateCandidate>
    ) {
        media.forEach { deleteAppOwnedUri(context, it.uri) }
        plateCandidates.forEach { candidate ->
            candidate.thumbnailUri?.let { deleteAppOwnedUri(context, it) }
            candidate.videoFramePreviewUri?.let { deleteAppOwnedUri(context, it) }
        }
        deleteDirectory(File(context.cacheDir, "alpr-thumbnails"))
        deleteDirectory(File(context.cacheDir, "alpr-video-frames"))
        deleteEmptyDirectory(File(File(context.filesDir, "submission-media"), "alpr-video-frames"))
        deleteEmptyDirectory(File(context.filesDir, "submission-media"))
    }

    private fun deleteAppOwnedUri(context: Context, uriValue: String) {
        val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        val file = uri.path?.let(::File) ?: return
        if (!isAppOwnedFile(context, file)) return
        runCatching { file.delete() }
    }

    private fun isAppOwnedFile(context: Context, file: File): Boolean {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val allowedRoots = listOf(
            File(context.filesDir, "submission-media"),
            File(context.cacheDir, "alpr-thumbnails"),
            File(context.cacheDir, "alpr-video-frames"),
            File(File(context.filesDir, "submission-media"), "alpr-video-frames")
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        return allowedRoots.any { root ->
            canonicalFile.path == root.path || canonicalFile.path.startsWith(root.path + File.separator)
        }
    }

    private fun deleteEmptyDirectory(directory: File) {
        if (directory.exists() && directory.isDirectory && directory.list().isNullOrEmpty()) {
            runCatching { directory.delete() }
        }
    }

    private fun deleteDirectory(directory: File) {
        if (directory.exists() && directory.isDirectory) {
            runCatching { directory.deleteRecursively() }
        }
    }
}
