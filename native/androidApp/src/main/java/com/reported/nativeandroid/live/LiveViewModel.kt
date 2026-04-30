package com.reported.nativeandroid.live

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.shared.model.Catalogs
import com.reported.shared.model.ComplaintCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import kotlin.math.max

data class LiveUiState(
    val cameraPermissionGranted: Boolean = false,
    val microphonePermissionGranted: Boolean = false,
    val recordAudio: Boolean = false,
    val selectedComplaintId: String = Catalogs.complaintCategories.firstOrNull()?.id.orEmpty(),
    val complaintCategories: List<ComplaintCategory> = Catalogs.complaintCategories,
    val address: String = "",
    val latitude: Double = 40.7128,
    val longitude: Double = -74.0060,
    val processing: Boolean = false,
    val lastFrameProcessedAtMs: Long = 0L,
    val plateCandidates: List<PlateCandidate> = emptyList(),
    val selectedComplaintDetectedId: String? = null,
    val queuedIncidents: List<LiveIncidentRecord> = emptyList(),
    val nextOffset: Int = 0,
    val hasMore: Boolean = true,
    val clipRequestId: Long? = null,
    val capturingIncidentClip: Boolean = false,
    val error: String? = null
) {
    val bestCandidate: PlateCandidate?
        get() = plateCandidates.firstOrNull()

    val canQueueManualIncident: Boolean
        get() = bestCandidate != null && selectedComplaintId.isNotBlank() && address.isNotBlank()
}

sealed interface LiveAction {
    data class CameraPermissionChanged(val granted: Boolean) : LiveAction
    data class MicrophonePermissionChanged(val granted: Boolean) : LiveAction
    data class RecordAudioChanged(val enabled: Boolean) : LiveAction
    data class ComplaintChanged(val complaintId: String) : LiveAction
    data class LocationChanged(val address: String, val latitude: Double, val longitude: Double) : LiveAction
    data object MarkIncident : LiveAction
    data class IncidentClipReady(val requestId: Long, val videoUri: String?) : LiveAction
    data class IncidentClipFailed(val requestId: Long, val message: String) : LiveAction
    data class ShowMessage(val message: String) : LiveAction
    data class DeleteIncident(val incident: LiveIncidentRecord) : LiveAction
    data class UpdateIncident(val incident: LiveIncidentRecord) : LiveAction
    data class SubmitIncidents(val ids: Set<Long>) : LiveAction
    data object LoadMoreIncidents : LiveAction
    data object LoadAllIncidents : LiveAction
    data object ClearError : LiveAction
}

private data class PendingLiveIncident(
    val createdAtEpochMs: Long,
    val incidentAtIso: String,
    val complaintId: String,
    val plate: String,
    val plateRegion: String,
    val plateConfidence: Float,
    val stateConfidence: Float?,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailUri: String?
)

class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LiveIncidentStore(application)
    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private var analysisInFlight = false
    private val queuedPlateKeys = mutableSetOf<String>()
    private var nextClipRequestId = 1L
    private val pendingIncidents = mutableMapOf<Long, PendingLiveIncident>()

    init {
        onAction(LiveAction.LoadMoreIncidents)
    }

    fun onAction(action: LiveAction) {
        when (action) {
            is LiveAction.CameraPermissionChanged -> _state.update { it.copy(cameraPermissionGranted = action.granted) }
            is LiveAction.MicrophonePermissionChanged -> _state.update { it.copy(microphonePermissionGranted = action.granted) }
            is LiveAction.RecordAudioChanged -> _state.update { it.copy(recordAudio = action.enabled) }
            is LiveAction.ComplaintChanged -> _state.update { it.copy(selectedComplaintId = action.complaintId) }
            is LiveAction.LocationChanged -> _state.update {
                it.copy(address = action.address, latitude = action.latitude, longitude = action.longitude)
            }
            LiveAction.MarkIncident -> requestIncidentClip(manual = true)
            is LiveAction.IncidentClipReady -> queuePendingIncident(action.requestId, action.videoUri)
            is LiveAction.IncidentClipFailed -> queuePendingIncident(action.requestId, videoUri = null, errorMessage = action.message)
            is LiveAction.ShowMessage -> _state.update { it.copy(error = action.message) }
            is LiveAction.DeleteIncident -> deleteIncident(action.incident)
            is LiveAction.UpdateIncident -> updateIncident(action.incident)
            is LiveAction.SubmitIncidents -> submitIncidents(action.ids)
            LiveAction.LoadMoreIncidents -> loadMore()
            LiveAction.LoadAllIncidents -> loadAll()
            LiveAction.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        val snapshot = _state.value
        val now = System.currentTimeMillis()
        if (analysisInFlight || now - snapshot.lastFrameProcessedAtMs < LiveFrameThrottleMs) return
        analysisInFlight = true
        _state.update { it.copy(processing = true, lastFrameProcessedAtMs = now) }
        viewModelScope.launch {
            val result = runCatching {
                NativeAlprEngine.detectLiveFrame(
                    context = getApplication(),
                    bitmap = bitmap,
                    expectedComplaintHint = snapshot.selectedComplaintId
                )
            }
            result.onSuccess { detection ->
                _state.update {
                    it.copy(
                        processing = false,
                        plateCandidates = detection.candidates,
                        selectedComplaintDetectedId = detection.complaintId,
                        error = null
                    )
                }
                maybeQueueAutomaticIncident()
            }.onFailure { error ->
                _state.update {
                    it.copy(processing = false, error = error.message ?: "Live detection failed.")
                }
            }
            analysisInFlight = false
        }
    }

    private fun maybeQueueAutomaticIncident() {
        val state = _state.value
        val candidate = state.bestCandidate ?: return
        val complaintId = state.selectedComplaintDetectedId ?: return
        if (!candidate.meetsLiveThresholds()) return
        if (state.address.isBlank()) return
        requestIncidentClip(manual = false, complaintOverride = complaintId)
    }

    private fun requestIncidentClip(manual: Boolean, complaintOverride: String? = null) {
        val state = _state.value
        val candidate = state.bestCandidate ?: run {
            _state.update { it.copy(error = "No plate is locked yet.") }
            return
        }
        val complaintId = complaintOverride ?: state.selectedComplaintId
        if (complaintId.isBlank()) {
            _state.update { it.copy(error = "Choose a complaint first.") }
            return
        }
        if (state.address.isBlank()) {
            _state.update { it.copy(error = "Pick the incident location first.") }
            return
        }

        val plateKey = candidate.sessionPlateKey()
        if (plateKey in queuedPlateKeys) {
            if (manual) {
                _state.update { it.copy(error = "This plate is already queued in this Live session.") }
            }
            return
        }
        queuedPlateKeys += plateKey

        val now = Instant.now()
        val requestId = nextClipRequestId++
        pendingIncidents[requestId] = PendingLiveIncident(
            createdAtEpochMs = now.toEpochMilli(),
            incidentAtIso = now.toString(),
            complaintId = complaintId,
            plate = candidate.plate,
            plateRegion = candidate.state ?: "NY",
            plateConfidence = candidate.confidence,
            stateConfidence = candidate.stateConfidence,
            address = state.address,
            latitude = state.latitude,
            longitude = state.longitude,
            thumbnailUri = candidate.thumbnailUri
        )
        _state.update {
            it.copy(
                clipRequestId = requestId,
                capturingIncidentClip = true,
                error = null
            )
        }
    }

    private fun queuePendingIncident(requestId: Long, videoUri: String?, errorMessage: String? = null) {
        val pending = pendingIncidents.remove(requestId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val id = store.insert(
                LiveIncidentRecord(
                    createdAtEpochMs = pending.createdAtEpochMs,
                    incidentAtIso = pending.incidentAtIso,
                    complaintId = pending.complaintId,
                    plate = pending.plate,
                    plateRegion = pending.plateRegion,
                    plateConfidence = pending.plateConfidence,
                    stateConfidence = pending.stateConfidence,
                    address = pending.address,
                    latitude = pending.latitude,
                    longitude = pending.longitude,
                    videoUri = videoUri,
                    thumbnailUri = pending.thumbnailUri,
                    submitted = false
                )
            )
            val inserted = store.page(limit = 1, offset = 0).firstOrNull { it.id == id }
            _state.update {
                it.copy(
                    queuedIncidents = (listOfNotNull(inserted) + it.queuedIncidents).distinctBy { record -> record.id },
                    nextOffset = it.nextOffset + if (inserted != null) 1 else 0,
                    clipRequestId = null,
                    capturingIncidentClip = pendingIncidents.isNotEmpty(),
                    error = errorMessage
                )
            }
        }
    }

    private fun loadMore() {
        val state = _state.value
        if (!state.hasMore) return
        viewModelScope.launch(Dispatchers.IO) {
            val page = store.page(LiveIncidentPageSize, state.nextOffset)
            _state.update {
                it.copy(
                    queuedIncidents = (it.queuedIncidents + page).distinctBy { record -> record.id },
                    nextOffset = it.nextOffset + page.size,
                    hasMore = page.size == LiveIncidentPageSize
                )
            }
        }
    }

    private fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = store.all()
            _state.update {
                it.copy(
                    queuedIncidents = records,
                    nextOffset = records.size,
                    hasMore = false
                )
            }
        }
    }

    private fun deleteIncident(incident: LiveIncidentRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(incident.id)
            incident.videoUri?.deleteLocalFileUri()
            incident.thumbnailUri?.deleteLocalFileUri()
            queuedPlateKeys.remove(incident.sessionPlateKey())
            _state.update {
                it.copy(
                    queuedIncidents = it.queuedIncidents.filterNot { record -> record.id == incident.id },
                    nextOffset = max(0, it.nextOffset - 1)
                )
            }
        }
    }

    private fun updateIncident(incident: LiveIncidentRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            store.update(incident)
            _state.update {
                it.copy(
                    queuedIncidents = it.queuedIncidents.map { record ->
                        if (record.id == incident.id) incident else record
                    }
                )
            }
        }
    }

    private fun submitIncidents(ids: Set<Long>) {
        if (ids.isEmpty()) {
            _state.update { it.copy(error = "Choose at least one report to submit.") }
            return
        }
        viewModelScope.launch {
            val session = com.reported.nativeandroid.di.AppGraph.shared.loadSessionUseCase.execute()
            if (session?.isAuthorized != true) {
                _state.update { it.copy(error = "Sign in before submitting Live reports.") }
                return@launch
            }
            ids.forEach { id ->
                val request = OneTimeWorkRequestBuilder<SubmitLiveIncidentWorker>()
                    .setInputData(workDataOf(SubmitLiveIncidentWorker.KEY_INCIDENT_ID to id))
                    .build()
                WorkManager.getInstance(getApplication()).enqueue(request)
            }
            _state.update { it.copy(error = "Submitting ${ids.size} Live report(s) in the background.") }
        }
    }
}

private fun PlateCandidate.meetsLiveThresholds(): Boolean =
    confidence >= 0.85f && (stateConfidence == null || stateConfidence >= 0.85f)

private fun PlateCandidate.sessionPlateKey(): String =
    "${plate.trim().uppercase()}:${state?.trim()?.uppercase().orEmpty()}"

private fun LiveIncidentRecord.sessionPlateKey(): String =
    "${plate.trim().uppercase()}:${plateRegion.trim().uppercase()}"

private fun String.deleteLocalFileUri() {
    runCatching {
        val uri = Uri.parse(this)
        if (uri.scheme == "file") {
            uri.path?.let(::File)?.takeIf { it.exists() }?.delete()
        }
    }
}

private const val LiveFrameThrottleMs = 900L
private const val LiveIncidentPageSize = 20
