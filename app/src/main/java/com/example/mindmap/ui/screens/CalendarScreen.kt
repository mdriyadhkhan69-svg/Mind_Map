package com.example.mindmap.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.mindmap.data.CalendarAlarmScheduler
import com.example.mindmap.data.CalendarEventEntity
import com.example.mindmap.ui.theme.SoftNeutral
import com.example.mindmap.ui.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/* ---------------- style + settings persistence ---------------- */

private data class CalendarStyle(
    val backgroundArgb: Long? = null,
    val cardArgb: Long? = null,
    val textArgb: Long? = null,
    val accentArgb: Long? = null
)

private fun loadCalendarStyle(context: Context): CalendarStyle {
    val prefs = context.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE)
    fun color(key: String): Long? = prefs.getLong(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
    return CalendarStyle(
        backgroundArgb = color("background_argb"),
        cardArgb = color("card_argb"),
        textArgb = color("text_argb"),
        accentArgb = color("accent_argb")
    )
}

private fun saveCalendarStyle(context: Context, style: CalendarStyle) {
    val editor = context.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE).edit()
    fun put(key: String, value: Long?) { if (value == null) editor.remove(key) else editor.putLong(key, value) }
    put("background_argb", style.backgroundArgb)
    put("card_argb", style.cardArgb)
    put("text_argb", style.textArgb)
    put("accent_argb", style.accentArgb)
    editor.apply()
}

/* ---------------- date math ---------------- */

private const val CAL_BASE_YEAR = 2000
private const val CAL_TOTAL_MONTHS = 3600 // 2000..2299 পর্যন্ত কভার করে, dynamic paging

private fun monthIndexForToday(): Int {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.YEAR) - CAL_BASE_YEAR) * 12 + cal.get(Calendar.MONTH)
}

private fun yearMonthFromIndex(index: Int): Pair<Int, Int> {
    val year = CAL_BASE_YEAR + index / 12
    val month = index % 12
    return year to month
}

private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun dateKeyOf(year: Int, month: Int, day: Int): String = "%04d-%02d-%02d".format(year, month + 1, day)

private fun todayDateKey(): String {
    val cal = Calendar.getInstance()
    return dateKeyOf(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
}

private data class CalendarDayCell(val day: Int, val dateKey: String)

private fun buildMonthDays(year: Int, month: Int): List<CalendarDayCell?> {
    val cal = Calendar.getInstance()
    cal.set(year, month, 1)
    val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<CalendarDayCell?>()
    repeat(firstWeekday) { cells += null }
    for (day in 1..daysInMonth) cells += CalendarDayCell(day, dateKeyOf(year, month, day))
    while (cells.size % 7 != 0) cells += null
    return cells
}

/* ---------------- permission helpers ---------------- */

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
}

/* ---------------- main entry ---------------- */

@Composable
fun CalendarHomeDialog(
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit,
    onNavigateToMindMap: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToTimer: () -> Unit
) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(loadCalendarStyle(context)) }
    val background = Color(style.backgroundArgb ?: 0xFF0F1020)
    val cardColor = Color(style.cardArgb ?: 0xFF1E1E2E)
    val textColor = Color(style.textArgb ?: 0xFFFFFFFF)
    val accent = Color(style.accentArgb ?: 0xFFEDE6DA)

    val allEvents by viewModel.allEvents.collectAsState()
    val eventsByDate = remember(allEvents) { allEvents.associateBy { it.dateKey } }

    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("calendar") }
    var actionForDate by remember { mutableStateOf<String?>(null) }
    var selectedDateKey by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission(context)) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun persistEvent(event: CalendarEventEntity) {
        viewModel.save(event) { saved ->
            CalendarAlarmScheduler.cancel(context, saved)
            if (!saved.isCompleted && (saved.text.isNotBlank() || saved.hasTimer)) {
                CalendarAlarmScheduler.schedule(context, saved)
            }
        }
    }

    var calendarResetSignal by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = background, modifier = Modifier.fillMaxSize(), contentColor = textColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = accent) }
                    TextButton(onClick = { activeTab = "upcoming" }) {
                        Text("Upcoming", color = if (activeTab == "upcoming") accent else textColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (activeTab == "calendar") calendarResetSignal++
                        activeTab = "calendar"
                    }) {
                        Text("Calendar", color = if (activeTab == "calendar") accent else textColor, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Mind map") }, onClick = { showMenu = false; onNavigateToMindMap() })
                            DropdownMenuItem(text = { Text("Files") }, onClick = { showMenu = false; onNavigateToFiles() })
                            DropdownMenuItem(text = { Text("Timer") }, onClick = { showMenu = false; onNavigateToTimer() })
                            DropdownMenuItem(text = { Text("Calendar Settings") }, onClick = { showMenu = false; showSettings = true })
                        }
                    }
                }

                if (activeTab == "calendar") {
                    CalendarMonthPager(
                        eventsByDate = eventsByDate,
                        cardColor = cardColor,
                        textColor = textColor,
                        accent = accent,
                        resetSignal = calendarResetSignal,
                        selectedDateKey = selectedDateKey,
                        onTapDate = { dateKey -> selectedDateKey = dateKey },
                        onTripleTapDate = { dateKey -> actionForDate = dateKey }
                    )
                } else {
                    CalendarUpcomingList(
                        events = allEvents,
                        cardColor = cardColor,
                        textColor = textColor,
                        accent = accent,
                        onOpen = { event -> actionForDate = event.dateKey }
                    )
                }
            }
        }
    }

    actionForDate?.let { dateKey ->
        val existing = eventsByDate[dateKey]
        CalendarDateOptionsDialog(
            dateKey = dateKey,
            existing = existing,
            onDismiss = { actionForDate = null },
            onSaveOccasion = { newText ->
                val base = existing ?: CalendarEventEntity(dateKey = dateKey)
                persistEvent(base.copy(text = newText))
            },
            onSaveTimer = { hour, minute ->
                val base = existing ?: CalendarEventEntity(dateKey = dateKey)
                persistEvent(base.copy(hasTimer = true, timerHour = hour, timerMinute = minute))
            },
            onToggleComplete = {
                val base = existing ?: CalendarEventEntity(dateKey = dateKey)
                persistEvent(base.copy(isCompleted = !base.isCompleted))
                actionForDate = null
            },
            onDelete = existing?.let { ev ->
                {
                    CalendarAlarmScheduler.cancel(context, ev)
                    viewModel.delete(ev)
                    actionForDate = null
                }
            }
        )
    }
    if (showSettings) {
        CalendarSettingsDialog(
            style = style,
            onDismiss = { showSettings = false },
            onStyleChange = { updated -> style = updated; saveCalendarStyle(context, updated) }
        )
    }
}

/* ---------------- month grid ---------------- */

@Composable
private fun CalendarMonthPager(
    eventsByDate: Map<String, CalendarEventEntity>,
    cardColor: Color,
    textColor: Color,
    accent: Color,
    resetSignal: Int,
    selectedDateKey: String?,
    onTapDate: (String) -> Unit,
    onTripleTapDate: (String) -> Unit
) {
    val initialPage = remember { monthIndexForToday() }
    val pagerState = rememberPagerState(initialPage = initialPage) { CAL_TOTAL_MONTHS }
    val today = remember { todayDateKey() }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) pagerState.animateScrollToPage(initialPage)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val (year, month) = yearMonthFromIndex(pagerState.currentPage)
        Text(
            "${MonthNames[month]} $year",
            color = textColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Text(
                    label, color = textColor.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val (pageYear, pageMonth) = yearMonthFromIndex(page)
            val days = remember(page) { buildMonthDays(pageYear, pageMonth) }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(days.chunked(7)) { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { cell ->
                            Box(modifier = Modifier.weight(1f).padding(3.dp)) {
                                if (cell != null) {
                                    CalendarDateCell(
                                        day = cell.day,
                                        isToday = cell.dateKey == today,
                                        isSelected = cell.dateKey == selectedDateKey,
                                        event = eventsByDate[cell.dateKey],
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        accent = accent,
                                        onTap = { onTapDate(cell.dateKey) },
                                        onTripleTap = { onTripleTapDate(cell.dateKey) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDateCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    event: CalendarEventEntity?,
    cardColor: Color,
    textColor: Color,
    accent: Color,
    onTap: () -> Unit,
    onTripleTap: () -> Unit
) {
    val hasContent = event != null && (event.text.isNotBlank() || event.hasTimer)
    val selectionBlue = Color(0xFF3B82F6)
    val tapScope = rememberCoroutineScope()
    var tapCount by remember { mutableStateOf(0) }
    var tapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor.copy(alpha = if (isToday) 1f else 0.7f))
            .border(
                width = if (isToday || isSelected) 2.dp else 1.dp,
                color = if (isToday) accent else if (isSelected) selectionBlue else textColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(day) {
                detectTapGestures(
                    onTap = {
                        tapCount += 1
                        tapJob?.cancel()
                        tapJob = tapScope.launch {
                            delay(220)
                            if (tapCount >= 2) onTripleTap() else onTap()
                            tapCount = 0
                        }
                    }
                )
            }
            .padding(6.dp)
    ) {
        Column {
            Text(
                day.toString(),
                color = if (isToday) accent else textColor,
                fontSize = 16.sp,
                fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold
            )
            if (hasContent && event != null) {
                if (event.text.isNotBlank()) {
                    Text(event.text, color = textColor.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (event.hasTimer) {
                    Text("%02d:%02d".format(event.timerHour, event.timerMinute), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (event?.isCompleted == true) {
            Text("✕", color = Color(0xFFFF5252), fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/* ---------------- upcoming ---------------- */

@Composable
private fun CalendarUpcomingList(
    events: List<CalendarEventEntity>,
    cardColor: Color,
    textColor: Color,
    accent: Color,
    onOpen: (CalendarEventEntity) -> Unit
) {
    val today = remember { todayDateKey() }
    val upcoming = remember(events) {
        events.filter { !it.isCompleted && (it.text.isNotBlank() || it.hasTimer) && it.dateKey >= today }
            .sortedWith(compareBy({ it.dateKey }, { if (it.hasTimer) it.timerHour * 60 + it.timerMinute else 0 }))
    }
    if (upcoming.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো upcoming event নেই", color = textColor.copy(alpha = 0.6f))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        items(upcoming, key = { it.id }) { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardColor)
                    .clickable { onOpen(event) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.dateKey, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (event.text.isNotBlank()) {
                        Text(event.text, color = textColor, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (event.hasTimer) {
                    Text("%02d:%02d".format(event.timerHour, event.timerMinute), color = textColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ---------------- dialogs ---------------- */

@Composable
private fun CalendarDateOptionsDialog(
    dateKey: String,
    existing: CalendarEventEntity?,
    onDismiss: () -> Unit,
    onSaveOccasion: (String) -> Unit,
    onSaveTimer: (Int, Int) -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var panelMode by remember(dateKey) { mutableStateOf("options") }
    var occasionText by remember(dateKey) { mutableStateOf(existing?.text.orEmpty()) }
    val existingHour24 = existing?.timerHour?.takeIf { it >= 0 } ?: 9
    var timerHourText by remember(dateKey) { mutableStateOf((if (existingHour24 % 12 == 0) 12 else existingHour24 % 12).toString()) }
    var timerMinuteText by remember(dateKey) { mutableStateOf((existing?.timerMinute?.takeIf { it >= 0 } ?: 0).toString()) }
    var timerIsPm by remember(dateKey) { mutableStateOf(existingHour24 >= 12) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput("calendar-panel-scrim") { detectTapGestures(onTap = { onDismiss() }) }
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp),
                enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 3 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 3 }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E2E),
                    contentColor = SoftNeutral,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 260.dp)
                        .pointerInput("calendar-panel-block") { detectTapGestures(onTap = {}) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dateKey, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SoftNeutral)
                        Spacer(Modifier.height(8.dp))
                        when (panelMode) {
                            "occasion" -> {
                                OutlinedTextField(
                                    value = occasionText,
                                    onValueChange = { occasionText = it },
                                    label = { Text("Occasion") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { panelMode = "options" }) { Text("Cancel", color = Color.LightGray) }
                                    TextButton(onClick = {
                                        onSaveOccasion(occasionText)
                                        panelMode = "options"
                                    }) { Text("Done", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold) }
                                }
                            }
                            "timer" -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = timerHourText,
                                        onValueChange = { timerHourText = it.filter(Char::isDigit).take(2) },
                                        label = { Text("Hour") },
                                        singleLine = true,
                                        modifier = Modifier.width(64.dp)
                                    )
                                    OutlinedTextField(
                                        value = timerMinuteText,
                                        onValueChange = { timerMinuteText = it.filter(Char::isDigit).take(2) },
                                        label = { Text("Min") },
                                        singleLine = true,
                                        modifier = Modifier.width(64.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .pointerInput("calendar-ampm-toggle") {
                                                detectTapGestures(onTap = { timerIsPm = !timerIsPm })
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Text(if (timerIsPm) "PM" else "AM", color = SoftNeutral, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { panelMode = "options" }) { Text("Cancel", color = Color.LightGray) }
                                    TextButton(onClick = {
                                        val hour12 = (timerHourText.toIntOrNull() ?: 12).coerceIn(1, 12)
                                        val minute = (timerMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                                        val hour24 = when {
                                            timerIsPm && hour12 != 12 -> hour12 + 12
                                            !timerIsPm && hour12 == 12 -> 0
                                            else -> hour12
                                        }
                                        onSaveTimer(hour24, minute)
                                        panelMode = "options"
                                    }) { Text("Done", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold) }
                                }
                            }
                            else -> {
                                if (existing?.text?.isNotBlank() == true) {
                                    Text(existing.text, color = SoftNeutral, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                                } else {
                                    TextButton(onClick = { panelMode = "occasion" }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Add Occasion +", color = SoftNeutral)
                                    }
                                }
                                if (existing?.hasTimer == true) {
                                    Text(
                                        "%02d:%02d".format(existing.timerHour, existing.timerMinute),
                                        color = Color(0xFF64FFDA),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                } else {
                                    TextButton(onClick = { panelMode = "timer" }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Add Timer +", color = SoftNeutral)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = onToggleComplete, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (existing?.isCompleted == true) "Undo Complete" else "Mark Complete", color = SoftNeutral)
                                }
                                if (onDelete != null) {
                                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = Color(0xFFFF6E6E)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTimerDialog(
    dateKey: String,
    initialHour: Int,
    initialMinute: Int,
    needsExactAlarmPermission: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
    onRemoveTimer: (() -> Unit)?
) {
    var hourText by remember { mutableStateOf(initialHour.toString()) }
    var minuteText by remember { mutableStateOf(initialMinute.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1E1E2E), contentColor = Color.White) {
            Column(modifier = Modifier.padding(18.dp).width(280.dp)) {
                Text("Set Timer - $dateKey", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                if (needsExactAlarmPermission) {
                    Text("Exact reminder-এর জন্য permission দরকার", color = Color(0xFFFFD166), fontSize = 12.sp)
                    TextButton(onClick = onRequestExactAlarmPermission) { Text("Allow", color = Color(0xFF64FFDA)) }
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0-23)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute (0-59)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (onRemoveTimer != null) {
                        TextButton(onClick = onRemoveTimer) { Text("Remove", color = Color(0xFFFF6E6E)) }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                    TextButton(onClick = {
                        val h = (hourText.toIntOrNull() ?: 9).coerceIn(0, 23)
                        val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                        onSave(h, m)
                    }) { Text("Save", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CalendarSettingsDialog(
    style: CalendarStyle,
    onDismiss: () -> Unit,
    onStyleChange: (CalendarStyle) -> Unit
) {
    var picker by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF1E1E2E), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Calendar Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { onStyleChange(CalendarStyle()) }) { Text("Reset to default", color = SoftNeutral) }
                Spacer(Modifier.height(10.dp))
                CalendarColorRow("Background", Color(style.backgroundArgb ?: 0xFF0F1020)) { picker = "background" }
                CalendarColorRow("Card color", Color(style.cardArgb ?: 0xFF1E1E2E)) { picker = "card" }
                CalendarColorRow("Text color", Color(style.textArgb ?: 0xFFFFFFFF)) { picker = "text" }
                CalendarColorRow("Accent color", Color(style.accentArgb ?: 0xFFEDE6DA)) { picker = "accent" }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold) }
            }
        }
    }

    val config = when (picker) {
        "background" -> "Background color" to (style.backgroundArgb ?: 0xFF0F1020)
        "card" -> "Card color" to (style.cardArgb ?: 0xFF1E1E2E)
        "text" -> "Text color" to (style.textArgb ?: 0xFFFFFFFF)
        "accent" -> "Accent color" to (style.accentArgb ?: 0xFFEDE6DA)
        else -> null
    }
    config?.let { (title, initial) ->
        ColorPickerDialog(
            title = title,
            initialColorArgb = initial,
            onDismiss = { picker = null },
            onSelect = { color ->
                onStyleChange(
                    when (picker) {
                        "background" -> style.copy(backgroundArgb = color)
                        "card" -> style.copy(cardArgb = color)
                        "text" -> style.copy(textArgb = color)
                        "accent" -> style.copy(accentArgb = color)
                        else -> style
                    }
                )
                picker = null
            },
            allowReset = false,
            onReset = {}
        )
    }
}

@Composable
private fun CalendarColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable { onClick() }
        )
    }
}