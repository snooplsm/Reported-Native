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


class ReportsViewModel : ViewModel(), UdfStore<ReportsUiState, ReportsAction> {
    private val _state = MutableStateFlow(ReportsUiState())
    override val state: StateFlow<ReportsUiState> = _state.asStateFlow()
    private var activeFilter: ReportFilter? = null

    override fun onAction(action: ReportsAction) {
        when (action) {
            ReportsAction.ListChosen -> chooseList()
            ReportsAction.SearchChosen -> chooseSearch()
            ReportsAction.SearchPressed -> search()
            ReportsAction.NextPageRequested -> loadNextPage()
            is ReportsAction.DetailRequested -> loadDetail(action.reportObjectId)
            is ReportsAction.ReportOpened -> openReport(action.reportObjectId)
            is ReportsAction.ReportDeleted -> deleteReport(action.report)
            is ReportsAction.SearchFieldsChanged -> updateSearch(
                license = action.license,
                startDate = action.startDate,
                endDate = action.endDate
            )
        }
    }

    fun chooseList() {
        activeFilter = null
        ReportedAnalytics.logReportsList()
        _state.update {
            it.copy(
                mode = ReportsMode.LIST,
                reports = emptyList(),
                reportDetails = emptyMap(),
                hasMore = false,
                nextSkip = 0,
                error = null
            )
        }
        load(filter = null, append = false)
    }

    fun chooseSearch() {
        activeFilter = null
        _state.update {
            it.copy(
                mode = ReportsMode.SEARCH,
                reports = emptyList(),
                reportDetails = emptyMap(),
                hasMore = false,
                nextSkip = 0,
                error = null
            )
        }
    }

    fun updateSearch(license: String? = null, startDate: String? = null, endDate: String? = null) {
        _state.update {
            it.copy(
                licenseQuery = license ?: it.licenseQuery,
                startDate = startDate ?: it.startDate,
                endDate = endDate ?: it.endDate
            )
        }
    }

    fun search() {
        val current = _state.value
        val filter = ReportFilter(
            license = current.licenseQuery.trim(),
            startDateIso = current.startDate.trim(),
            endDateIso = current.endDate.trim()
        )
        ReportedAnalytics.logReportsSearch(
            hasLicense = filter.license.isNotBlank(),
            hasStartDate = filter.startDateIso.isNotBlank(),
            hasEndDate = filter.endDateIso.isNotBlank()
        )
        activeFilter = filter
        load(filter = filter, append = false)
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        load(filter = activeFilter, append = true)
    }

    private fun load(filter: ReportFilter?, append: Boolean) {
        viewModelScope.launch {
            val skip = if (append) _state.value.nextSkip else 0
            _state.update {
                it.copy(
                    loading = !append,
                    loadingMore = append,
                    error = null
                )
            }
            runCatching {
                AppGraph.shared.fetchReportsUseCase.execute(filter = filter, skip = skip, forCurrentUser = true)
            }.onSuccess { page ->
                _state.update {
                    val mergedReports = if (append) {
                        (it.reports + page.reports).distinctBy { report -> report.objectId.ifBlank { report.id.toString() } }
                    } else {
                        page.reports
                    }
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        reports = mergedReports,
                        hasMore = page.hasMore,
                        nextSkip = mergedReports.size,
                        reportDetails = if (append) it.reportDetails else emptyMap(),
                        detailLoadingIds = if (append) it.detailLoadingIds else emptySet()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = error.message ?: "Unable to load reports"
                    )
                }
            }
        }
    }

    fun loadDetail(reportObjectId: String) {
        if (reportObjectId.isBlank()) return
        val current = _state.value
        if (current.reportDetails.containsKey(reportObjectId) || reportObjectId in current.detailLoadingIds) return
        viewModelScope.launch {
            _state.update { it.copy(detailLoadingIds = it.detailLoadingIds + reportObjectId, error = null) }
            runCatching {
                AppGraph.shared.fetchReportDetailUseCase.execute(reportObjectId)
            }.onSuccess { detail ->
                _state.update {
                    it.copy(
                        reportDetails = it.reportDetails + (reportObjectId to detail),
                        detailLoadingIds = it.detailLoadingIds - reportObjectId
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        detailLoadingIds = it.detailLoadingIds - reportObjectId,
                        error = error.message ?: "Unable to load report detail"
                    )
                }
            }
        }
    }

    fun openReport(reportObjectId: String) {
        if (reportObjectId.isBlank()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    mode = ReportsMode.LIST,
                    loading = true,
                    loadingMore = false,
                    error = null,
                    detailLoadingIds = it.detailLoadingIds + reportObjectId
                )
            }
            runCatching {
                AppGraph.shared.fetchReportDetailUseCase.execute(reportObjectId)
            }.onSuccess { detail ->
                _state.update { current ->
                    val mergedReports = (listOf(detail) + current.reports)
                        .distinctBy { report -> report.reportKey() }
                    current.copy(
                        loading = false,
                        reports = mergedReports,
                        reportDetails = current.reportDetails + (reportObjectId to detail),
                        detailLoadingIds = current.detailLoadingIds - reportObjectId,
                        nextSkip = mergedReports.size
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        detailLoadingIds = it.detailLoadingIds - reportObjectId,
                        error = error.message ?: "Unable to load report"
                    )
                }
            }
        }
    }

    fun deleteReport(report: ReportSummary) {
        val reportKey = report.reportKey()
        if (!report.canDelete || reportKey in _state.value.deletingReportKeys) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    deletingReportKeys = it.deletingReportKeys + reportKey,
                    error = null
                )
            }
            runCatching {
                AppGraph.shared.deleteReportUseCase.execute(report)
            }.onSuccess {
                _state.update {
                    it.copy(
                        reports = it.reports.filterNot { candidate -> candidate.reportKey() == reportKey },
                        reportDetails = it.reportDetails - report.objectId,
                        detailLoadingIds = it.detailLoadingIds - report.objectId,
                        deletingReportKeys = it.deletingReportKeys - reportKey
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        deletingReportKeys = it.deletingReportKeys - reportKey,
                        error = error.message ?: "Unable to delete report"
                    )
                }
            }
        }
    }
}
