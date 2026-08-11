package com.example.mindmap.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val TimerBg = Color(0xFF0B0B0F)
private val TimerCardBg = Color(0xFF15151A)
private val TimerDigit = Color(0xFFB7B7BF)
private val TimerAccent = Color(0xFF64FFDA)

/* ---------------- persistence (SharedPreferences + JSON, same pattern as pdf_library) ---------------- */

private data class StudySubject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accumulatedMillis: Long = 0L,
    val isRunning: Boolean = false,
    val startedAtMillis: Long = 0L
)

private fun loadIs24Hour(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("is_24_hour", false)

private fun saveIs24Hour(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("is_24_hour", value).apply()
}

private fun loadStudySubjects(context: Context): List<StudySubject> = runCatching {
    val raw = context.getSharedPreferences("study_subjects", Context.MODE_PRIVATE).getString("subjects", "[]") ?: "[]"
    val array = org.json.JSONArray(raw)
    buildList {
        repeat(array.length()) { i ->
            val o = array.getJSONObject(i)
            add(
                StudySubject(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name"),
                    accumulatedMillis = o.optLong("accumulatedMillis", 0L),
                    isRunning = o.optBoolean("isRunning", false),
                    startedAtMillis = o.optLong("startedAtMillis", 0L)
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveStudySubjects(context: Context, subjects: List<StudySubject>) {
    val array = org.json.JSONArray()
    subjects.forEach { s ->
        array.put(
            org.json.JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("accumulatedMillis", s.accumulatedMillis)
                .put("isRunning", s.isRunning)
                .put("startedAtMillis", s.startedAtMillis)
        )
    }
    context.getSharedPreferences("study_subjects", Context.MODE_PRIVATE).edit().putString("subjects", array.toString()).apply()
}

private fun StudySubject.currentElapsedMillis(nowMillis: Long): Long =
    accumulatedMillis + if (isRunning) (nowMillis - startedAtMillis).coerceAtLeast(0L) else 0L

private fun formatDurationDhm(totalMillis: Long): String {
    val totalMinutes = totalMillis / 60000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/* ---------------- flip-style building blocks (matches the reference screenshot) ---------------- */

@Composable
private fun FlipDigitCard(
    mainText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 92.sp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(TimerCardBg),
        contentAlignment = Alignment.Center
    ) {
        Text(mainText, color = TimerDigit, fontSize = fontSize, fontWeight = FontWeight.Black)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun FlipBlock(
    topLabel: String,
    mainText: String,
    cornerLabel: String,
    cornerAtStart: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(topLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            FlipDigitCard(mainText, modifier = Modifier.fillMaxSize())
            if (cornerLabel.isNotEmpty()) {
                Text(
                    cornerLabel,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(if (cornerAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(14.dp)
                )
            }
        }
    }
}

/* ---------------- live clock (real-time, top of Timer home) ---------------- */

@Composable
private fun LiveClockDisplay(is24Hour: Boolean) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val dateLabel = remember(now) { SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(now)) }
    val dayLabel = remember(now) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now)) }
    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val hourDisplay = if (is24Hour) hour24 else {
        val h = hour24 % 12
        if (h == 0) 12 else h
    }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val amPm = if (hour24 < 12) "AM" else "PM"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlipBlock(
            topLabel = dateLabel,
            mainText = "%02d".format(hourDisplay),
            cornerLabel = if (is24Hour) "" else amPm,
            cornerAtStart = true,
            modifier = Modifier.weight(1f)
        )
        FlipBlock(
            topLabel = dayLabel,
            mainText = "%02d".format(minute),
            cornerLabel = "%02d".format(second),
            cornerAtStart = false,
            modifier = Modifier.weight(1f)
        )
    }
}

/* ---------------- Timer home ---------------- */

@Composable
fun TimerHomeDialog(
    onDismiss: () -> Unit,
    onNavigateToMindMap: () -> Unit,
    onNavigateToFiles: () -> Unit
) {
    val context = LocalContext.current
    var is24Hour by remember { mutableStateOf(loadIs24Hour(context)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStudy by remember { mutableStateOf(false) }
    var showQuickTimer by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("timer-home-tap") {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth()) {
                    LiveClockDisplay(is24Hour = is24Hour)
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(160)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(150))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Timer menu", tint = Color.White)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Timer settings") }, onClick = { showMenu = false; showSettings = true })
                                DropdownMenuItem(text = { Text("Files") }, onClick = { showMenu = false; onNavigateToFiles() })
                                DropdownMenuItem(text = { Text("Mind map") }, onClick = { showMenu = false; onNavigateToMindMap() })
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
                    exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimerSideIcon(label = "Study") { showStudy = true }
                        TimerSideIcon(label = "New") { showQuickTimer = true }
                        TimerSideIcon(label = "Set") { showSettings = true }
                    }
                }
            }
        }
    }

    if (showSettings) {
        TimerSettingsDialog(
            is24Hour = is24Hour,
            onDismiss = { showSettings = false },
            onIs24HourChange = { value ->
                is24Hour = value
                saveIs24Hour(context, value)
            }
        )
    }

    if (showStudy) {
        StudyHomeDialog(onDismiss = { showStudy = false })
    }

    if (showQuickTimer) {
        QuickTimerDialog(onDismiss = { showQuickTimer = false })
    }
}

@Composable
private fun TimerSideIcon(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(label.take(1), color = TimerAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimerSettingsDialog(
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onIs24HourChange: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Timer settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("24-hour time", fontSize = 15.sp)
                        Text("Off shows 12-hour with AM/PM", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = is24Hour,
                        onCheckedChange = onIs24HourChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = TimerAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ---------------- Quick stopwatch / countdown ---------------- */

@Composable
private fun QuickTimerDialog(onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf("stopwatch") } // "stopwatch" | "countdown"
    var isRunning by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    var countdownTotalMillis by remember { mutableStateOf(5 * 60_000L) }
    var remainingMillis by remember { mutableStateOf(countdownTotalMillis) }
    var startTimestamp by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var minutesInput by remember { mutableStateOf("5") }

    LaunchedEffect(isRunning, mode) {
        if (isRunning) {
            startTimestamp = System.currentTimeMillis() - (if (mode == "stopwatch") elapsedMillis else countdownTotalMillis - remainingMillis)
            while (isRunning) {
                delay(200)
                val now = System.currentTimeMillis()
                if (mode == "stopwatch") {
                    elapsedMillis = now - startTimestamp
                } else {
                    val spent = now - startTimestamp
                    remainingMillis = (countdownTotalMillis - spent).coerceAtLeast(0L)
                    if (remainingMillis <= 0L) {
                        isRunning = false
                        finished = true
                    }
                }
            }
        }
    }

    fun formatClock(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds / 60) % 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Back", color = TimerAccent) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { if (!isRunning) { mode = "stopwatch"; finished = false; elapsedMillis = 0L } }) {
                        Text("Stopwatch", color = if (mode == "stopwatch") TimerAccent else Color.White.copy(alpha = 0.5f))
                    }
                    TextButton(onClick = { if (!isRunning) { mode = "countdown"; finished = false; remainingMillis = countdownTotalMillis } }) {
                        Text("Countdown", color = if (mode == "countdown") TimerAccent else Color.White.copy(alpha = 0.5f))
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (finished) {
                        Text("TIME UP", color = Color(0xFFFF6E6E), fontSize = 44.sp, fontWeight = FontWeight.Black)
                    } else if (mode == "stopwatch") {
                        FlipDigitCard(formatClock(elapsedMillis), modifier = Modifier.width(260.dp).height(120.dp), fontSize = 48.sp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FlipDigitCard(formatClock(remainingMillis), modifier = Modifier.width(260.dp).height(120.dp), fontSize = 48.sp)
                            if (!isRunning) {
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = minutesInput,
                                        onValueChange = { minutesInput = it.filter(Char::isDigit) },
                                        label = { Text("Minutes") },
                                        singleLine = true,
                                        modifier = Modifier.width(120.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    TextButton(onClick = {
                                        val minutes = minutesInput.toLongOrNull() ?: 0L
                                        countdownTotalMillis = (minutes * 60_000L).coerceAtLeast(1000L)
                                        remainingMillis = countdownTotalMillis
                                    }) { Text("Set", color = TimerAccent) }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isRunning) Color(0xFFFF6E6E) else TimerAccent)
                            .pointerInput(isRunning, finished) {
                                detectTapGestures(onTap = {
                                    if (finished) {
                                        finished = false
                                        elapsedMillis = 0L
                                        remainingMillis = countdownTotalMillis
                                    } else {
                                        isRunning = !isRunning
                                    }
                                })
                            }
                            .padding(horizontal = 34.dp, vertical = 14.dp)
                    ) {
                        Text(
                            when {
                                finished -> "Reset"
                                isRunning -> "Pause"
                                else -> "Start"
                            },
                            color = Color(0xFF0F1020), fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- Study feature ---------------- */

@Composable
private fun StudyHomeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var subjects by remember { mutableStateOf(loadStudySubjects(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var optionsForSubject by remember { mutableStateOf<StudySubject?>(null) }
    var customiseForSubject by remember { mutableStateOf<StudySubject?>(null) }

    LaunchedEffect(subjects.any { it.isRunning }) {
        while (subjects.any { it.isRunning }) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    fun persist(updated: List<StudySubject>) {
        subjects = updated
        saveStudySubjects(context, updated)
    }

    fun toggleRunning(subject: StudySubject) {
        val updated = subjects.map { s ->
            when {
                s.id == subject.id && s.isRunning -> s.copy(
                    isRunning = false,
                    accumulatedMillis = s.currentElapsedMillis(System.currentTimeMillis())
                )
                s.id == subject.id && !s.isRunning -> s.copy(
                    isRunning = true,
                    startedAtMillis = System.currentTimeMillis()
                )
                else -> s
            }
        }
        persist(updated)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Back", color = TimerAccent) }
                        Text("Study", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    }
                    if (subjects.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Add a subject with +", color = Color.LightGray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(subjects, key = { it.id }) { subject ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .pointerInput(subject.id) {
                                            detectTapGestures(onTap = { optionsForSubject = subject })
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(subject.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            formatDurationDhm(subject.currentElapsedMillis(now)),
                                            color = Color.LightGray,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (subject.isRunning) Color(0xFFFF6E6E).copy(alpha = 0.85f) else TimerAccent.copy(alpha = 0.9f))
                                            .pointerInput(subject.id, subject.isRunning) {
                                                detectTapGestures(onTap = { toggleRunning(subject) })
                                            }
                                            .padding(horizontal = 18.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            if (subject.isRunning) "Stop" else "Start",
                                            color = Color(0xFF0F1020),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                GlassFab(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp)
                )
            }
        }
    }

    if (showAddDialog) {
        StyledInputDialog("Add subject", "", { showAddDialog = false }) { name ->
            if (name.isNotBlank()) {
                persist(subjects + StudySubject(name = name))
            }
            showAddDialog = false
        }
    }

    optionsForSubject?.let { subject ->
        Dialog(onDismissRequest = { optionsForSubject = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TimerCardBg,
                contentColor = Color.White,
                modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(subject.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        val updated = subjects.map { s -> if (s.id == subject.id) s.copy(accumulatedMillis = 0L, isRunning = false, startedAtMillis = 0L) else s }
                        persist(updated)
                        optionsForSubject = null
                    }) { Text("Restart", color = TimerAccent) }
                    TextButton(onClick = {
                        customiseForSubject = subject
                        optionsForSubject = null
                    }) { Text("Customise time", color = TimerAccent) }
                    TextButton(onClick = {
                        persist(subjects.filterNot { it.id == subject.id })
                        optionsForSubject = null
                    }) { Text("Delete", color = Color(0xFFFF7A7A)) }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { optionsForSubject = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = Color.LightGray)
                    }
                }
            }
        }
    }

    customiseForSubject?.let { subject ->
        val existingElapsed = subject.currentElapsedMillis(System.currentTimeMillis())
        var daysText by remember(subject.id) { mutableStateOf((existingElapsed / 86_400_000L).toString()) }
        var hoursText by remember(subject.id) { mutableStateOf(((existingElapsed / 3_600_000L) % 24).toString()) }
        var minutesText by remember(subject.id) { mutableStateOf(((existingElapsed / 60_000L) % 60).toString()) }
        Dialog(onDismissRequest = { customiseForSubject = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = TimerCardBg, contentColor = Color.White) {
                Column(modifier = Modifier.padding(18.dp).width(260.dp)) {
                    Text("Customise time", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = daysText, onValueChange = { daysText = it.filter(Char::isDigit) }, label = { Text("Days") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = hoursText, onValueChange = { hoursText = it.filter(Char::isDigit) }, label = { Text("Hrs") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = minutesText, onValueChange = { minutesText = it.filter(Char::isDigit) }, label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { customiseForSubject = null }) { Text("Cancel", color = Color.LightGray) }
                        TextButton(onClick = {
                            val days = daysText.toLongOrNull() ?: 0L
                            val hours = hoursText.toLongOrNull() ?: 0L
                            val minutes = minutesText.toLongOrNull() ?: 0L
                            val newMillis = (days * 24 * 60 + hours * 60 + minutes) * 60_000L
                            val updated = subjects.map { s ->
                                if (s.id == subject.id) s.copy(accumulatedMillis = newMillis, isRunning = false, startedAtMillis = 0L) else s
                            }
                            persist(updated)
                            customiseForSubject = null
                        }) { Text("Save", color = TimerAccent, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}