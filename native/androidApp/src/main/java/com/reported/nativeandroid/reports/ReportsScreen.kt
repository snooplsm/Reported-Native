package com.reported.nativeandroid.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.reported.nativeandroid.app.ReportsMode
import com.reported.nativeandroid.app.ReportsAction
import com.reported.nativeandroid.app.ReportsViewModel
import com.reported.nativeandroid.screens.LoginRequiredScreen
import com.reported.nativeandroid.screens.MessageCard
import com.reported.nativeandroid.screens.OccurredAtField
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.ReportedField
import com.reported.nativeandroid.screens.ScreenSection
import com.reported.nativeandroid.screens.SecondaryButton
import com.reported.shared.model.ReportSummary
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    isAuthorized: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMenu: () -> Unit,
    openReportObjectId: String? = null,
    onOpenedReport: () -> Unit = {},
    vm: ReportsViewModel = viewModel()
) {
    if (!isAuthorized) {
        LoginRequiredScreen(
            title = "",
            message = "Sign in to see your history and keep track of the reports you've submitted.",
            onLogin = onRequireLogin
        )
        return
    }

    val state by vm.state.collectAsState()
    var expandedReports by remember { mutableStateOf(setOf<String>()) }
    var pendingDeleteReport by remember { mutableStateOf<ReportSummary?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(openReportObjectId) {
        val objectId = openReportObjectId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        expandedReports = expandedReports + objectId
        vm.onAction(ReportsAction.ReportOpened(objectId))
        onOpenedReport()
    }

    LaunchedEffect(listState, state.reports.size, state.hasMore, state.loadingMore, state.loading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleIndex >= totalItems - 4) {
                    vm.onAction(ReportsAction.NextPageRequested)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        pendingDeleteReport?.let { report ->
            AlertDialog(
                onDismissRequest = { pendingDeleteReport = null },
                title = { Text("Delete report?") },
                text = {
                    Text("This pending report for ${listOf(report.plateRegion, report.plate).filter { it.isNotBlank() }.joinToString(" ")} will be removed.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.onAction(ReportsAction.ReportDeleted(report))
                            pendingDeleteReport = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteReport = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        CenterAlignedTopAppBar(
            title = { Text("My Reports") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    com.reported.nativeandroid.app.ShellMenuIcon()
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenSection {
                    Text(
                        "Choose how you want to pull your reports. We will not load the list until you ask.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SecondaryButton(
                            "Search",
                            onClick = { vm.onAction(ReportsAction.SearchChosen) },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            "List",
                            onClick = { vm.onAction(ReportsAction.ListChosen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.mode == ReportsMode.SEARCH) {
                item {
                    ScreenSection(title = "Search Filters") {
                        ReportedField(
                            label = "License plate",
                            value = state.licenseQuery,
                            onValueChange = { vm.onAction(ReportsAction.SearchFieldsChanged(license = it)) }
                        )
                        OccurredAtField(
                            label = "Start Date",
                            isoValue = state.startDate,
                            onValueSelected = { vm.onAction(ReportsAction.SearchFieldsChanged(startDate = it)) },
                            onClear = { vm.onAction(ReportsAction.SearchFieldsChanged(startDate = "")) }
                        )
                        OccurredAtField(
                            label = "End Date",
                            isoValue = state.endDate,
                            onValueSelected = { vm.onAction(ReportsAction.SearchFieldsChanged(endDate = it)) },
                            onClear = { vm.onAction(ReportsAction.SearchFieldsChanged(endDate = "")) }
                        )
                        PrimaryButton(
                            "Search reports",
                            onClick = { vm.onAction(ReportsAction.SearchPressed) },
                            enabled = !state.loading
                        )
                    }
                }
            }

            if (state.error != null) {
                item {
                    ScreenSection(title = "Error") {
                        MessageCard(state.error ?: "Unknown error")
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.mode != null) {
                item {
                    ScreenSection(title = "Results") {
                        Text(
                            if (state.reports.isEmpty()) "No reports found." else "${state.reports.size} reports",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            items(state.reports, key = { it.objectId.ifBlank { it.id.toString() } }) { report ->
                val reportKey = report.objectId.ifBlank { report.id.toString() }
                val expanded = reportKey in expandedReports
                val detail = state.reportDetails[report.objectId] ?: report
                OutlinedCard(
                    modifier = Modifier.clickable {
                            expandedReports = if (expanded) {
                            expandedReports - reportKey
                        } else {
                                if (report.objectId.isNotBlank()) vm.onAction(ReportsAction.DetailRequested(report.objectId))
                                expandedReports + reportKey
                            }
                        },
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    report.street.ifBlank { "Unknown address" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    listOf(report.plate, report.plateRegion).filter { it.isNotBlank() }.joinToString(" - ")
                                        .ifBlank { "No plate captured" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                report.status.ifBlank { "Pending" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        val incidentAtDisplay = remember(report.incidentAt) { report.incidentAt.toReportDateTimeDisplay() }
                        if (incidentAtDisplay.isNotBlank()) {
                            Text(incidentAtDisplay, style = MaterialTheme.typography.bodySmall)
                        }
                        if (report.complaint.isNotBlank()) {
                            Text(report.complaint, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (expanded) {
                            if (report.objectId in state.detailLoadingIds) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (detail.description.isNotBlank()) {
                                Text(detail.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (detail.notes.isNotBlank()) {
                                Text(
                                    "Notes: ${detail.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val mediaUrls = detail.mediaUrls
                            val videoUrls = detail.videoUrls
                            if (mediaUrls.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(mediaUrls, key = { it }) { url ->
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Report photo",
                                            modifier = Modifier
                                                .size(112.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            if (videoUrls.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    videoUrls.forEachIndexed { index, _ ->
                                        Text(
                                            "Video ${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (mediaUrls.isEmpty() && videoUrls.isEmpty() && report.objectId !in state.detailLoadingIds) {
                                Text(
                                    "No media attached.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (report.canDelete) {
                                val isDeleting = reportKey in state.deletingReportKeys
                                TextButton(
                                    onClick = { pendingDeleteReport = report },
                                    enabled = !isDeleting
                                ) {
                                    Text(if (isDeleting) "Deleting..." else "Delete report")
                                }
                            }
                        }
                    }
                }
            }
            if (state.loadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private fun String.toReportDateTimeDisplay(): String {
    if (isBlank()) return ""
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    return try {
        OffsetDateTime.parse(this).atZoneSameInstant(zoneId).format(formatter)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(this).atZone(zoneId).format(formatter)
        } catch (_: DateTimeParseException) {
            this
        }
    }
}
