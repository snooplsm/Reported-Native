package com.reported.nativeandroid.ai

import android.content.Context
import com.reported.nativeandroid.R
import com.reported.nativeandroid.di.MessageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ReportedAiModelStore {
    const val DownloadSizeLabel = "2.6 GB"
    const val DownloadFileName = "gemma-4-E2B-it.litertlm"

    enum class AccelerationStatus {
        Unknown,
        Accelerated,
        CpuFallback
    }

    private const val DownloadEstimatedBytes = 2_590_000_000L
    private const val DownloadMinimumBytes = 512L * 1024L * 1024L
    private const val DownloadUrl =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    private const val PrefsName = "reported.ai.model"
    private const val AccelerationStatusKey = "acceleration_status"
    private val ModelFileNames = listOf(
        "gemma-voice-report.litertlm",
        "gemma-4-E2B-it.litertlm",
        "gemma-4-E4B-it.litertlm"
    )

    val compactDownloadSizeLabel: String
        get() = DownloadSizeLabel.replace(" ", "").lowercase(Locale.US)

    fun isModelInstalled(context: Context): Boolean = resolveModelFile(context) != null

    fun installedModelSizeLabel(context: Context): String? =
        resolveModelFile(context)?.length()?.let(::formatBytes)

    fun accelerationStatus(context: Context): AccelerationStatus =
        runCatching {
            AccelerationStatus.valueOf(
                prefs(context).getString(AccelerationStatusKey, AccelerationStatus.Unknown.name)
                    ?: AccelerationStatus.Unknown.name
            )
        }.getOrDefault(AccelerationStatus.Unknown)

    fun setAccelerationStatus(context: Context, status: AccelerationStatus) {
        prefs(context)
            .edit()
            .putString(AccelerationStatusKey, status.name)
            .apply()
    }

    fun accelerationMessage(context: Context, messages: MessageResolver): String =
        when (accelerationStatus(context)) {
            AccelerationStatus.Accelerated ->
                messages.resolve(R.string.reported_ai_acceleration_available)
            AccelerationStatus.CpuFallback ->
                messages.resolve(R.string.reported_ai_acceleration_unavailable)
            AccelerationStatus.Unknown ->
                messages.resolve(R.string.reported_ai_acceleration_unknown)
        }

    suspend fun downloadModel(
        context: Context,
        messages: MessageResolver,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            resolveModelFile(context)?.let { return@runCatching it }

            val modelsDir = File(context.noBackupFilesDir, "models").apply { mkdirs() }
            val destinationFile = File(modelsDir, DownloadFileName)
            val partialFile = File(modelsDir, "$DownloadFileName.part")
            if (partialFile.exists()) partialFile.delete()
            if (modelsDir.usableSpace in 1 until DownloadEstimatedBytes) {
                error(messages.resolve(R.string.reported_ai_storage_required, compactDownloadSizeLabel))
            }

            val connection = (URL(DownloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Reported Android")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error(messages.resolve(R.string.reported_ai_server_error, responseCode))
                }
                val totalBytes = connection.contentLengthLong
                    .takeIf { it > 0L }
                    ?: DownloadEstimatedBytes
                connection.inputStream.use { input ->
                    partialFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        var lastProgressBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (
                                downloadedBytes - lastProgressBytes >= 4L * 1024L * 1024L ||
                                downloadedBytes == totalBytes
                            ) {
                                lastProgressBytes = downloadedBytes
                                withContext(Dispatchers.Main) {
                                    onProgress(downloadedBytes, totalBytes)
                                }
                            }
                        }
                        output.flush()
                        withContext(Dispatchers.Main) {
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
            } catch (error: Throwable) {
                partialFile.delete()
                throw error
            } finally {
                connection.disconnect()
            }

            if (destinationFile.exists()) destinationFile.delete()
            if (!partialFile.renameTo(destinationFile)) {
                partialFile.copyTo(destinationFile, overwrite = true)
                partialFile.delete()
            }
            destinationFile.takeIf { it.isUsableModelFile() }
                ?: error(messages.resolve(R.string.reported_ai_install_incomplete))
        }
    }

    suspend fun deleteModel(context: Context): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        val directories = modelSearchDirectories(context) + listOf(context.noBackupFilesDir, context.filesDir)
        directories.distinctBy { it.absolutePath }.forEach { directory ->
            ModelFileNames.forEach { modelFileName ->
                val file = File(directory, modelFileName)
                if (file.exists() && file.delete()) deleted += 1
                val partial = File(directory, "$modelFileName.part")
                if (partial.exists() && partial.delete()) deleted += 1
            }
        }
        resolveCacheDir(context).deleteRecursively()
        setAccelerationStatus(context, AccelerationStatus.Unknown)
        deleted
    }

    fun resolveModelFile(context: Context): File? {
        modelSearchDirectories(context).forEach { directory ->
            ModelFileNames.forEach { modelFileName ->
                val model = File(directory, modelFileName).takeIf { it.isUsableModelFile() }
                if (model != null) return model
            }
        }

        ModelFileNames.forEach { modelFileName ->
            val appNoBackupModel = File(context.noBackupFilesDir, modelFileName).takeIf { it.isUsableModelFile() }
            if (appNoBackupModel != null) return appNoBackupModel
        }

        ModelFileNames.forEach { modelFileName ->
            val filesModel = File(context.filesDir, modelFileName).takeIf { it.isUsableModelFile() }
            if (filesModel != null) return filesModel
        }

        ModelFileNames.forEach { modelFileName ->
            val assetsModel = runCatching {
                context.assets.open(modelFileName).use { input ->
                    val outputFile = File(context.noBackupFilesDir, modelFileName)
                    if (!outputFile.exists() || outputFile.length() == 0L) {
                        outputFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outputFile.takeIf { it.isUsableModelFile() }
                }
            }.getOrNull()
            if (assetsModel != null) return assetsModel
        }
        return null
    }

    fun resolveCacheDir(context: Context): File {
        return File(context.cacheDir, "litertlm").apply { mkdirs() }
    }

    private fun modelSearchDirectories(context: Context): List<File> = listOf(
        File(context.noBackupFilesDir, "models"),
        File(context.filesDir, "models")
    )

    private fun formatBytes(bytes: Long): String {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB", gib)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private fun File.isUsableModelFile(): Boolean =
        exists() && isFile && length() >= DownloadMinimumBytes
}
