package com.reported.nativeandroid.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.SubmissionMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ExtractedSubmissionMetadata(
    val occurredAtIso: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val videoThumbnail: Bitmap? = null
)

private const val NYC_SEARCH_URL = "https://geosearch.planninglabs.nyc/v2/search"
private const val NYC_REVERSE_URL = "https://geosearch.planninglabs.nyc/v2/reverse"

suspend fun buildSubmissionMedia(
    context: Context,
    uri: Uri,
    isVideo: Boolean
): SubmissionMedia = withContext(Dispatchers.IO) {
    val durableUri = persistSubmissionMedia(context, uri, isVideo)
    val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank {
        if (isVideo) "video/*" else "image/*"
    }
    SubmissionMedia(
        uri = durableUri.toString(),
        displayName = queryDisplayName(context.contentResolver, uri),
        mimeType = mimeType,
        isVideo = isVideo
    )
}

suspend fun extractSubmissionMetadata(
    context: Context,
    media: SubmissionMedia
): ExtractedSubmissionMetadata = withContext(Dispatchers.IO) {
    if (media.isVideo) {
        extractVideoMetadata(context, Uri.parse(media.uri))
    } else {
        extractImageMetadata(context, Uri.parse(media.uri))
    }
}

suspend fun reverseGeocodeNyc(latitude: Double, longitude: Double): AddressSuggestion? =
    withContext(Dispatchers.IO) {
        val url = URL("$NYC_REVERSE_URL?point.lat=$latitude&point.lon=$longitude&size=1")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val feature = json.optJSONArray("features")?.optJSONObject(0) ?: return@use null
                featureToSuggestion(feature)
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

suspend fun searchNycAddresses(query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
    if (query.isBlank()) {
        return@withContext emptyList()
    }
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    val url = URL("$NYC_SEARCH_URL?text=$encoded")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
    }
    runCatching {
        connection.inputStream.bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            val features = json.optJSONArray("features") ?: return@use emptyList()
            buildList {
                for (index in 0 until features.length()) {
                    val feature = features.optJSONObject(index) ?: continue
                    featureToSuggestion(feature)?.let(::add)
                }
            }
        }
    }.getOrDefault(emptyList()).also {
        connection.disconnect()
    }
}

private fun featureToSuggestion(feature: JSONObject): AddressSuggestion? {
    val properties = feature.optJSONObject("properties") ?: return null
    val geometry = feature.optJSONObject("geometry") ?: return null
    val coordinates = geometry.optJSONArray("coordinates") ?: return null
    return AddressSuggestion(
        label = properties.optString("label"),
        latitude = coordinates.optDouble(1),
        longitude = coordinates.optDouble(0)
    )
}

private fun extractImageMetadata(context: Context, uri: Uri): ExtractedSubmissionMetadata {
    val input = openInputStreamSafely(context, uri) ?: return ExtractedSubmissionMetadata()
    return input.use {
        val exif = ExifInterface(it)
        val latLong = exif.latLong
        val dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ExtractedSubmissionMetadata(
            occurredAtIso = dateTimeOriginal?.let(::parseExifDateTime),
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1)
        )
    }
}

private fun extractVideoMetadata(context: Context, uri: Uri): ExtractedSubmissionMetadata {
    val retriever = MediaMetadataRetriever()
    return try {
        setRetrieverDataSource(context, retriever, uri)
        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
        val thumbnail = retriever.getFrameAtTime(0)
        val (lat, lon) = parseIso6709Location(location)
        ExtractedSubmissionMetadata(
            occurredAtIso = date?.let(::parseVideoDate),
            latitude = lat,
            longitude = lon,
            videoThumbnail = thumbnail
        )
    } finally {
        retriever.release()
    }
}

private fun parseExifDateTime(raw: String): String? = runCatching {
    val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    LocalDateTime.parse(raw, formatter).atOffset(ZoneOffset.UTC).toInstant().toString()
}.getOrNull()

private fun parseVideoDate(raw: String): String? = runCatching {
    Instant.parse(raw).toString()
}.getOrElse {
    runCatching {
        val normalized = raw.removeSuffix("Z")
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS")
        LocalDateTime.parse(normalized, formatter).atOffset(ZoneOffset.UTC).toInstant().toString()
    }.getOrNull()
}

private fun parseIso6709Location(raw: String?): Pair<Double?, Double?> {
    if (raw.isNullOrBlank()) return null to null
    val match = Regex("([+-][0-9.]+)([+-][0-9.]+)").find(raw) ?: return null to null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull() to
        match.groupValues.getOrNull(2)?.toDoubleOrNull()
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            return cursor.getString(nameIndex) ?: "Upload"
        }
    }
    return uri.lastPathSegment ?: "Upload"
}

private fun persistSubmissionMedia(context: Context, sourceUri: Uri, isVideo: Boolean): Uri {
    if (sourceUri.scheme == "file") {
        return sourceUri
    }

    val displayName = queryDisplayName(context.contentResolver, sourceUri)
    val extension = displayName.substringAfterLast('.', "").ifBlank {
        when {
            isVideo -> "mp4"
            else -> "jpg"
        }
    }
    val sanitizedBaseName = displayName.substringBeforeLast('.')
        .ifBlank { if (isVideo) "video" else "image" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    val mediaDir = File(context.filesDir, "submission-media").apply { mkdirs() }
    val targetFile = File(mediaDir, "${System.currentTimeMillis()}_${sanitizedBaseName}.$extension")
    openInputStreamSafely(context, sourceUri)?.use { input ->
        targetFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: return sourceUri
    return Uri.fromFile(targetFile)
}

private fun openInputStreamSafely(context: Context, uri: Uri): InputStream? =
    when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }

private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
    if (uri.scheme == "file") {
        retriever.setDataSource(uri.path)
    } else {
        retriever.setDataSource(context, uri)
    }
}
