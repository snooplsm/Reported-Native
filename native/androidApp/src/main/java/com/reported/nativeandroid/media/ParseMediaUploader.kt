package com.reported.nativeandroid.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.shared.model.RemoteConfigOverrides
import com.reported.shared.model.SubmitReportMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

data class MediaUploadProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val message: String
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

object ParseMediaUploader {
    private const val TAG = "ReportedSubmit"

    suspend fun upload(
        context: Context,
        media: SubmissionMedia,
        onProgress: suspend (MediaUploadProgress) -> Unit = {}
    ): SubmitReportMediaFile? =
        uploadUrl(context, media, 0, 1, onProgress)?.let { url ->
            SubmitReportMediaFile(url = url, isVideo = media.isVideo)
        }

    suspend fun uploadAll(
        context: Context,
        media: List<SubmissionMedia>,
        onProgress: suspend (MediaUploadProgress) -> Unit = {}
    ): List<SubmitReportMediaFile> {
        val appContext = context.applicationContext
        Log.d(TAG, "Media upload batch starting; count=${media.size}")
        return media.mapIndexedNotNull { index, item ->
            uploadUrl(appContext, item, index, media.size, onProgress)?.let { url ->
                SubmitReportMediaFile(url = url, isVideo = item.isVideo)
            }
        }.also {
            Log.d(TAG, "Media upload batch finished; uploaded=${it.size}/${media.size}")
        }
    }

    private suspend fun uploadUrl(
        context: Context,
        media: SubmissionMedia,
        index: Int,
        totalFiles: Int,
        onProgress: suspend (MediaUploadProgress) -> Unit
    ): String? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(media.uri)
            val rawName = media.displayName.ifBlank { uri.lastPathSegment ?: if (media.isVideo) "video.mp4" else "photo.jpg" }
            val payload = buildUploadPayload(context, uri, rawName, media)
                ?: run {
                    Log.w(TAG, "Media upload skipped; could not read ${media.uri}")
                    return@withContext null
                }
            val filename = payload.filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val encodedName = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
            val baseUrl = parseBaseUrl()
            Log.d(
                TAG,
                "Uploading media ${index + 1}/$totalFiles name=$filename type=${payload.contentType} bytes=${payload.length} compressed=${payload.compressed}"
            )
            val connection = (URL("$baseUrl/files/$encodedName").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setFixedLengthStreamingMode(payload.length)
                setRequestProperty("X-Parse-Application-Id", BuildConfig.PARSE_APPLICATION_ID)
                setRequestProperty("X-Parse-JavaScript-Key", BuildConfig.PARSE_JAVASCRIPT_KEY)
                setRequestProperty("Content-Type", payload.contentType)
            }
            try {
                payload.openStream().use { input ->
                    connection.outputStream.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var uploaded = 0L
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            uploaded += read
                            onProgress(
                                MediaUploadProgress(
                                    completedBytes = uploaded,
                                    totalBytes = payload.length,
                                    currentFileIndex = index,
                                    totalFiles = totalFiles,
                                    message = "Uploading media ${index + 1} of $totalFiles"
                                )
                            )
                            read = input.read(buffer)
                        }
                    }
                }
                val responseText = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                Log.d(TAG, "Media upload response ${connection.responseCode}; name=$filename")
                if (connection.responseCode !in 200..299) {
                    error("Media upload failed: ${connection.responseCode} $responseText")
                }
                JSONObject(responseText).optString("url").takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
                payload.cleanup()
            }
        }

    private fun buildUploadPayload(
        context: Context,
        uri: Uri,
        rawName: String,
        media: SubmissionMedia
    ): UploadPayload? {
        if (media.isVideo) {
            return sourcePayload(context, uri, rawName, media.mimeType.ifBlank { "video/mp4" })
        }
        val original = readSourceBytes(context, uri) ?: return null
        return compressJpegPayload(context, original, rawName) ?: UploadPayload(
            filename = rawName,
            contentType = media.mimeType.ifBlank { "image/jpeg" },
            length = original.size.toLong(),
            compressed = false,
            openStream = { ByteArrayInputStream(original) }
        )
    }

    private fun sourcePayload(
        context: Context,
        uri: Uri,
        rawName: String,
        contentType: String
    ): UploadPayload? {
        uri.path?.let { path ->
            val file = File(path)
            if (file.exists()) {
                return UploadPayload(
                    filename = rawName,
                    contentType = contentType,
                    length = file.length(),
                    compressed = false,
                    openStream = { FileInputStream(file) }
                )
            }
        }
        val temp = File.createTempFile("reported-upload-", rawName.substringAfterLast('.', "bin"), context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            UploadPayload(
                filename = rawName,
                contentType = contentType,
                length = temp.length(),
                compressed = false,
                openStream = { FileInputStream(temp) },
                cleanup = { temp.delete() }
            )
        } catch (error: Throwable) {
            temp.delete()
            Log.w(TAG, "Could not prepare media source for upload", error)
            null
        }
    }

    private fun readSourceBytes(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: uri.path?.let { path -> File(path).takeIf { it.exists() }?.readBytes() }

    private fun compressJpegPayload(context: Context, original: ByteArray, rawName: String): UploadPayload? {
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size) ?: return null
        val filenameBase = rawName.substringBeforeLast('.', rawName)
        val temp = File.createTempFile("reported-upload-jpeg-", ".jpg", context.cacheDir)
        return try {
            temp.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            copyExifToJpeg(original, temp)
            Log.d(TAG, "JPEG image prepared; original=${original.size} jpeg=${temp.length()}")
            UploadPayload(
                filename = "$filenameBase.jpg",
                contentType = "image/jpeg",
                length = max(temp.length(), 0L),
                compressed = true,
                openStream = { FileInputStream(temp) },
                cleanup = { temp.delete() }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "JPEG preparation failed; falling back to original image", error)
            temp.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyExifToJpeg(original: ByteArray, jpegFile: File) {
        runCatching {
            val source = ExifInterface(ByteArrayInputStream(original))
            val target = ExifInterface(jpegFile)
            exifTagsToPreserve.forEach { tag ->
                source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
            }
            target.saveAttributes()
        }.onFailure { error ->
            Log.w(TAG, "Could not preserve EXIF metadata on JPEG upload payload", error)
        }
    }

    private fun parseBaseUrl(): String {
        val trimmed = RemoteConfigOverrides.parseServerUrl(BuildConfig.PARSE_SERVER_URL).trimEnd('/')
        return when {
            trimmed.endsWith("/parse") -> trimmed
            trimmed.contains("parseapi.back4app.com") -> trimmed
            else -> "$trimmed/parse"
        }
    }

    private data class UploadPayload(
        val filename: String,
        val contentType: String,
        val length: Long,
        val compressed: Boolean,
        val openStream: () -> InputStream,
        val cleanup: () -> Unit = {}
    )

    private val exifTagsToPreserve = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_ORIENTATION
    )
}
