package com.reported.nativeandroid.screens

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val ReportedFieldShape = RoundedCornerShape(8.dp)
private val ReportedFieldMinHeight = 56.dp
private val ReportedFieldHorizontalPadding = 10.dp
private val ReportedFieldBorderColor = Color(0xFF6F6877)
private val ReportedFieldBorderColorDisabled = Color(0xFFBBB4C2)
private val ReportedFieldErrorBackground = Color(0xFFFFF1F1)

@Composable
private fun ReportedFieldShell(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    minHeight: Dp = ReportedFieldMinHeight,
    onClick: (() -> Unit)? = null,
    trailingWidth: Dp = 40.dp,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    var labelWidthPx by remember(label) { mutableStateOf(0) }
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val strokeWidthPx = with(density) { 1.25.dp.toPx() }
    val notchInsetPx = with(density) { 6.dp.toPx() }
    val labelStartPx = with(density) { 12.dp.toPx() }
    val outlineColor = when {
        isError -> MaterialTheme.colorScheme.error
        enabled -> ReportedFieldBorderColor
        else -> ReportedFieldBorderColorDisabled
    }
    val fieldBackground = if (isError) {
        ReportedFieldErrorBackground
    } else {
        MaterialTheme.colorScheme.background
    }
    val clickableModifier = if (enabled && onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val left = strokeWidthPx / 2f
                    val top = strokeWidthPx / 2f
                    val right = size.width - strokeWidthPx / 2f
                    val bottom = size.height - strokeWidthPx / 2f
                    val radius = (cornerRadiusPx - strokeWidthPx / 2f)
                        .coerceAtMost((right - left) / 2f)
                        .coerceAtMost((bottom - top) / 2f)
                    val notchStart = (labelStartPx - notchInsetPx).coerceAtLeast(left + radius)
                    val notchEnd = (labelStartPx + labelWidthPx + notchInsetPx).coerceAtMost(right - radius)

                    val outline = Path().apply {
                        moveTo(notchStart, top)
                        lineTo(left + radius, top)
                        quadraticTo(left, top, left, top + radius)
                        lineTo(left, bottom - radius)
                        quadraticTo(left, bottom, left + radius, bottom)
                        lineTo(right - radius, bottom)
                        quadraticTo(right, bottom, right, bottom - radius)
                        lineTo(right, top + radius)
                        quadraticTo(right, top, right - radius, top)
                        lineTo(notchEnd, top)
                    }
                    drawPath(
                        path = outline,
                        color = outlineColor,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
                .then(clickableModifier),
            shape = ReportedFieldShape,
            color = fieldBackground
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minHeight)
                    .padding(horizontal = ReportedFieldHorizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content(Modifier.weight(1f))
                if (trailing != null) {
                    Box(
                        modifier = Modifier.width(trailingWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        trailing()
                    }
                }
            }
        }
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
                .offset(y = (-8).dp)
                .onSizeChanged { labelWidthPx = it.width },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isError -> MaterialTheme.colorScheme.error
                enabled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun ScreenSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

@Composable
fun ReportedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClear: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    trailingWidth: Dp = 40.dp,
    autoFitText: Boolean = false,
    minHeight: Dp = ReportedFieldMinHeight
) {
    ReportedTextInputField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        onClear = onClear,
        trailingContent = trailingContent,
        trailingWidth = trailingWidth,
        autoFitText = autoFitText,
        minHeight = minHeight
    )
}

@Composable
fun ReportedPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    minHeight: Dp = ReportedFieldMinHeight
) {
    var passwordVisible by remember { mutableStateOf(false) }
    ReportedTextInputField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        minHeight = minHeight,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingWidth = 48.dp,
        trailingContent = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
            }
        }
    )
}

@Composable
private fun ReportedTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClear: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    trailingWidth: Dp = 40.dp,
    autoFitText: Boolean = false,
    minHeight: Dp = ReportedFieldMinHeight,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val inputTextColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var textSize by remember(value) { mutableStateOf(bodyStyle.fontSize) }
    ReportedFieldShell(
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        minHeight = minHeight,
        trailingWidth = trailingWidth,
        trailing = if ((onClear != null && value.isNotEmpty()) || trailingContent != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent?.invoke()
                    if (onClear != null && value.isNotEmpty()) {
                        IconButton(onClick = onClear, enabled = enabled) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear $label")
                        }
                    }
                }
            }
        } else {
            null
        }
    ) { fieldModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = fieldModifier,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = textSize,
                color = inputTextColor
            ),
            cursorBrush = SolidColor(inputTextColor),
            onTextLayout = { layoutResult ->
                if (autoFitText && layoutResult.hasVisualOverflow && textSize.value > 14f) {
                    textSize = (textSize.value - 1f).coerceAtLeast(14f).sp
                }
            },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun MessageCard(message: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun LoginRequiredScreen(
    title: String,
    message: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title.isNotBlank()) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        }
        MessageCard(message)
        PrimaryButton(text = "Login", onClick = onLogin)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    textSize: TextUnit = TextUnit.Unspecified
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = textSize)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        Text(text, color = textColor)
    }
}

private val preferredPlateRegions = listOf("NY", "NJ", "CT", "PA", "FL", "OTHER")
private val allPlateRegions = listOf(
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"
)

@Composable
private fun PickerStyleField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isError: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    trailing: @Composable (() -> Unit)? = null,
    autoFitText: Boolean = false
) {
    ReportedFieldShell(
        label = label,
        modifier = modifier,
        isError = isError,
        onClick = onClick,
        trailing = trailing
    ) { fieldModifier ->
        Box(
            modifier = fieldModifier,
            contentAlignment = Alignment.CenterStart
        ) {
            AutoFitSingleLineText(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
                enabled = autoFitText
            )
        }
    }
}

@Composable
private fun AutoFitSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    textAlign: TextAlign,
    enabled: Boolean
) {
    var textSize by remember(text) { mutableStateOf(style.fontSize) }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = textSize),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        onTextLayout = { layoutResult ->
            if (enabled && layoutResult.hasVisualOverflow && textSize.value > 14f) {
                textSize = (textSize.value - 1f).coerceAtLeast(14f).sp
            }
        }
    )
}

@Composable
fun ReportedSelectionField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isError: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    trailing: @Composable (() -> Unit)? = null
) {
    PickerStyleField(
        label = label,
        value = value,
        modifier = modifier,
        onClick = onClick,
        isError = isError,
        textAlign = textAlign,
        trailing = trailing,
        autoFitText = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateRegionPickerField(
    label: String,
    value: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    var showAllRegions by remember { mutableStateOf(false) }

    if (showPicker) {
        val options = if (showAllRegions) allPlateRegions else preferredPlateRegions
        AlertDialog(
            onDismissRequest = {
                showPicker = false
                showAllRegions = false
            },
            title = {
                Text(if (showAllRegions) "All states" else "Choose state")
            },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        FilterChip(
                            selected = value == option,
                            onClick = {
                                if (!showAllRegions && option == "OTHER") {
                                    showAllRegions = true
                                } else {
                                    showPicker = false
                                    showAllRegions = false
                                    onSelected(option)
                                }
                            },
                            label = { Text(option) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                        showAllRegions = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    PickerStyleField(
        label = label,
        value = value.ifBlank { "Select" },
        modifier = modifier.fillMaxWidth(),
        onClick = {
            showAllRegions = false
            showPicker = true
        },
        isError = isError,
        textAlign = TextAlign.Center
    )

}

@Composable
fun OccurredAtField(
    label: String,
    isoValue: String,
    photoIsoValue: String? = null,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now = remember { LocalDateTime.now(zoneId) }
    var showPicker by remember { mutableStateOf(false) }

    fun parseIsoOrNow(value: String): LocalDateTime {
        if (value.isBlank()) return LocalDateTime.now(zoneId)
        return try {
            OffsetDateTime.parse(value).atZoneSameInstant(zoneId).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value).atZone(zoneId).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                LocalDateTime.now(zoneId)
            }
        }
    }

    fun formatDisplay(value: String): String {
        if (value.isBlank()) return "Select date and time"
        return parseIsoOrNow(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
    }

    fun toIso(localDateTime: LocalDateTime): String =
        localDateTime.atZone(zoneId).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    if (showPicker) {
        val initial = parseIsoOrNow(isoValue).let { if (it.isAfter(now)) now else it }
        val yearStart = minOf(initial.year, now.year) - 2
        val yearEnd = now.year
        val years = remember(initial, now) { (yearStart..yearEnd).toList() }
        val monthLabels = remember {
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        }
        var selectedYear by remember(initial, now) { mutableStateOf(initial.year.coerceIn(yearStart, yearEnd)) }
        var selectedMonthIndex by remember(initial) { mutableStateOf(initial.monthValue - 1) }
        var selectedDay by remember(initial) { mutableStateOf(initial.dayOfMonth) }
        var selectedHour12 by remember(initial) {
            mutableStateOf(
                ((initial.hour + 11) % 12) + 1
            )
        }
        var selectedMinute by remember(initial) { mutableStateOf(initial.minute) }
        var selectedMeridiemIndex by remember(initial) { mutableStateOf(if (initial.hour >= 12) 1 else 0) }
        val maxDay = remember(selectedYear, selectedMonthIndex) {
            YearMonth.of(selectedYear, selectedMonthIndex + 1).lengthOfMonth()
        }
        if (selectedDay > maxDay) {
            selectedDay = maxDay
        }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    photoIsoValue
                        ?.takeIf { it.isNotBlank() }
                        ?.let { photoIso ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onValueSelected(photoIso)
                                        showPicker = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Use photo time of ${formatDisplay(photoIso)}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WheelSpinner(
                            values = monthLabels,
                            selectedIndex = selectedMonthIndex,
                            onSelectedIndexChange = { selectedMonthIndex = it },
                            modifier = Modifier.weight(1.2f)
                        )
                        WheelSpinner(
                            values = (1..maxDay).map(Int::toString),
                            selectedIndex = (selectedDay - 1).coerceIn(0, maxDay - 1),
                            onSelectedIndexChange = { selectedDay = it + 1 },
                            modifier = Modifier.weight(0.9f)
                        )
                        WheelSpinner(
                            values = years.map(Int::toString),
                            selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                            onSelectedIndexChange = { selectedYear = years[it] },
                            modifier = Modifier.weight(1.1f)
                        )
                        WheelSpinner(
                            values = (1..12).map(Int::toString),
                            selectedIndex = (selectedHour12 - 1).coerceIn(0, 11),
                            onSelectedIndexChange = { selectedHour12 = it + 1 },
                            modifier = Modifier.weight(0.8f)
                        )
                        WheelSpinner(
                            values = (0..59).map { it.toString().padStart(2, '0') },
                            selectedIndex = selectedMinute,
                            onSelectedIndexChange = { selectedMinute = it },
                            modifier = Modifier.weight(0.8f)
                        )
                        WheelSpinner(
                            values = listOf("AM", "PM"),
                            selectedIndex = selectedMeridiemIndex.coerceIn(0, 1),
                            onSelectedIndexChange = { selectedMeridiemIndex = it },
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedHour = when {
                            selectedMeridiemIndex == 0 && selectedHour12 == 12 -> 0
                            selectedMeridiemIndex == 1 && selectedHour12 != 12 -> selectedHour12 + 12
                            else -> selectedHour12
                        }
                        var selected = LocalDateTime.of(
                            selectedYear,
                            selectedMonthIndex + 1,
                            selectedDay,
                            selectedHour,
                            selectedMinute
                        )
                        if (selected.isAfter(now)) {
                            selected = now
                        }
                        onValueSelected(toIso(selected))
                        showPicker = false
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    PickerStyleField(
        label = label,
        value = formatDisplay(isoValue),
        modifier = modifier,
        onClick = { showPicker = true },
        isError = isError,
        autoFitText = true,
        trailing = {
            if (onClear != null && isoValue.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear $label")
                }
            } else {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = "Choose $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun WheelSpinner(
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.height(140.dp),
        factory = { context ->
            NumberPicker(context).apply {
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                wrapSelectorWheel = false
                minValue = 0
                maxValue = maxOf(values.lastIndex, 0)
                displayedValues = values.toTypedArray()
                value = selectedIndex.coerceIn(0, maxOf(values.lastIndex, 0))
                setOnValueChangedListener { _, _, newVal ->
                    onSelectedIndexChange(newVal)
                }
            }
        },
        update = { picker ->
            val safeIndex = selectedIndex.coerceIn(0, maxOf(values.lastIndex, 0))
            if (picker.maxValue != maxOf(values.lastIndex, 0)) {
                picker.displayedValues = null
                picker.minValue = 0
                picker.maxValue = maxOf(values.lastIndex, 0)
                picker.displayedValues = values.toTypedArray()
            } else {
                picker.displayedValues = null
                picker.displayedValues = values.toTypedArray()
            }
            if (picker.value != safeIndex) {
                picker.value = safeIndex
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplaintChipGroup(
    selectedIds: List<String>,
    options: List<Pair<String, String>>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = selectedIds.contains(id),
                onClick = { onToggle(id) },
                label = { Text(label) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            )
        }
    }
}
