package com.reported.nativeandroid.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.net.toUri
import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

data class LiveIncidentClipResult(
    val uri: String?,
    val error: String? = null
)

class LiveRollingVideoRecorder(
    context: Context,
    private val mainExecutor: Executor
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val segmentDir = File(appContext.cacheDir, "live-video-segments").apply { mkdirs() }
    private val clipDir = File(appContext.cacheDir, "live-incident-clips").apply { mkdirs() }
    private val completedSegments = ArrayDeque<LiveVideoSegment>()
    private val pendingClipRequests = mutableListOf<PendingClipRequest>()
    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.HD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        )
        .build()

    private var currentRecording: Recording? = null
    private var activeSegment: ActiveLiveVideoSegment? = null
    private var audioEnabled = false
    private var released = false
    private var rolloverRunnable: Runnable? = null
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    fun start(recordAudio: Boolean) {
        if (released) return
        if (currentRecording != null) {
            setAudioEnabled(recordAudio)
            return
        }
        audioEnabled = recordAudio
        startSegment()
    }

    fun setAudioEnabled(recordAudio: Boolean) {
        if (released) return
        if (audioEnabled == recordAudio) return
        audioEnabled = recordAudio
        rolloverSegment()
    }

    fun requestIncidentClip(requestId: Long, onResult: (LiveIncidentClipResult) -> Unit) {
        if (released) {
            onResult(LiveIncidentClipResult(uri = null, error = "Live recorder is not running."))
            return
        }
        val now = System.currentTimeMillis()
        pendingClipRequests += PendingClipRequest(
            id = requestId,
            windowStartMs = now - IncidentPreRollMs,
            windowEndMs = now + IncidentPostRollMs,
            onResult = onResult
        )
    }

    fun shutdown() {
        released = true
        rolloverRunnable?.let(handler::removeCallbacks)
        currentRecording?.stop()
        currentRecording = null
        activeSegment = null
        ioExecutor.shutdown()
    }

    @SuppressLint("MissingPermission")
    private fun startSegment() {
        if (released) return
        val startedAtMs = System.currentTimeMillis()
        val file = File(segmentDir, "live_${startedAtMs}.mp4")
        activeSegment = ActiveLiveVideoSegment(file, startedAtMs)
        val outputOptions = FileOutputOptions.Builder(file).build()
        var pendingRecording: PendingRecording = recorder.prepareRecording(appContext, outputOptions)
        if (audioEnabled) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }
        currentRecording = pendingRecording.start(mainExecutor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val active = activeSegment
                val finalizedFile = event.outputResults.outputUri.toFileOrNull() ?: active?.file
                if (active != null && finalizedFile != null && finalizedFile.exists() && finalizedFile.length() > 0L) {
                    completedSegments += LiveVideoSegment(
                        file = finalizedFile,
                        startMs = active.startMs,
                        endMs = System.currentTimeMillis()
                    )
                    pruneSegments()
                }
                activeSegment = null
                currentRecording = null
                processReadyClipRequests()
                if (!released) startSegment()
            }
        }
        scheduleRollover(SegmentDurationMs)
    }

    private fun rolloverSegment() {
        if (released) return
        rolloverRunnable?.let(handler::removeCallbacks)
        rolloverRunnable = null
        currentRecording?.stop()
        if (currentRecording == null) {
            processReadyClipRequests()
            startSegment()
        }
    }

    private fun scheduleRollover(delayMs: Long) {
        rolloverRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { rolloverSegment() }
        rolloverRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun pruneSegments() {
        val cutoff = System.currentTimeMillis() - RollingWindowMs - SegmentDurationMs
        while (completedSegments.isNotEmpty() && completedSegments.first().endMs < cutoff) {
            completedSegments.removeFirst().file.delete()
        }
    }

    private fun processReadyClipRequests() {
        if (pendingClipRequests.isEmpty()) return
        val now = System.currentTimeMillis()
        val ready = pendingClipRequests.filter { now >= it.windowEndMs }
        pendingClipRequests.removeAll(ready.toSet())
        ready.forEach { request ->
            val sources = completedSegments
                .filter { it.endMs >= request.windowStartMs && it.startMs <= request.windowEndMs }
                .map { it.file }
                .filter { it.exists() && it.length() > 0L }
            if (sources.isEmpty()) {
                request.onResult(LiveIncidentClipResult(uri = null, error = "No rolling video segment was available."))
                return@forEach
            }
            ioExecutor.execute {
                val output = File(clipDir, "live_incident_${request.id}.mp4")
                val result = runCatching {
                    if (sources.size == 1) {
                        sources.first().copyTo(output, overwrite = true)
                    } else {
                        concatenateMp4Segments(sources, output)
                    }
                    LiveIncidentClipResult(uri = output.toUri().toString())
                }.getOrElse { error ->
                    LiveIncidentClipResult(uri = null, error = error.message ?: "Could not create incident clip.")
                }
                handler.post { request.onResult(result) }
            }
        }
    }
}

private fun concatenateMp4Segments(sourceFiles: List<File>, outputFile: File) {
    if (outputFile.exists()) outputFile.delete()
    val firstExtractor = MediaExtractor()
    firstExtractor.setDataSource(sourceFiles.first().absolutePath)
    val selectedMimes = buildList {
        for (trackIndex in 0 until firstExtractor.trackCount) {
            val format = firstExtractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") || mime.startsWith("audio/")) add(mime)
        }
    }
    firstExtractor.release()
    if (selectedMimes.isEmpty()) error("No audio or video tracks found.")

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val muxerTracks = mutableMapOf<String, Int>()
    var started = false
    var presentationOffsetUs = 0L

    try {
        sourceFiles.forEachIndexed { fileIndex, file ->
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            val sourceTracks = mutableListOf<Pair<Int, String>>()
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime in selectedMimes) {
                    sourceTracks += trackIndex to mime
                    if (fileIndex == 0) {
                        muxerTracks[mime] = muxer.addTrack(format)
                    }
                }
            }
            if (!started) {
                muxer.start()
                started = true
            }

            var segmentDurationUs = 0L
            sourceTracks.forEach { (trackIndex, mime) ->
                val muxerTrack = muxerTracks[mime] ?: return@forEach
                extractor.unselectAllTracks()
                extractor.selectTrack(trackIndex)
                val maxInputSize = extractor.getTrackFormat(trackIndex)
                    .getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, DefaultMuxerBufferSize)
                val buffer = ByteBuffer.allocate(max(DefaultMuxerBufferSize, maxInputSize))
                val bufferInfo = MediaCodec.BufferInfo()
                var lastSampleTimeUs = 0L
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val sampleTimeUs = extractor.sampleTime.takeIf { it >= 0L } ?: break
                    val sampleFlags = extractor.sampleFlags
                    val bufferFlags =
                        (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }) or
                            (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                                MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                            } else {
                                0
                            })
                    bufferInfo.set(
                        0,
                        sampleSize,
                        presentationOffsetUs + sampleTimeUs,
                        bufferFlags
                    )
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    lastSampleTimeUs = sampleTimeUs
                    buffer.clear()
                    extractor.advance()
                }
                segmentDurationUs = max(segmentDurationUs, lastSampleTimeUs)
            }
            presentationOffsetUs += segmentDurationUs + SegmentPtsPaddingUs
            extractor.release()
        }
    } finally {
        if (started) muxer.stop()
        muxer.release()
    }
}

private fun MediaExtractor.unselectAllTracks() {
    for (trackIndex in 0 until trackCount) {
        runCatching { unselectTrack(trackIndex) }
    }
}

private fun MediaFormat.getIntegerOrDefault(key: String, defaultValue: Int): Int =
    if (containsKey(key)) getInteger(key) else defaultValue

private fun Uri.toFileOrNull(): File? =
    takeIf { it.scheme == "file" }?.path?.let(::File)

private data class ActiveLiveVideoSegment(
    val file: File,
    val startMs: Long
)

private data class LiveVideoSegment(
    val file: File,
    val startMs: Long,
    val endMs: Long
)

private data class PendingClipRequest(
    val id: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val onResult: (LiveIncidentClipResult) -> Unit
)

private const val SegmentDurationMs = 5_000L
private const val RollingWindowMs = 30_000L
private const val IncidentPreRollMs = 10_000L
private const val IncidentPostRollMs = 10_000L
private const val SegmentPtsPaddingUs = 33_333L
private const val DefaultMuxerBufferSize = 1 * 1024 * 1024
