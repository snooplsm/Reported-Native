package com.reported.nativeandroid.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reported.nativeandroid.R
import com.reported.nativeandroid.screens.PrimaryButton
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewReportTutorialSheet(
    scannerAvailable: Boolean,
    scannerEnabled: Boolean,
    onScannerEnabledChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onRequestMediaLocationPermission: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    val pageCount = if (scannerAvailable) 4 else 3
    val lastPage = pageCount - 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    fun advance() {
        when (pagerState.currentPage) {
            1 -> onRequestMediaLocationPermission()
        }
        if (pagerState.currentPage < lastPage) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else {
            onComplete()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 270.dp, max = 360.dp)
            ) { page ->
                when (page) {
                    0 -> TutorialPage(
                        title = stringResource(R.string.tutorial_report_faster_title),
                        body = stringResource(R.string.tutorial_report_faster_body),
                        icon = "1"
                    )
                    1 -> TutorialPage(
                        title = stringResource(R.string.tutorial_metadata_title),
                        body = stringResource(R.string.tutorial_metadata_body),
                        icon = "2"
                    )
                    2 -> TutorialPage(
                        title = stringResource(R.string.tutorial_ai_title),
                        body = stringResource(R.string.tutorial_ai_body),
                        icon = "3"
                    )
                    else -> TutorialScannerPage(
                        scannerAvailable = scannerAvailable
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (index == pagerState.currentPage) 18.dp else 8.dp, height = 8.dp),
                        shape = CircleShape,
                        color = if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        content = {}
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_skip))
                }
                PrimaryButton(
                    text = if (pagerState.currentPage == lastPage) {
                        stringResource(R.string.action_done)
                    } else {
                        stringResource(R.string.action_continue)
                    },
                    onClick = ::advance,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TutorialPage(
    title: String,
    body: String,
    icon: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = icon,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialScannerPage(
    scannerAvailable: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(18.dp)
                    .size(38.dp)
            )
        }
        Text(stringResource(R.string.tutorial_private_media_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            if (scannerAvailable) {
                stringResource(R.string.tutorial_scanner_available_body)
            } else {
                stringResource(R.string.tutorial_picker_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
