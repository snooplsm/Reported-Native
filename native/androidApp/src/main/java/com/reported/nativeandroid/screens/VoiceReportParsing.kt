package com.reported.nativeandroid.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.SvgDecoder
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.reported.nativeandroid.app.ComposerAction
import com.reported.nativeandroid.app.ComposerEvent
import com.reported.nativeandroid.app.ComposerUiState
import com.reported.nativeandroid.app.ComposerViewModel
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.PhiladelphiaSubmissionVideoMessage
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.app.ReportsMode
import com.reported.nativeandroid.app.ReportsViewModel
import com.reported.nativeandroid.app.SharedMediaRequest
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.SubmissionStage
import com.reported.nativeandroid.app.isPhiladelphiaSubmission
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.ai.ReportedAiModelStore
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.report.NewReportTutorialSheet
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.ComplaintCategory
import com.reported.shared.model.PhiladelphiaMobilityAccessCatalogs
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.ReportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

internal class VoiceReportAudioRecorder {
    companion object {
        const val WAV_HEADER_BYTES = 44
        private const val SampleRateHz = 16_000
        private const val BitsPerSample = 16
        private const val ChannelCount = 1
    }

    @Volatile private var isRecording = false
    private var recorder: AudioRecord? = null
    private var outputFile: File? = null
    private var writerThread: Thread? = null
    @Volatile private var amplitudeCallback: ((Float) -> Unit)? = null
    var errorMessage: String? = null
        private set

    @SuppressLint("MissingPermission")
    fun start(context: Context, onAmplitude: (Float) -> Unit = {}): Boolean {
        cancel()
        errorMessage = null

        val minBufferSize = AudioRecord.getMinBufferSize(
            SampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            errorMessage = "This device cannot provide microphone audio in the format Gemma needs."
            return false
        }

        val meteringBufferSize = SampleRateHz / 10 * ChannelCount * BitsPerSample / 8
        val bufferSize = max(minBufferSize, meteringBufferSize)
        val file = runCatching {
            File.createTempFile("reported_voice_report_", ".wav", context.cacheDir)
        }.getOrElse {
            errorMessage = it.localizedMessage ?: "Could not create a temporary recording file."
            return false
        }

        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrElse {
            errorMessage = it.localizedMessage ?: "Could not open the microphone."
            runCatching { file.delete() }
            return false
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            errorMessage = "Could not initialize the microphone."
            audioRecord.release()
            runCatching { file.delete() }
            return false
        }

        runCatching {
            RandomAccessFile(file, "rw").use { output ->
                writeWavHeader(output, pcmDataLength = 0L)
            }
            audioRecord.startRecording()
        }.onFailure {
            errorMessage = it.localizedMessage ?: "Could not start microphone recording."
            audioRecord.release()
            runCatching { file.delete() }
            return false
        }

        recorder = audioRecord
        outputFile = file
        amplitudeCallback = onAmplitude
        isRecording = true
        writerThread = thread(start = true, name = "ReportedVoiceReportRecorder") {
            writeMicInput(audioRecord, file, bufferSize)
        }
        return true
    }

    fun stop(): File? {
        val file = outputFile
        isRecording = false
        writerThread?.join(1_500)
        writerThread = null
        recorder = null
        outputFile = null
        amplitudeCallback?.invoke(0f)
        amplitudeCallback = null
        return file?.takeIf { it.exists() && it.length() > WAV_HEADER_BYTES }
    }

    fun cancel() {
        val file = outputFile
        isRecording = false
        writerThread?.join(750)
        writerThread = null
        recorder = null
        outputFile = null
        amplitudeCallback?.invoke(0f)
        amplitudeCallback = null
        runCatching { file?.delete() }
    }

    private fun writeMicInput(audioRecord: AudioRecord, file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var pcmDataLength = 0L
        var lastAmplitudeEmitNanos = 0L
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.seek(WAV_HEADER_BYTES.toLong())
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        pcmDataLength += read
                        val now = System.nanoTime()
                        if (now - lastAmplitudeEmitNanos >= 64_000_000L) {
                            lastAmplitudeEmitNanos = now
                            amplitudeCallback?.invoke(calculateAmplitude(buffer, read))
                        }
                    }
                }
                output.seek(0L)
                writeWavHeader(output, pcmDataLength)
            }
        } catch (error: Throwable) {
            errorMessage = error.localizedMessage ?: "Could not write microphone recording."
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
    }

    private fun calculateAmplitude(buffer: ByteArray, read: Int): Float {
        var sumSquares = 0.0
        var sampleCount = 0
        var index = 0
        while (index + 1 < read) {
            val low = buffer[index].toInt() and 0xff
            val high = buffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            sampleCount += 1
            index += 2
        }
        if (sampleCount == 0) return 0f
        val rms = sqrt(sumSquares / sampleCount.toDouble()) / Short.MAX_VALUE.toDouble()
        val decibels = 20.0 * log10(rms.coerceAtLeast(0.000_001))
        val normalized = ((decibels + 60.0) / 60.0).coerceIn(0.0, 1.0)
        return normalized.pow(1.35).toFloat().coerceIn(0f, 1f)
    }

    private fun writeWavHeader(output: RandomAccessFile, pcmDataLength: Long) {
        val byteRate = SampleRateHz * ChannelCount * BitsPerSample / 8
        val blockAlign = ChannelCount * BitsPerSample / 8
        output.writeBytes("RIFF")
        output.writeIntLe((36L + pcmDataLength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(ChannelCount)
        output.writeIntLe(SampleRateHz)
        output.writeIntLe(byteRate)
        output.writeShortLe(blockAlign)
        output.writeShortLe(BitsPerSample)
        output.writeBytes("data")
        output.writeIntLe(pcmDataLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }
}

internal data class VoiceDraftChangeRow(
    val field: VoiceDraftField,
    val label: String,
    val currentValue: String,
    val nextValue: String
)

internal enum class VoiceDraftField {
    Complaint,
    Plate,
    State,
    Address,
    OccurredAt,
    Description,
    Notes
}

internal enum class VoiceDraftSource(val title: String) {
    Current("Current"),
    ReportedAi("Reported AI")
}

internal data class VoiceReportDraft(
    val complaintId: String? = null,
    val complaintTitle: String? = null,
    val plate: String? = null,
    val plateRegion: String? = null,
    val address: String? = null,
    val occurredAtIso: String? = null,
    val make: String? = null,
    val model: String? = null,
    val yearRange: String? = null,
    val description: String? = null,
    val notes: String? = null
) {
    val hasAnyFillableField: Boolean
        get() = listOf(complaintId, plate, plateRegion, address, occurredAtIso, description, notes).any { !it.isNullOrBlank() }

    fun keepingReportedFields(reportedFields: Set<VoiceDraftField>): VoiceReportDraft =
        copy(
            complaintId = complaintId.takeIf { VoiceDraftField.Complaint in reportedFields },
            complaintTitle = complaintTitle.takeIf { VoiceDraftField.Complaint in reportedFields },
            plate = plate.takeIf { VoiceDraftField.Plate in reportedFields },
            plateRegion = plateRegion.takeIf { VoiceDraftField.State in reportedFields },
            address = address.takeIf { VoiceDraftField.Address in reportedFields },
            occurredAtIso = occurredAtIso.takeIf { VoiceDraftField.OccurredAt in reportedFields },
            description = description.takeIf { VoiceDraftField.Description in reportedFields },
            notes = notes.takeIf { VoiceDraftField.Notes in reportedFields }
        )

    fun changeRows(
        currentState: ComposerUiState,
        complaintOptions: List<ComplaintOption>
    ): List<VoiceDraftChangeRow> = buildList {
        val currentComplaint = complaintOptions.firstOrNull { it.id == currentState.selectedComplaintId }?.title
        addVoiceChangeRow(VoiceDraftField.Complaint, "Complaint", currentComplaint, complaintTitle)
        addVoiceChangeRow(VoiceDraftField.Plate, "Plate", currentState.plate, plate)
        addVoiceChangeRow(VoiceDraftField.State, "State", currentState.plateRegion, plateRegion)
        addVoiceChangeRow(VoiceDraftField.Address, "Address", currentState.addressQuery.ifBlank { currentState.address }, address)
        addVoiceChangeRow(VoiceDraftField.OccurredAt, "Occurred At", currentState.occurredAtIso, occurredAtIso)
        addVoiceChangeRow(VoiceDraftField.Description, "Description", currentState.description, description)
        addVoiceChangeRow(VoiceDraftField.Notes, "Notes", currentState.notes, notes)
    }
}

internal fun MutableList<VoiceDraftChangeRow>.addVoiceChangeRow(
    field: VoiceDraftField,
    label: String,
    currentValue: String?,
    nextValue: String?
) {
    val cleanedNext = nextValue.cleanedVoicePreviewValue() ?: return
    add(
        VoiceDraftChangeRow(
            field = field,
            label = label,
            currentValue = currentValue.cleanedVoicePreviewValue() ?: "Empty",
            nextValue = cleanedNext
        )
    )
}

internal fun String?.cleanedVoicePreviewValue(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

internal fun generateVoiceReportDraft(
    transcript: String,
    complaintOptions: List<ComplaintOption>
): VoiceReportDraft {
    val cleanedTranscript = transcript.trim()
    val complaint = inferVoiceComplaint(cleanedTranscript, complaintOptions)
    return VoiceReportDraft(
        complaintId = complaint?.id,
        complaintTitle = complaint?.title,
        plate = inferVoicePlate(cleanedTranscript),
        plateRegion = inferVoicePlateRegion(cleanedTranscript),
        address = inferVoiceAddress(cleanedTranscript),
        occurredAtIso = inferVoiceOccurredAt(cleanedTranscript),
        make = inferVoiceVehicleMake(cleanedTranscript),
        model = inferVoiceVehicleModel(cleanedTranscript),
        yearRange = inferVoiceVehicleYearRange(cleanedTranscript),
        description = inferVoiceDescription(cleanedTranscript),
        notes = inferVoiceNotes(cleanedTranscript)
    )
}

internal fun buildVoiceReportGemmaPrompt(
    complaintOptions: List<ComplaintOption>,
    voiceContext: VoiceReportContext
): String {
    val complaints = complaintOptions.joinToString(separator = "\n") { option ->
        "- ${option.id}: ${option.title}"
    }
    val contextBlock = voiceContext.toPromptBlock()
    val currentDate = LocalDate.now(ZoneId.systemDefault())
    val currentYear = currentDate.year
    return """
        You are filling a Reported traffic complaint form from one spoken audio recording and an optional attached report photo.
        Transcribe the speech, then return only one strict JSON object.
        Do not include markdown or prose.

        Available complaints:
        $complaints

        Current form and device context:
        $contextBlock

        JSON keys:
        {
          "transcript": string or null,
          "complaint": one available complaint title or null,
          "complaintId": one available complaint ID or null,
          "timeofincident": ISO-8601 incident datetime with timezone or null,
          "occurredAtIso": same value as timeofincident or null,
          "plate": uppercase license plate letters/numbers only, max 8 characters, or null,
          "state": two-letter US plate state, default "NY" only when the speaker implies New York or says no state,
          "address": incident address or null,
          "make": vehicle make, for example "Honda" from "2024 Honda Acura", or null,
          "model": vehicle model, for example "Acura" from "2024 Honda Acura", or null,
          "yearRange": vehicle year or spoken year range, for example "2024" or "2021-2024", or null,
          "description": vehicle description and public-facing incident details or null,
          "notes": extra private details that do not fit another field or null
        }

        Rules:
        - Prefer exact spoken values over guesses.
        - If a report photo is attached, use it as supporting visual context for visible plate text, vehicle details, location clues, and complaint type before producing JSON.
        - If imageVisualContext is present, treat it as a pre-read summary of the attached photo.
        - Spoken values win when audio conflicts with the photo. Do not invent fields from the photo unless they are clearly visible.
        - The current date is $currentDate and the current year is $currentYear. For spoken dates without an explicit year: if the current month is January and the spoken incident month is December, use ${currentYear - 1}. Otherwise, use $currentYear.
        - timeofincident and occurredAtIso must use that month/year rule; do not roll non-December dates back to a previous year.
        - Use current context only when the speaker explicitly refers to it, such as "here", "this location", "current address", "same address", "now", "today", "same time as the photo", "the image time", "same plate", or "keep the state".
        - If the speaker says "here" or "current address", use currentAddress when present; otherwise use imageAddress when present; otherwise use currentCoordinates when present; otherwise use null.
        - If the speaker says "now" or gives a relative time, resolve it against currentDeviceTimeIso.
        - If the speaker says "time from the photo" or "same time as the photo", use imageOccurredAtIso when present.
        - If a field was not spoken, use null.
        - Extract vehicle make, model, and yearRange only when the speaker says them. Do not infer trim, color, or vehicle type into these keys.
        - If the speaker says a phrase like "2024 Honda Acura", set yearRange to "2024", make to "Honda", and model to "Acura".
        - Put any extra information in notes.
        - Pick complaintId only from the available IDs and complaint only from the available titles.
    """.trimIndent()
}

internal fun parseVoiceReportGemmaJson(
    jsonText: String,
    complaintOptions: List<ComplaintOption>
): VoiceReportGemmaResult {
    val json = JSONObject(extractFirstJsonObject(jsonText))
    val transcript = json.optNullableString("transcript")
    val transcriptFallback = transcript?.let { generateVoiceReportDraft(it, complaintOptions) }
    val complaintId = resolveVoiceComplaintId(
        rawComplaintId = json.optNullableString("complaintId"),
        rawComplaint = json.optNullableString("complaint"),
        complaintOptions = complaintOptions
    )
        ?: transcriptFallback?.complaintId
    val complaintTitle = complaintOptions.firstOrNull { it.id == complaintId }?.title
    val plate = PlatePatternClassifier.normalizePlateInput(json.optNullableString("plate").orEmpty())
        .take(VoicePlateMaxLength)
        .ifBlank { null }
        ?: transcriptFallback?.plate
    val draft = VoiceReportDraft(
        complaintId = complaintId,
        complaintTitle = complaintTitle,
        plate = plate,
        plateRegion = json.optNullableString("state")?.uppercase(Locale.US)?.take(2)
            ?: transcriptFallback?.plateRegion,
        address = json.optNullableString("address") ?: transcriptFallback?.address,
        occurredAtIso = sanitizeVoiceOccurredAt(
            json.optNullableString("occurredAtIso")
                ?: json.optNullableString("timeofincident")
                ?: json.optNullableString("timeOfIncident")
                ?: json.optNullableString("time_of_incident")
        ) ?: transcriptFallback?.occurredAtIso,
        make = json.optNullableString("make")
            ?: json.optNullableString("vehicleMake")
            ?: json.optNullableString("vehicle_make")
            ?: transcriptFallback?.make,
        model = json.optNullableString("model")
            ?: json.optNullableString("vehicleModel")
            ?: json.optNullableString("vehicle_model")
            ?: transcriptFallback?.model,
        yearRange = json.optNullableString("yearRange")
            ?: json.optNullableString("vehicleYearRange")
            ?: json.optNullableString("vehicle_year_range")
            ?: json.optNullableString("year")
            ?: transcriptFallback?.yearRange,
        description = json.optNullableString("description")
            ?: transcript?.let { extractVoiceSection(it, "description") },
        notes = json.optNullableString("notes") ?: transcriptFallback?.notes
    )
    return VoiceReportGemmaResult(
        transcript = transcript,
        draft = draft
    )
}

internal fun extractFirstJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) error("Gemma did not return a JSON object.")
    return text.substring(start, end + 1)
}

internal const val VoicePlateMaxLength = 8

internal fun resolveVoiceComplaintId(
    rawComplaintId: String?,
    rawComplaint: String?,
    complaintOptions: List<ComplaintOption>
): String? {
    rawComplaintId
        ?.trim()
        ?.takeIf { id -> complaintOptions.any { it.id == id } }
        ?.let { return it }
    val normalizedComplaint = rawComplaint
        ?.lowercase(Locale.US)
        ?.replace(Regex("""[^a-z0-9]+"""), " ")
        ?.trim()
        ?: return null
    if (normalizedComplaint.isBlank()) return null
    return complaintOptions.firstOrNull { option ->
        option.id.equals(rawComplaint, ignoreCase = true) ||
            option.title.lowercase(Locale.US)
                .replace(Regex("""[^a-z0-9]+"""), " ")
                .trim() == normalizedComplaint
    }?.id
        ?: inferVoiceComplaint(normalizedComplaint, complaintOptions)?.id
}

internal fun sanitizeVoiceOccurredAt(rawValue: String?): String? {
    val cleaned = rawValue
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: return null
    val instant = runCatching { Instant.parse(cleaned) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(cleaned).toInstant() }.getOrNull()
        }
        ?: return cleaned
    return normalizeVoiceIncidentYear(instant).toString()
}

internal fun normalizeVoiceIncidentYear(instant: Instant): Instant {
    val zone = ZoneId.systemDefault()
    val currentDate = LocalDate.now(zone)
    val currentYear = currentDate.year
    val zonedDateTime = instant.atZone(zone)
    val targetYear = if (currentDate.monthValue == 1 && zonedDateTime.monthValue == 12) currentYear - 1 else currentYear
    return zonedDateTime.withYear(targetYear).toInstant()
}

internal fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key)
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

internal fun inferVoiceComplaint(
    transcript: String,
    complaintOptions: List<ComplaintOption>
): ComplaintOption? {
    val normalized = transcript.lowercase(Locale.US)
    val aliases = listOf(
        listOf("blocked bike lane", "bike lane") to listOf("bike", "lane"),
        listOf("blocked crosswalk", "crosswalk") to listOf("crosswalk"),
        listOf("ran red light", "red light", "stop sign") to listOf("red", "light"),
        listOf("parked illegally", "illegal parking") to listOf("park"),
        listOf("reckless driving", "reckless") to listOf("reckless")
    )
    aliases.forEach { (phrases, optionTerms) ->
        if (phrases.any { it in normalized }) {
            complaintOptions.firstOrNull { option ->
                val haystack = "${option.id} ${option.title}".lowercase(Locale.US)
                optionTerms.all { it in haystack }
            }?.let { return it }
        }
    }
    return complaintOptions.firstOrNull { option ->
        val words = option.title
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
        words.isNotEmpty() && words.all { it in normalized }
    }
}

internal fun inferVoicePlate(transcript: String): String? {
    val explicit = Regex(
        """\b(?:license\s+plate|plate|tag)\s*(?:is|number|#|:)?\s*([a-z0-9][a-z0-9 -]{1,12}?)(?=\s+(?:state|address|complaint|description|notes|time|at|near)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    val fallback = Regex("""\b[a-z0-9]{5,10}\b""", RegexOption.IGNORE_CASE)
        .findAll(transcript)
        .map { it.value }
        .firstOrNull { token -> token.any(Char::isDigit) && token.any(Char::isLetter) }
    return PlatePatternClassifier.normalizePlateInput(explicit ?: fallback.orEmpty())
        .take(VoicePlateMaxLength)
        .ifBlank { null }
}

internal fun inferVoicePlateRegion(transcript: String): String? {
    val normalized = transcript.lowercase(Locale.US)
    val explicit = Regex("""\b(?:state|plate\s+state)\s*(?:is|:)?\s*([a-z]{2}|new york|new jersey|connecticut|pennsylvania)\b""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    val state = explicit ?: when {
        "new york" in normalized -> "new york"
        "new jersey" in normalized -> "new jersey"
        "connecticut" in normalized -> "connecticut"
        "pennsylvania" in normalized -> "pennsylvania"
        else -> null
    }
    return when (state?.trim()) {
        "new york", "ny" -> "NY"
        "new jersey", "nj" -> "NJ"
        "connecticut", "ct" -> "CT"
        "pennsylvania", "pa" -> "PA"
        else -> state?.uppercase(Locale.US)?.takeIf { it.length == 2 }
    }
}

internal fun inferVoiceAddress(transcript: String): String? {
    val match = Regex("""\b(?:address\s+is|address|near|at)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    val candidate = match.groupValues[1]
        .split(Regex("""\b(?:plate|license\s+plate|state|complaint|description|notes|time|when|occurred)\b""", RegexOption.IGNORE_CASE))
        .firstOrNull()
        ?.trim(' ', ',', '.', ';')
        .orEmpty()
    if (candidate.matches(Regex("""\d{1,2}(:\d{2})?\s*(am|pm).*""", RegexOption.IGNORE_CASE))) return null
    return candidate.takeIf { it.length >= 4 }
}

internal fun inferVoiceOccurredAt(transcript: String): String? {
    val normalized = transcript.lowercase(Locale.US)
    val zone = ZoneId.systemDefault()
    if ("now" in normalized || "right now" in normalized) {
        return Instant.now().toString()
    }
    var date = LocalDate.now(zone)
    if ("yesterday" in normalized) {
        date = date.minusDays(1)
    }
    val timeMatch = Regex("""\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE)
        .find(transcript)
    val time = timeMatch?.let { match ->
        val rawHour = match.groupValues[1].toIntOrNull() ?: return@let null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val isPm = match.groupValues.last().lowercase(Locale.US).startsWith("p")
        val hour = when {
            isPm && rawHour < 12 -> rawHour + 12
            !isPm && rawHour == 12 -> 0
            else -> rawHour
        }
        runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
    if (time != null) {
        return date.atTime(time).atZone(zone).toInstant().toString()
    }
    if ("today" in normalized || "yesterday" in normalized) {
        return date.atStartOfDay(zone).toInstant().toString()
    }
    return null
}

internal fun inferVoiceVehicleYearRange(transcript: String): String? {
    val match = Regex("""\b((?:19|20)\d{2})(?:\s*(?:-|to|through)\s*((?:19|20)\d{2}))?\b""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    val firstYear = match.groupValues.getOrNull(1).orEmpty()
    val secondYear = match.groupValues.getOrNull(2).orEmpty()
    if (firstYear.isBlank()) return null
    return if (secondYear.isBlank()) firstYear else "$firstYear-$secondYear"
}

internal fun inferVoiceVehicleMake(transcript: String): String? {
    val explicit = Regex(
        """\b(?:make|vehicle\s+make)\s*(?:is|:)?\s*([A-Za-z][A-Za-z -]{1,28}?)(?=\s+(?:model|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    return normalizeVoiceVehicleToken(explicit) ?: voiceVehicleMakeModelPhrase(transcript)?.first
}

internal fun inferVoiceVehicleModel(transcript: String): String? {
    val explicit = Regex(
        """\b(?:model|vehicle\s+model)\s*(?:is|:)?\s*([A-Za-z0-9][A-Za-z0-9 -]{1,32}?)(?=\s+(?:make|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    return normalizeVoiceVehicleToken(explicit) ?: voiceVehicleMakeModelPhrase(transcript)?.second
}

internal fun voiceVehicleMakeModelPhrase(transcript: String): Pair<String, String>? {
    val match = Regex(
        """\b(?:19|20)\d{2}(?:\s*(?:-|to|through)\s*(?:19|20)\d{2})?\s+([A-Za-z][A-Za-z-]+)\s+([A-Za-z][A-Za-z0-9-]+)\b""",
        RegexOption.IGNORE_CASE
    ).find(transcript) ?: return null
    val make = normalizeVoiceVehicleToken(match.groupValues.getOrNull(1)) ?: return null
    val model = normalizeVoiceVehicleToken(match.groupValues.getOrNull(2)) ?: return null
    return make to model
}

internal fun normalizeVoiceVehicleToken(raw: String?): String? {
    val cleaned = raw
        ?.trim(' ', ',', '.', ';', ':')
        ?.replace(Regex("""\s+"""), " ")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return cleaned
        .split(" ")
        .joinToString(" ") { token ->
            if (token.length <= 4 && token.all { it.isUpperCase() || it.isDigit() }) {
                token
            } else {
                token.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
            }
        }
}

internal fun inferVoiceDescription(transcript: String): String? {
    val explicit = extractVoiceSection(transcript, "description")
    return (explicit ?: transcript.trim())
        .take(280)
        .takeIf { it.isNotBlank() }
}

internal fun inferVoiceNotes(transcript: String): String? =
    extractVoiceSection(transcript, "notes") ?: extractVoiceSection(transcript, "note")

internal fun extractVoiceSection(transcript: String, label: String): String? {
    val match = Regex("""\b$label\s*(?:are|is|:)?\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    return match.groupValues[1]
        .split(Regex("""\b(?:plate|license\s+plate|state|complaint|description|notes|address|time|when|occurred)\b""", RegexOption.IGNORE_CASE))
        .firstOrNull()
        ?.trim(' ', ',', '.', ';')
        ?.takeIf { it.isNotBlank() }
}
