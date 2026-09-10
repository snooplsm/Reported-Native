package com.reported.nativeandroid.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import android.util.Log
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.batch.BatchSubmitStore
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.LocalSubmissionMediaCleaner
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.nativeandroid.remoteconfig.ReportedRemoteConfig
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.Catalogs
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.DraftMedia
import com.reported.shared.model.DraftPlateCandidate
import com.reported.shared.model.PhiladelphiaMobilityAccessCatalogs
import com.reported.shared.model.PhiladelphiaMobilityAccessDetails
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.ReportDraft
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.SubmitReportMediaFile
import com.reported.shared.model.VehicleLookupDetails
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal class ComposerVehicleLookupCoordinator(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val currentState: () -> ComposerUiState,
    private val updateState: ((ComposerUiState) -> ComposerUiState) -> Unit,
    private val onPersistDraft: () -> Unit
) {
    private var vehicleClassificationDebugJob: Job? = null
    private var vehicleDetailsLookupJob: Job? = null

private fun refreshVehicleClassificationDebugNote() {
    if (!BuildConfig.DEBUG) return
    val normalizedPlate = PlatePatternClassifier.normalizePlateInput(currentState().plate)
        .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
    if (
        normalizedPlate.length < 2 ||
        (normalizedPlate.firstOrNull() != 'T' && normalizedPlate.firstOrNull() != 'Y')
    ) {
        applyVehicleClassificationDebugNote(null)
        return
    }

    vehicleClassificationDebugJob?.cancel()
    vehicleClassificationDebugJob = scope.launch {
        delay(350)
        val debugNote = runCatching {
            AppGraph.shared.previewVehicleEnrichmentDebugNoteUseCase.execute(normalizedPlate)
        }.getOrNull()
        val latestPlate = PlatePatternClassifier.normalizePlateInput(currentState().plate)
            .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
        if (latestPlate == normalizedPlate) {
            applyVehicleClassificationDebugNote(debugNote)
        }
    }
}

fun refresh() {
    refreshVehicleDetailsLookup()
    refreshVehicleClassificationDebugNote()
}

private fun refreshVehicleDetailsLookup() {
    val previousDetails = currentState().vehicleLookupDetails
    val lookupPlate = PlatePatternClassifier.normalizePlateInput(currentState().plate)
        .takeIf { it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH }
    val lookupState = currentState().plateRegion.trim().uppercase()
        .takeIf { it.matches(Regex("^[A-Z]{2}$")) }
    vehicleDetailsLookupJob?.cancel()
    if (previousDetails != null) {
        updateState { current ->
            current.copy(
                philadelphiaMobilityAccessDetails = current.philadelphiaMobilityAccessDetails
                    .withoutVehicleLookupPrefill(previousDetails)
            )
        }
    }
    if (lookupPlate == null || lookupState == null) {
        updateState {
            it.copy(
                vehicleLookupDetails = null,
                vehicleLookupInFlight = false,
                vehicleLookupMessage = null
            )
        }
        return
    }

    val lookupKey = "$lookupState:$lookupPlate"
    updateState {
        it.copy(
            vehicleLookupDetails = null,
            vehicleLookupInFlight = true,
            vehicleLookupMessage = null
        )
    }
    vehicleDetailsLookupJob = scope.launch {
        delay(500)
        try {
            val details = AppGraph.shared.lookupVehicleDetailsUseCase.execute(lookupPlate, lookupState)
            if (currentState().vehicleLookupKey() != lookupKey) return@launch
            updateState { current ->
                current.copy(
                    vehicleLookupDetails = details,
                    vehicleLookupInFlight = false,
                    vehicleLookupMessage = if (details == null) {
                        "No vehicle details were found for this plate."
                    } else {
                        null
                    },
                    philadelphiaMobilityAccessDetails = details?.let { vehicle ->
                        current.philadelphiaMobilityAccessDetails.copy(
                            vehicleMake = current.philadelphiaMobilityAccessDetails.vehicleMake
                                .ifBlank { vehicle.vehicleMake.orEmpty() },
                            vehicleModel = current.philadelphiaMobilityAccessDetails.vehicleModel
                                .ifBlank { vehicle.vehicleModel.orEmpty() },
                            bodyStyle = current.philadelphiaMobilityAccessDetails.bodyStyle
                                .ifBlank { vehicle.vehicleBody.orEmpty() }
                        )
                    } ?: current.philadelphiaMobilityAccessDetails
                )
            }
            if (details != null) onPersistDraft()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (currentState().vehicleLookupKey() != lookupKey) return@launch
            updateState {
                it.copy(
                    vehicleLookupDetails = null,
                    vehicleLookupInFlight = false,
                    vehicleLookupMessage = "Vehicle details are unavailable right now. You can still submit the report."
                )
            }
        }
    }
}

private fun applyVehicleClassificationDebugNote(debugNote: String?) {
    if (!BuildConfig.DEBUG) return
    updateState { current ->
        val nextLines = if (current.notes.isBlank()) {
            mutableListOf()
        } else {
            current.notes
                .lineSequence()
                .filterNot { it.startsWith(VehicleClassificationDebugPrefix) }
                .toMutableList()
        }
        debugNote?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(nextLines::add)
        current.copy(notes = nextLines.joinToString("\n"))
    }
    onPersistDraft()
}

}
