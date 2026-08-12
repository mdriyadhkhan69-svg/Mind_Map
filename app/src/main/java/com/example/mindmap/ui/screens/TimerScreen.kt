package com.example.mindmap.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val TimerBg = Color(0xFF0B0B0F)
private val TimerCardBg = Color(0xFF15151A)
private val TimerDigit = Color(0xFFB7B7BF)
private val TimerAccent = Color(0xFF64FFDA)

/* ---------------- persistence ---------------- */

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

private fun formatDurationDhms(totalMillis: Long): String {
    val totalSeconds = totalMillis / 1000L
    val days = totalSeconds / 86400
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m ${seconds}s"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/* ---------------- flip-digit building blocks ---------------- */

@Composable
private fun FlipDigitCell(
    char: Char,
    fontSize: TextUnit,
    color: Color = TimerDigit,
    fontWeight: FontWeight = FontWeight.Black,
    extraBold: Boolean = false
) {
    var shown by remember { mutableStateOf(char) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(char) {
        if (char != shown) {
            rotation.animateTo(90f, tween(150, easing = FastOutLinearInEasing))
            shown = char
            rotation.animateTo(0f, tween(160, easing = LinearOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            rotationX = rotation.value
            cameraDistance = 32f * density
        },
        contentAlignment = Alignment.Center
    ) {
        if (extraBold) {
            Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, modifier = Modifier.offset(x = 0.9.dp))
            Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, modifier = Modifier.offset(x = (-0.9).dp))
        }
        Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight)
    }
}

@Composable
private fun FlipText(
    text: String,
    fontSize: TextUnit,
    color: Color = TimerDigit,
    fontWeight: FontWeight = FontWeight.Black,
    extraBold: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEach { c ->
            if (c.isDigit()) {
                FlipDigitCell(char = c, fontSize = fontSize, color = color, fontWeight = fontWeight, extraBold = extraBold)
            } else {
                Text(c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight)
            }
        }
    }
}

@Composable
private fun FlipDigitCard(
    mainText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 92.sp,
    dividerThickness: Dp = 2.dp,
    cornerRadius: Dp = 26.dp,
    extraBold: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(TimerCardBg),
        contentAlignment = Alignment.Center
    ) {
        FlipText(text = mainText, fontSize = fontSize, extraBold = extraBold)
        Box(
            Modifier
                .fillMaxWidth()
                .height(dividerThickness)
                .background(Color.Black.copy(alpha = 0.75f))
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
    fontSize: TextUnit,
    dividerThickness: Dp,
    cornerFontSize: TextUnit,
    cornerIsNumeric: Boolean,
    boxAspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(boxAspectRatio)) {
        FlipDigitCard(mainText, modifier = Modifier.fillMaxSize(), fontSize = fontSize, dividerThickness = dividerThickness, extraBold = true)
        if (topLabel.isNotEmpty()) {
            Text(
                topLabel,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            )
        }
        if (cornerLabel.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(if (cornerAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(14.dp)
            ) {
                if (cornerIsNumeric) {
                    FlipText(
                        text = cornerLabel,
                        fontSize = cornerFontSize,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        extraBold = false
                    )
                } else {
                    Text(cornerLabel, color = Color.White.copy(alpha = 0.85f), fontSize = cornerFontSize, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// stopwatch / countdown - hour|minute|second ke alada alada box e vag kore dekhai, clock er box style er sathe milaiye
@Composable
private fun SplitTimeDisplay(
    totalMillis: Long,
    fontSize: TextUnit,
    dividerThickness: Dp,
    boxAspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val totalSeconds = totalMillis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds / 60) % 60
    val s = totalSeconds % 60
    val units = if (h > 0) {
        listOf("HR" to h, "MIN" to m, "SEC" to s)
    } else {
        listOf("MIN" to m, "SEC" to s)
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        units.forEach { (label, value) ->
            Box(modifier = Modifier.weight(1f).aspectRatio(boxAspectRatio)) {
                FlipDigitCard(
                    "%02d".format(value),
                    modifier = Modifier.fillMaxSize(),
                    fontSize = fontSize,
                    dividerThickness = dividerThickness,
                    extraBold = true
                )
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }
        }
    }
}

/* ---------------- live clock ---------------- */

@Composable
private fun LiveClockDisplay(is24Hour: Boolean, isLandscape: Boolean) {
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

    val mainFontSize = if (isLandscape) 230.sp else 96.sp
    val dividerThickness = if (isLandscape) 7.dp else 2.dp
    val cornerFontSize = if (isLandscape) 30.sp else 14.sp
    val boxAspectRatio = if (isLandscape) 1.05f else 1.6f

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlipBlock(
            topLabel = dateLabel,
            mainText = "%02d".format(hourDisplay),
            cornerLabel = if (is24Hour) "" else amPm,
            cornerAtStart = true,
            fontSize = mainFontSize,
            dividerThickness = dividerThickness,
            cornerFontSize = cornerFontSize,
            cornerIsNumeric = false,
            boxAspectRatio = boxAspectRatio,
            modifier = Modifier.weight(1f)
        )
        FlipBlock(
            topLabel = dayLabel,
            mainText = "%02d".format(minute),
            cornerLabel = "%02d".format(second),
            cornerAtStart = false,
            fontSize = mainFontSize,
            dividerThickness = dividerThickness,
            cornerFontSize = cornerFontSize,
            cornerIsNumeric = true,
            boxAspectRatio = boxAspectRatio,
            modifier = Modifier.weight(1f)
        )
    }
}

/* ---------------- fullscreen immersive helper ---------------- */

@Composable
private fun ImmersiveSystemBars(controlsVisible: Boolean) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(controlsVisible, dialogWindow) {
        val window = dialogWindow
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controlsVisible) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            window?.statusBarColor = AndroidColor.BLACK
            window?.navigationBarColor = AndroidColor.BLACK
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/* ---------------- options panel: ekta box, tar vitore chotto gray button gulo ---------------- */

@Composable
private fun TimerSideIcon(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, animationSpec = tween(120), label = "timerIconPress")
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .pointerInput(icon) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun TimerOptionsPanel(iconSize: Dp, items: List<Pair<ImageVector, () -> Unit>>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE6121212),
        contentColor = Color.White,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { (icon, action) -> TimerSideIcon(icon = icon, size = iconSize, onClick = action) }
        }
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
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStudy by rememberSaveable { mutableStateOf(false) }
    var showQuickTimer by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSystemBars(controlsVisible)
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("timer-home-tap") {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                val isLandscape = maxWidth > maxHeight
                val iconSize = if (isLandscape) 32.dp else 44.dp
                val panelReserve = iconSize + 26.dp
                val targetEndPadding = if (isLandscape && controlsVisible) panelReserve else 0.dp
                val animatedEndPadding by animateDpAsState(
                    targetValue = targetEndPadding,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "timerEndPadding"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(end = animatedEndPadding)
                ) {
                    LiveClockDisplay(is24Hour = is24Hour, isLandscape = isLandscape)
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(tween(200))
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
                    modifier = Modifier
                        .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomEnd)
                        .padding(if (isLandscape) 12.dp else 22.dp),
                    enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.82f),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + scaleOut(tween(200, easing = FastOutSlowInEasing), targetScale = 0.82f)
                ) {
                    TimerOptionsPanel(
                        iconSize = iconSize,
                        items = listOf(
                            Icons.Default.MenuBook to { showStudy = true },
                            Icons.Default.Timer to { showQuickTimer = true },
                            Icons.Default.Tune to { showSettings = true }
                        )
                    )
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
    var mode by rememberSaveable { mutableStateOf("stopwatch") }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var elapsedMillis by rememberSaveable { mutableStateOf(0L) }
    var countdownTotalMillis by rememberSaveable { mutableStateOf(5 * 60_000L) }
    var remainingMillis by rememberSaveable { mutableStateOf(countdownTotalMillis) }
    var startTimestamp by rememberSaveable { mutableStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var minutesInput by rememberSaveable { mutableStateOf("5") }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSystemBars(controlsVisible)
        val density = LocalDensity.current
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        var brightnessLevel by remember {
            mutableStateOf(dialogWindow?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.6f)
        }
        fun applyBrightness(value: Float) {
            val clamped = value.coerceIn(0.02f, 1f)
            brightnessLevel = clamped
            dialogWindow?.let { window ->
                val params = window.attributes
                params.screenBrightness = clamped
                window.attributes = params
            }
        }

        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                val digitFontSize = if (isLandscape) 130.sp else 64.sp
                val dividerThickness = if (isLandscape) 6.dp else 2.dp
                val boxAspectRatio = if (isLandscape) 0.9f else 1.05f
                val panelReserve = if (isLandscape) 128.dp else 0.dp
                val displayWidth = if (isLandscape) maxWidth * 0.6f else maxWidth * 0.88f
                val targetEndPadding = if (isLandscape && controlsVisible) panelReserve else 0.dp
                val animatedEndPadding by animateDpAsState(
                    targetValue = targetEndPadding,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "quickTimerEndPadding"
                )
                var horizontalDragAccum by remember { mutableStateOf(0f) }
                val swipeThresholdPx = with(density) { 90.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(mode, isRunning) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (kotlin.math.abs(horizontalDragAccum) > swipeThresholdPx && !isRunning) {
                                        mode = if (horizontalDragAccum < 0) "countdown" else "stopwatch"
                                        finished = false
                                        if (mode == "stopwatch") elapsedMillis = 0L else remainingMillis = countdownTotalMillis
                                    }
                                    horizontalDragAccum = 0f
                                },
                                onDragCancel = { horizontalDragAccum = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                    horizontalDragAccum += dragAmount.x
                                } else {
                                    applyBrightness(brightnessLevel - dragAmount.y / 600f)
                                }
                            }
                        }
                        .pointerInput("quick-timer-tap") {
                            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                        }
                ) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(260)),
                        exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(tween(200))
                    ) {
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
                    }

                    Box(
                        modifier = Modifier.fillMaxSize().padding(end = animatedEndPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        if (finished) {
                            Text("TIME UP", color = Color(0xFFFF6E6E), fontSize = if (isLandscape) 64.sp else 44.sp, fontWeight = FontWeight.Black)
                        } else {
                            SplitTimeDisplay(
                                totalMillis = if (mode == "stopwatch") elapsedMillis else remainingMillis,
                                fontSize = digitFontSize,
                                dividerThickness = dividerThickness,
                                boxAspectRatio = boxAspectRatio,
                                modifier = Modifier.width(displayWidth)
                            )
                        }
                    }

                    val showMinutesField = mode == "countdown" && !isRunning && !finished && controlsVisible

                    if (isLandscape) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                            enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.82f),
                            exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + scaleOut(tween(200, easing = FastOutSlowInEasing), targetScale = 0.82f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xE6121212),
                                contentColor = Color.White,
                                shadowElevation = 6.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp).width(108.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showMinutesField) {
                                        OutlinedTextField(
                                            value = minutesInput,
                                            onValueChange = { minutesInput = it.filter(Char::isDigit) },
                                            label = { Text("Min", fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        TextButton(onClick = {
                                            val minutes = minutesInput.toLongOrNull() ?: 0L
                                            countdownTotalMillis = (minutes * 60_000L).coerceAtLeast(1000L)
                                            remainingMillis = countdownTotalMillis
                                        }) { Text("Set", color = TimerAccent, fontSize = 13.sp) }
                                    }
                                    QuickTimerActionButton(isRunning = isRunning, finished = finished, compact = true) {
                                        if (finished) {
                                            finished = false
                                            elapsedMillis = 0L
                                            remainingMillis = countdownTotalMillis
                                        } else {
                                            isRunning = !isRunning
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(260)),
                            exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(tween(200))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 24.dp)) {
                                if (showMinutesField) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
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
                                QuickTimerActionButton(isRunning = isRunning, finished = finished, compact = false) {
                                    if (finished) {
                                        finished = false
                                        elapsedMillis = 0L
                                        remainingMillis = countdownTotalMillis
                                    } else {
                                        isRunning = !isRunning
                                    }
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
private fun QuickTimerActionButton(isRunning: Boolean, finished: Boolean, compact: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, animationSpec = tween(120), label = "timerButtonPress")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isRunning) Color(0xFFFF6E6E) else TimerAccent)
            .pointerInput(isRunning, finished) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = if (compact) 14.dp else 34.dp, vertical = if (compact) 10.dp else 14.dp)
    ) {
        Text(
            when {
                finished -> "Reset"
                isRunning -> "Pause"
                else -> "Start"
            },
            color = Color(0xFF0F1020), fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 16.sp
        )
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
                                            formatDurationDhms(subject.currentElapsedMillis(now)),
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