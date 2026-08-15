package com.example.mindmap.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
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

private fun loadStrikeIntervalMinutes(context: Context): Int =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getInt("strike_interval_minutes", 180)

private fun saveStrikeIntervalMinutes(context: Context, minutes: Int) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE)
        .edit().putInt("strike_interval_minutes", minutes.coerceAtLeast(1)).apply()
}

private object StrikeSettingsState {
    var intervalMinutes by mutableStateOf(180)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            intervalMinutes = loadStrikeIntervalMinutes(context)
            loaded = true
        }
    }

    fun update(context: Context, minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        intervalMinutes = clamped
        saveStrikeIntervalMinutes(context, clamped)
    }
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

private object StudyTimerState {
    var subjects by mutableStateOf<List<StudySubject>>(emptyList())
    var pendingCelebration by mutableStateOf<Pair<String, Int>?>(null)
    private var loaded = false
    private val previousStrikeCounts = mutableStateMapOf<String, Int>()

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            subjects = loadStudySubjects(context)
            subjects.forEach { s -> previousStrikeCounts[s.id] = strikeCountForElapsed(s.currentElapsedMillis(System.currentTimeMillis())) }
            loaded = true
        }
    }

    fun persist(context: Context, updated: List<StudySubject>) {
        subjects = updated
        saveStudySubjects(context, updated)
    }

    fun pauseAll(context: Context) {
        val now = System.currentTimeMillis()
        val updated = subjects.map { s ->
            if (s.isRunning) s.copy(isRunning = false, accumulatedMillis = s.currentElapsedMillis(now)) else s
        }
        if (updated != subjects) persist(context, updated)
    }

    fun checkStrikes(context: Context, nowMillis: Long) {
        subjects.forEach { s ->
            val currentCount = strikeCountForElapsed(s.currentElapsedMillis(nowMillis))
            val priorCount = previousStrikeCounts[s.id] ?: currentCount
            if (currentCount > priorCount && pendingCelebration == null) {
                pendingCelebration = s.id to currentCount
                playStrikeSound(context, currentCount)
            }
            previousStrikeCounts[s.id] = currentCount
        }
    }
}

@Composable
private fun StudyTimerTicker() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        StudyTimerState.ensureLoaded(context)
        StrikeSettingsState.ensureLoaded(context)
    }
    LaunchedEffect(StudyTimerState.subjects) {
        while (StudyTimerState.subjects.any { it.isRunning }) {
            delay(1000)
            StudyTimerState.checkStrikes(context, System.currentTimeMillis())
        }
    }
}

@Composable
private fun StudyCelebrationHost() {
    StudyTimerState.pendingCelebration?.let { (_, count) ->
        StudyStrikeCelebrationOverlay(strikeCount = count) {
            StudyTimerState.pendingCelebration = null
        }
    }
}

/* ---------------- responsive timer box settings ---------------- */

private data class TimerBoxSettings(
    val widthPercent: Float = 0.88f,
    val boxHeightDp: Float = 130f,
    val fontSizeSp: Float = 78f,
    val fontWeightValue: Int = 900,
    val spacingDp: Float = 12f
)

private fun timerBoxPrefsKey(scope: String, isLandscape: Boolean) = "${scope}_${if (isLandscape) "landscape" else "portrait"}"

private fun defaultTimerBoxSettings(scope: String, isLandscape: Boolean): TimerBoxSettings = when {
    scope == "clock" && isLandscape -> TimerBoxSettings(widthPercent = 1f, boxHeightDp = 220f, fontSizeSp = 350f, fontWeightValue = 900, spacingDp = 14f)
    scope == "clock" -> TimerBoxSettings(widthPercent = 1f, boxHeightDp = 150f, fontSizeSp = 130f, fontWeightValue = 900, spacingDp = 14f)
    isLandscape -> TimerBoxSettings(widthPercent = 2.2f, boxHeightDp = 320f, fontSizeSp = 350f, fontWeightValue = 900, spacingDp = 12f)
    else -> TimerBoxSettings(widthPercent = 0.88f, boxHeightDp = 130f, fontSizeSp = 78f, fontWeightValue = 900, spacingDp = 12f)
}

private fun loadTimerBoxSettings(context: Context, isLandscape: Boolean, scope: String = "quick"): TimerBoxSettings {
    val prefs = context.getSharedPreferences("timer_box_settings", Context.MODE_PRIVATE)
    val key = timerBoxPrefsKey(scope, isLandscape)
    val defaults = defaultTimerBoxSettings(scope, isLandscape)
    return TimerBoxSettings(
        widthPercent = prefs.getFloat("${key}_width_percent", defaults.widthPercent),
        boxHeightDp = prefs.getFloat("${key}_box_height", defaults.boxHeightDp),
        fontSizeSp = prefs.getFloat("${key}_font_size", defaults.fontSizeSp),
        fontWeightValue = prefs.getInt("${key}_font_weight", defaults.fontWeightValue),
        spacingDp = prefs.getFloat("${key}_spacing", defaults.spacingDp)
    )
}

private fun saveTimerBoxSettings(context: Context, isLandscape: Boolean, settings: TimerBoxSettings, scope: String = "quick") {
    val key = timerBoxPrefsKey(scope, isLandscape)
    context.getSharedPreferences("timer_box_settings", Context.MODE_PRIVATE).edit()
        .putFloat("${key}_width_percent", settings.widthPercent)
        .putFloat("${key}_box_height", settings.boxHeightDp)
        .putFloat("${key}_font_size", settings.fontSizeSp)
        .putInt("${key}_font_weight", settings.fontWeightValue)
        .putFloat("${key}_spacing", settings.spacingDp)
        .apply()
}

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

/* ---------------- study strikes ---------------- */

private fun strikeCountForElapsed(elapsedMillis: Long): Int {
    val intervalMinutes = StrikeSettingsState.intervalMinutes.coerceAtLeast(1)
    val elapsedMinutes = elapsedMillis / 60_000.0
    return (elapsedMinutes / intervalMinutes).toInt()
}

private val StudyStrikeMessages = listOf(
    "সাবাশ খানকির ছেলে তোকে দিয়েই হবে 🔥",
    "দারুণ! এভাবেই এগিয়ে যা। 💪",
    "অসাধারণ পরিশ্রম! গর্বিত তোর জন্য। ⭐",
    "থামিস না, তুই একদম ঠিক পথে আছিস! 🚀",
    "চমৎকার! আরেকটা স্ট্রাইক তোর ঝুলিতে। 🏆"
)

private fun studyStrikeSoundResName(strikeIndex: Int): String = "strike_$strikeIndex"

private object TimerSoundPlayer {
    private var player: android.media.MediaPlayer? = null

    fun play(context: Context, resName: String) {
        stop()
        runCatching {
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId != 0) {
                player = android.media.MediaPlayer.create(context, resId)?.apply {
                    setOnCompletionListener { mp -> mp.release(); if (player == mp) player = null }
                    start()
                }
            }
        }
    }

    fun stop() {
        runCatching {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        }
        player = null
    }
}

private fun playStrikeSound(context: Context, strikeIndex: Int) {
    val specific = studyStrikeSoundResName(strikeIndex)
    val hasSpecific = context.resources.getIdentifier(specific, "raw", context.packageName) != 0
    val resName = if (hasSpecific) specific else "strike_default"
    if (context.resources.getIdentifier(resName, "raw", context.packageName) != 0) {
        TimerSoundPlayer.play(context, resName)
    }
}

/* ---------------- countdown time-up ---------------- */

private fun playRawSound(context: Context, resName: String) {
    TimerSoundPlayer.play(context, resName)
}

@Composable
private fun TimeUpOverlay(isLandscape: Boolean, onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(14000)
        TimerSoundPlayer.stop()
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(200f)
            .pointerInput("time-up-close") {
                detectTapGestures(onDoubleTap = {
                    TimerSoundPlayer.stop()
                    onFinished()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "TIME UP",
            color = Color(0xFFFF6E6E),
            fontSize = if (isLandscape) 64.sp else 52.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun CuteBouncingCharacter() {
    val infinite = rememberInfiniteTransition(label = "cuteCharacter")
    val bounce by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val blink by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2200
                1f at 0
                1f at 1900
                0.15f at 2000
                1f at 2100
                1f at 2200
            },
            RepeatMode.Restart
        ),
        label = "blink"
    )
    Canvas(
        modifier = Modifier
            .size(110.dp)
            .offset(y = (-bounce * 14).dp)
    ) {
        val w = size.width
        val h = size.height
        val bodyColor = Color(0xFFFFC94D)
        val cheekColor = Color(0xFFFF8FA3)
        // body
        drawCircle(color = bodyColor, radius = w * 0.42f, center = Offset(w / 2f, h / 2f))
        // cheeks
        drawCircle(color = cheekColor.copy(alpha = 0.55f), radius = w * 0.09f, center = Offset(w * 0.28f, h * 0.58f))
        drawCircle(color = cheekColor.copy(alpha = 0.55f), radius = w * 0.09f, center = Offset(w * 0.72f, h * 0.58f))
        // eyes (blink scales vertical)
        val eyeHeight = (h * 0.11f) * blink
        drawOval(
            color = Color(0xFF2B2B2B),
            topLeft = Offset(w * 0.34f, h * 0.42f - eyeHeight / 2f),
            size = Size(w * 0.09f, eyeHeight)
        )
        drawOval(
            color = Color(0xFF2B2B2B),
            topLeft = Offset(w * 0.57f, h * 0.42f - eyeHeight / 2f),
            size = Size(w * 0.09f, eyeHeight)
        )
        // smile
        drawArc(
            color = Color(0xFF2B2B2B),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.32f, h * 0.42f),
            size = Size(w * 0.36f, h * 0.30f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.035f, cap = StrokeCap.Round)
        )
        // little feet bounce
        drawCircle(color = Color(0xFFFF9F1C), radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.86f))
        drawCircle(color = Color(0xFFFF9F1C), radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.86f))
    }
}

@Composable
private fun ChubbyCelebrationCharacter() {
    val infinite = rememberInfiniteTransition(label = "chubbyCharacter")
    val dance by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dance"
    )
    val bounce by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val blink by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2200
                1f at 0; 1f at 1900; 0.12f at 2000; 1f at 2100; 1f at 2200
            },
            RepeatMode.Restart
        ),
        label = "blink"
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 170f))
    }

    Canvas(
        modifier = Modifier
            .size(210.dp)
            .graphicsLayer {
                translationX = dance * 16f
                translationY = (-bounce * 18f) + ((1f - entrance.value) * 70f)
                rotationZ = dance * 5f
                scaleX = 0.55f + entrance.value * 0.45f
                scaleY = 0.55f + entrance.value * 0.45f
                alpha = entrance.value
            }
    ) {
        val w = size.width
        val h = size.height
        val bodyColor = Color(0xFFFFC94D)
        val limbColor = Color(0xFFFFB020)
        val footColor = Color(0xFFFF8A1C)
        val cheek = Color(0xFFFF7A93)
        val hairColor = Color(0xFF2B2B2B)
        val legSwing = dance * 6f

        // legs (walking swing)
        drawRoundRect(
            color = limbColor,
            topLeft = Offset(w * 0.33f - legSwing * 0.01f * w, h * 0.78f),
            size = Size(w * 0.13f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.05f)
        )
        drawRoundRect(
            color = limbColor,
            topLeft = Offset(w * 0.54f + legSwing * 0.01f * w, h * 0.78f),
            size = Size(w * 0.13f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.05f)
        )
        drawOval(color = footColor, topLeft = Offset(w * 0.30f - legSwing * 0.01f * w, h * 0.91f), size = Size(w * 0.19f, h * 0.08f))
        drawOval(color = footColor, topLeft = Offset(w * 0.51f + legSwing * 0.01f * w, h * 0.91f), size = Size(w * 0.19f, h * 0.08f))

        // arms
        drawRoundRect(color = limbColor, topLeft = Offset(w * 0.08f, h * 0.48f - legSwing * 0.008f * h), size = Size(w * 0.11f, h * 0.24f), cornerRadius = CornerRadius(w * 0.05f))
        drawRoundRect(color = limbColor, topLeft = Offset(w * 0.81f, h * 0.48f + legSwing * 0.008f * h), size = Size(w * 0.11f, h * 0.24f), cornerRadius = CornerRadius(w * 0.05f))
        drawCircle(color = footColor, radius = w * 0.055f, center = Offset(w * 0.135f, h * 0.74f - legSwing * 0.008f * h))
        drawCircle(color = footColor, radius = w * 0.055f, center = Offset(w * 0.865f, h * 0.74f + legSwing * 0.008f * h))

        // body: rounded square, tuned narrower/taller and less rounded corners
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(w * 0.27f, h * 0.22f),
            size = Size(w * 0.46f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.10f)
        )

        // spiky anime hair
        val hairBaseY = h * 0.24f
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, hairBaseY)
                lineTo(w * 0.36f, hairBaseY - h * 0.13f)
                lineTo(w * 0.42f, hairBaseY)
                lineTo(w * 0.47f, hairBaseY - h * 0.17f)
                lineTo(w * 0.53f, hairBaseY)
                lineTo(w * 0.58f, hairBaseY - h * 0.13f)
                lineTo(w * 0.64f, hairBaseY)
                lineTo(w * 0.70f, hairBaseY - h * 0.10f)
                lineTo(w * 0.70f, hairBaseY + h * 0.02f)
                lineTo(w * 0.30f, hairBaseY + h * 0.02f)
                close()
            },
            color = hairColor
        )

        drawCircle(color = cheek.copy(alpha = 0.55f), radius = w * 0.075f, center = Offset(w * 0.32f, h * 0.56f))
        drawCircle(color = cheek.copy(alpha = 0.55f), radius = w * 0.075f, center = Offset(w * 0.68f, h * 0.56f))

        val eyeHeight = (h * 0.10f) * blink
        drawOval(color = Color(0xFF2B2B2B), topLeft = Offset(w * 0.37f, h * 0.48f - eyeHeight / 2f), size = Size(w * 0.08f, eyeHeight))
        drawOval(color = Color(0xFF2B2B2B), topLeft = Offset(w * 0.57f, h * 0.48f - eyeHeight / 2f), size = Size(w * 0.08f, eyeHeight))

        drawArc(
            color = Color(0xFF2B2B2B),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.36f, h * 0.50f),
            size = Size(w * 0.28f, h * 0.22f),
            style = Stroke(width = w * 0.028f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun StudyStrikeCelebrationOverlay(strikeCount: Int, onFinished: () -> Unit) {
    val message = remember(strikeCount) { StudyStrikeMessages[(strikeCount - 1).coerceAtLeast(0) % StudyStrikeMessages.size] }
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(strikeCount) {
        scale.snapTo(0.3f)
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(220))
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        delay(5200)
        alpha.animateTo(0f, tween(280))
        TimerSoundPlayer.stop()
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD90429))
            .graphicsLayer { this.alpha = alpha.value }
            .zIndex(500f)
            .pointerInput("strike-celebration-block") { detectTapGestures(onTap = {}) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        ) {
            ChubbyCelebrationCharacter()
            Spacer(Modifier.height(14.dp))
            Text(
                "$strikeCount Strike${if (strikeCount > 1) "s" else ""}!",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 24.dp)
            )
        }
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
            Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, modifier = Modifier.offset(x = 1.6.dp))
            Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, modifier = Modifier.offset(x = (-1.6).dp))
            Text(shown.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, modifier = Modifier.offset(y = 0.8.dp))
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
    extraBold: Boolean = false,
    fillWidth: Boolean = true
) {
    val rowModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    val arrangement = if (fillWidth) Arrangement.Center else Arrangement.Start
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement, modifier = rowModifier) {
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
    cornerRadius: Dp = 34.dp,
    extraBold: Boolean = true,
    topInset: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(TimerCardBg)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(top = topInset),
            contentAlignment = Alignment.Center
        ) {
            // Numbers must never overflow the card regardless of manual font-size
            // settings: clamp the requested size to what the box can actually hold,
            // estimating width by digit count (roughly 0.62 * fontSize per digit).
            val density = LocalDensity.current
            val boxHeightPx = with(density) { maxHeight.toPx() }
            val boxWidthPx = with(density) { maxWidth.toPx() }
            val digitCount = mainText.count { it.isDigit() || !it.isWhitespace() }.coerceAtLeast(1)
            val requestedPx = with(density) { fontSize.toPx() }
            val heightCapPx = boxHeightPx * 0.82f
            val widthCapPx = if (digitCount > 0) (boxWidthPx * 0.90f) / (digitCount * 0.62f) else requestedPx
            val safeFontSizePx = minOf(requestedPx, heightCapPx, widthCapPx).coerceAtLeast(1f)
            val safeFontSize = with(density) { safeFontSizePx.toSp() }
            FlipText(text = mainText, fontSize = safeFontSize, extraBold = extraBold)
        }
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
    Box(modifier = modifier.fillMaxWidth()) {
        FlipDigitCard(
            mainText,
            modifier = Modifier.fillMaxSize(),
            fontSize = fontSize,
            dividerThickness = dividerThickness,
            extraBold = true,
            topInset = if (topLabel.isNotEmpty()) 1.dp else 0.dp
        )
        if (topLabel.isNotEmpty()) {
            Text(
                topLabel,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
        if (cornerLabel.isNotEmpty()) {
            Text(
                text = cornerLabel,
                color = TimerDigit,
                fontSize = cornerFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (cornerAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        start = if (cornerAtStart) 10.dp else 0.dp,
                        end = if (cornerAtStart) 0.dp else 10.dp,
                        bottom = 8.dp
                    )
            )
        }
    }
}

// stopwatch / countdown - hour|minute|second ke alada alada box e vag kore dekhai, clock er box style er sathe milaiye
@Composable
private fun SplitTimeDisplay(
    totalMillis: Long,
    fontSize: TextUnit,
    dividerThickness: Dp,
    boxHeight: Dp,
    spacing: Dp = 12.dp,
    fontWeight: FontWeight = FontWeight.Black,
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        units.forEach { (label, value) ->
            Box(modifier = Modifier.weight(1f).height(boxHeight)) {
                FlipDigitCard(
                    "%02d".format(value),
                    modifier = Modifier.fillMaxSize(),
                    fontSize = fontSize,
                    dividerThickness = dividerThickness,
                    extraBold = true,
                    topInset = 1.dp
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
private fun LiveClockDisplay(is24Hour: Boolean, isLandscape: Boolean, boxSettings: TimerBoxSettings) {
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

    val mainFontSize = boxSettings.fontSizeSp.sp
    val dividerThickness = if (isLandscape) 8.dp else 3.dp
    val cornerFontSize = (boxSettings.fontSizeSp * 0.115f).sp
    val boxHeightDp = boxSettings.boxHeightDp.dp

    Row(
        modifier = Modifier
            .fillMaxWidth(boxSettings.widthPercent.coerceIn(0.3f, 3f))
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(boxSettings.spacingDp.dp)
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
            boxAspectRatio = 1f,
            modifier = Modifier.weight(1f).height(boxHeightDp)
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
            boxAspectRatio = 1f,
            modifier = Modifier.weight(1f).height(boxHeightDp)
        )
    }
}

/* ---------------- fullscreen immersive helper ---------------- */

@Composable
private fun ImmersiveSystemBars(controlsVisible: Boolean) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(dialogWindow) {
        val window = dialogWindow
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = TimerBg.toArgb()
            window.navigationBarColor = TimerBg.toArgb()
        }
        // Only a manual top-edge swipe reveals the bars (transient). No tap
        // listener anywhere should call show()/hide() based on UI taps.
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
@Composable
private fun KeepScreenOn() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(dialogWindow) {
        dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            dialogWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

@Composable
private fun TimerPanelButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fontSize: TextUnit = 15.sp,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 10.dp,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(120), label = "timerPanelBtnPress")
    val bgAlpha by animateFloatAsState(if (selected) 0.24f else 0.11f, animationSpec = tween(160), label = "timerPanelBtnBg")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .pointerInput(text) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

@Composable
private fun NumberWheelColumn(
    range: IntRange,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp,
    visibleCount: Int = 3,
    columnWidth: Dp = 64.dp
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(selected, values) {
        val targetIndex = values.indexOf(selected).coerceAtLeast(0)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }
            .collect { (index, offset, scrolling) ->
                if (!scrolling) {
                    val centeredIndex = (index + if (offset > itemHeightPx / 2) 1 else 0).coerceIn(values.indices)
                    val value = values[centeredIndex]
                    if (value != selected) onSelectedChange(value)
                }
            }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxHeight().width(columnWidth)
        ) {
            items(values) { value ->
                Box(modifier = Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    val isSelected = value == selected
                    Text(
                        "%02d".format(value),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                        fontSize = if (isSelected) 22.sp else 17.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
    }
}

@Composable
private fun TimerBoxSettingsPanel(
    settings: TimerBoxSettings,
    isLandscape: Boolean,
    scopeLabel: String = "Timer box",
    onSettingsChange: (TimerBoxSettings) -> Unit,
    onDismiss: () -> Unit,
    onResetDefaults: (() -> TimerBoxSettings)? = null
) {
    var widthPercent by remember(settings) { mutableStateOf(settings.widthPercent) }
    var boxHeight by remember(settings) { mutableStateOf(settings.boxHeightDp) }
    var fontSize by remember(settings) { mutableStateOf(settings.fontSizeSp) }
    var fontWeightValue by remember(settings) { mutableStateOf(settings.fontWeightValue) }
    var spacing by remember(settings) { mutableStateOf(settings.spacingDp) }

    fun push() {
        onSettingsChange(TimerBoxSettings(widthPercent, boxHeight, fontSize, fontWeightValue, spacing))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (isLandscape) "$scopeLabel (Landscape)" else "$scopeLabel (Portrait)",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                Text("Width  ${"%.0f".format(widthPercent * 100)}%", color = Color.LightGray, fontSize = 13.sp)
                Slider(
                    value = widthPercent,
                    valueRange = if (isLandscape) 0.5f..3f else 0.4f..1f,
                    onValueChange = { widthPercent = it; push() },
                    colors = SliderDefaults.colors(thumbColor = TimerAccent, activeTrackColor = TimerAccent)
                )

                Text("Box height  ${boxHeight.toInt()}dp", color = Color.LightGray, fontSize = 13.sp)
                Slider(
                    value = boxHeight,
                    valueRange = 60f..420f,
                    onValueChange = { boxHeight = it; push() },
                    colors = SliderDefaults.colors(thumbColor = TimerAccent, activeTrackColor = TimerAccent)
                )

                Text("Number size  ${fontSize.toInt()}sp", color = Color.LightGray, fontSize = 13.sp)
                Slider(
                    value = fontSize,
                    valueRange = 24f..420f,
                    onValueChange = { fontSize = it; push() },
                    colors = SliderDefaults.colors(thumbColor = TimerAccent, activeTrackColor = TimerAccent)
                )

                Text("Number weight  $fontWeightValue", color = Color.LightGray, fontSize = 13.sp)
                Slider(
                    value = fontWeightValue.toFloat(),
                    valueRange = 100f..900f,
                    onValueChange = { fontWeightValue = it.toInt(); push() },
                    colors = SliderDefaults.colors(thumbColor = TimerAccent, activeTrackColor = TimerAccent)
                )

                Text("Spacing  ${spacing.toInt()}dp", color = Color.LightGray, fontSize = 13.sp)
                Slider(
                    value = spacing,
                    valueRange = 0f..40f,
                    onValueChange = { spacing = it; push() },
                    colors = SliderDefaults.colors(thumbColor = TimerAccent, activeTrackColor = TimerAccent)
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        val reset = onResetDefaults?.invoke() ?: if (isLandscape) {
                            TimerBoxSettings(widthPercent = 2.2f, boxHeightDp = 320f, fontSizeSp = 350f, fontWeightValue = 900, spacingDp = 12f)
                        } else {
                            TimerBoxSettings(widthPercent = 0.88f, boxHeightDp = 130f, fontSizeSp = 78f, fontWeightValue = 900, spacingDp = 12f)
                        }
                        widthPercent = reset.widthPercent
                        boxHeight = reset.boxHeightDp
                        fontSize = reset.fontSizeSp
                        fontWeightValue = reset.fontWeightValue
                        spacing = reset.spacingDp
                        onSettingsChange(reset)
                    }) { Text("Default", color = Color.LightGray) }
                    TextButton(onClick = onDismiss) { Text("Done", color = TimerAccent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CountdownTimeSetPanel(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onCustomTap: () -> Unit,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 64.dp,
    panelMaxWidth: Dp = 84.dp,
    wheelItemHeight: Dp = 34.dp,
    colonFontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    Row(
        modifier = modifier
            .widthIn(max = panelMaxWidth)
            .pointerInput("countdown-custom-tap") { detectTapGestures(onTap = { onCustomTap() }) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        NumberWheelColumn(range = 0..99, selected = hours, onSelectedChange = onHoursChange, itemHeight = wheelItemHeight, visibleCount = 3, columnWidth = columnWidth)
        Text(":", color = Color.White, fontSize = colonFontSize, fontWeight = FontWeight.Black)
        NumberWheelColumn(range = 0..59, selected = minutes, onSelectedChange = onMinutesChange, itemHeight = wheelItemHeight, visibleCount = 3, columnWidth = columnWidth)
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
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStudy by rememberSaveable { mutableStateOf(false) }
    var showQuickTimer by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSystemBars(controlsVisible)
        KeepScreenOn()
        StudyTimerTicker()
        val activity = LocalActivity.current
        DisposableEffect(activity) {
            val previousOrientation = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            onDispose {
                // Timer section (Clock/Stopwatch/Countdown/Study) ছেড়ে গেলেই এই onDispose চলবে,
                // orientation change এ চলবে না কারণ MainActivity manifest-এ configChanges হ্যান্ডেল করা আছে
                // এবং এই DisposableEffect এখন শুধু activity key-তে bind, orientation-এ recompose হবে না।
                StudyTimerState.pauseAll(context)
                activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        // App গিয়ে background/অন্য app-এ চলে গেলে (home button, app-switch, screen off)
        // Compose dispose হয় না, তাই আলাদাভাবে ON_STOP শুনে Study Timer pause করা হচ্ছে।
        // Orientation change-এ ON_STOP fire করবে না, কারণ manifest configChanges handle করছে।
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    StudyTimerState.pauseAll(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val homeDialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        var homeBrightnessLevel by remember {
            mutableStateOf(homeDialogWindow?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.6f)
        }
        fun applyHomeBrightness(value: Float) {
            val clamped = value.coerceIn(0.02f, 1f)
            homeBrightnessLevel = clamped
            homeDialogWindow?.let { window ->
                val params = window.attributes
                params.screenBrightness = clamped
                window.attributes = params
            }
        }
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("timer-home-brightness") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            applyHomeBrightness(homeBrightnessLevel - dragAmount.y / 600f)
                        }
                    }
                    .pointerInput("timer-home-tap") {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                StudyTimerTicker()
                val isLandscape = maxWidth > maxHeight
                val activeClockBoxSettings = remember(isLandscape) { loadTimerBoxSettings(context, isLandscape = isLandscape, scope = "clock") }
                val iconSize = if (isLandscape) 32.dp else 44.dp
                val panelReserve = iconSize + 26.dp
                val targetEndPadding = if (isLandscape && controlsVisible) panelReserve else 0.dp
                val animatedEndPadding by animateDpAsState(
                    targetValue = targetEndPadding,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "timerEndPadding"
                )

                val homeTargetBottomPadding = if (!isLandscape && controlsVisible) 96.dp else 0.dp
                val animatedBottomPadding by animateDpAsState(
                    targetValue = homeTargetBottomPadding,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "timerBottomPadding"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(end = animatedEndPadding, bottom = animatedBottomPadding)
                ) {
                    LiveClockDisplay(is24Hour = is24Hour, isLandscape = isLandscape, boxSettings = activeClockBoxSettings)
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(tween(200))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
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
                StudyCelebrationHost()
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
private data class BoxEditTarget(val scope: String, val isLandscape: Boolean, val label: String)

@Composable
private fun TimerSettingsDialog(
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onIs24HourChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    StrikeSettingsState.ensureLoaded(context)
    var boxEditTarget by remember { mutableStateOf<BoxEditTarget?>(null) }
    var strikeHoursText by remember { mutableStateOf((StrikeSettingsState.intervalMinutes / 60).toString()) }
    var strikeMinutesText by remember { mutableStateOf((StrikeSettingsState.intervalMinutes % 60).toString()) }
    val savedStrikeIntervalMinutes = StrikeSettingsState.intervalMinutes
    val strikeInputMinutes = (strikeHoursText.toIntOrNull() ?: 0) * 60 + (strikeMinutesText.toIntOrNull() ?: 0)
    val strikeIsDirty = strikeInputMinutes > 0 && strikeInputMinutes != savedStrikeIntervalMinutes

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(20.dp))
                Text("Strike timer", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Strike animation repeats after this much time", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = strikeHoursText,
                        onValueChange = { strikeHoursText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = strikeMinutesText,
                        onValueChange = { strikeMinutesText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (strikeIsDirty) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TimerAccent.copy(alpha = 0.22f))
                            .pointerInput(strikeInputMinutes) {
                                detectTapGestures(onTap = {
                                    StrikeSettingsState.update(context, strikeInputMinutes)
                                })
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save", color = TimerAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Saved", color = Color.LightGray, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Box size", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    BoxEditTarget("clock", false, "Real-time clock (Portrait)"),
                    BoxEditTarget("clock", true, "Real-time clock (Landscape)"),
                    BoxEditTarget("quick", false, "Stopwatch / Countdown (Portrait)"),
                    BoxEditTarget("quick", true, "Stopwatch / Countdown (Landscape)")
                ).forEach { target ->
                    TextButton(onClick = { boxEditTarget = target }, modifier = Modifier.fillMaxWidth()) {
                        Text(target.label, color = TimerAccent, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = TimerAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    boxEditTarget?.let { target ->
        val currentSettings = remember(target) { loadTimerBoxSettings(context, isLandscape = target.isLandscape, scope = target.scope) }
        TimerBoxSettingsPanel(
            settings = currentSettings,
            isLandscape = target.isLandscape,
            scopeLabel = target.label,
            onSettingsChange = { updated -> saveTimerBoxSettings(context, target.isLandscape, updated, scope = target.scope) },
            onDismiss = { boxEditTarget = null },
            onResetDefaults = { defaultTimerBoxSettings(target.scope, target.isLandscape) }
        )
    }
}

/* ---------------- Quick stopwatch / countdown ---------------- */

@Composable
private fun QuickTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("stopwatch") }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var elapsedMillis by rememberSaveable { mutableStateOf(0L) }
    var countdownTotalMillis by rememberSaveable { mutableStateOf(5 * 60_000L) }
    var remainingMillis by rememberSaveable { mutableStateOf(countdownTotalMillis) }
    var startTimestamp by rememberSaveable { mutableStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var pickerHours by rememberSaveable { mutableStateOf(((countdownTotalMillis / 3_600_000L).toInt())) }
    var pickerMinutes by rememberSaveable { mutableStateOf(((countdownTotalMillis / 60_000L) % 60).toInt()) }
    var showCustomTimeDialog by remember { mutableStateOf(false) }
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    var portraitBoxSettings by remember { mutableStateOf(loadTimerBoxSettings(context, isLandscape = false)) }
    var landscapeBoxSettings by remember { mutableStateOf(loadTimerBoxSettings(context, isLandscape = true)) }
    var maxWidthLandscapeSnapshot by rememberSaveable { mutableStateOf(false) }

    fun applyPickerToCountdown() {
        val newMillis = (pickerHours * 3_600_000L + pickerMinutes * 60_000L).coerceAtLeast(1000L)
        countdownTotalMillis = newMillis
        remainingMillis = newMillis
    }
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

    LaunchedEffect(finished) {
        if (finished) {
            playRawSound(context, "timer_up")
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSystemBars(controlsVisible)
        KeepScreenOn()
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
                StudyTimerTicker()
                val isLandscape = maxWidth > maxHeight
                maxWidthLandscapeSnapshot = isLandscape
                LaunchedEffect(isLandscape) {
                    if (isLandscape) landscapeBoxSettings = loadTimerBoxSettings(context, isLandscape = true)
                    else portraitBoxSettings = loadTimerBoxSettings(context, isLandscape = false)
                }
                val activeBoxSettings = if (isLandscape) landscapeBoxSettings else portraitBoxSettings
                val digitFontSize = activeBoxSettings.fontSizeSp.sp
                val digitFontWeight = FontWeight(activeBoxSettings.fontWeightValue)
                val dividerThickness = if (isLandscape) 7.dp else 3.dp
                val boxHeightDp = activeBoxSettings.boxHeightDp.dp
                val boxSpacingDp = activeBoxSettings.spacingDp.dp
                val panelReserve = if (isLandscape) 128.dp else 0.dp
                val displayWidth = (maxWidth * activeBoxSettings.widthPercent).coerceAtMost(if (isLandscape) maxWidth * 3f else maxWidth)
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
                    var quickTopBarHeightPx by remember { mutableStateOf(0) }
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(10f)
                            .onGloballyPositioned { quickTopBarHeightPx = it.size.height },
                        enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(260)),
                        exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(tween(200))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TimerBg)
                                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimerPanelButton(text = "Back", onClick = onDismiss)
                            Spacer(Modifier.weight(1f))
                            TimerPanelButton(
                                text = "Stopwatch",
                                selected = mode == "stopwatch",
                                onClick = { if (!isRunning) { mode = "stopwatch"; finished = false; hasStarted = false; elapsedMillis = 0L } }
                            )
                            TimerPanelButton(
                                text = "Countdown",
                                selected = mode == "countdown",
                                onClick = { if (!isRunning) { mode = "countdown"; finished = false; hasStarted = false; remainingMillis = countdownTotalMillis } }
                            )
                        }
                    }

                    val quickTargetBottomPadding = if (!isLandscape && controlsVisible) 140.dp else 0.dp
                    val animatedQuickBottomPadding by animateDpAsState(
                        targetValue = quickTargetBottomPadding,
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "quickTimerBottomPadding"
                    )
                    val quickTopBarHeightDp = with(density) { quickTopBarHeightPx.toDp() }
                    val quickTargetTopPadding = if (controlsVisible) quickTopBarHeightDp else 0.dp
                    val animatedQuickTopPadding by animateDpAsState(
                        targetValue = quickTargetTopPadding,
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "quickTimerTopPadding"
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().padding(
                            end = animatedEndPadding,
                            top = animatedQuickTopPadding,
                            bottom = animatedQuickBottomPadding
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        SplitTimeDisplay(
                            totalMillis = if (mode == "stopwatch") elapsedMillis else remainingMillis,
                            fontSize = digitFontSize,
                            dividerThickness = dividerThickness,
                            boxHeight = boxHeightDp,
                            spacing = boxSpacingDp,
                            fontWeight = digitFontWeight,
                            modifier = Modifier.width(displayWidth)
                        )
                    }

                    if (finished) {
                        TimeUpOverlay(isLandscape = isLandscape) {
                            finished = false
                            hasStarted = false
                            if (mode == "stopwatch") elapsedMillis = 0L else remainingMillis = countdownTotalMillis
                        }
                    }

                    val showTimeSetPanel = mode == "countdown" && !isRunning && !finished && controlsVisible

                    if (isLandscape) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(top = quickTopBarHeightDp + 12.dp, end = 12.dp, bottom = 12.dp),
                            enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.82f),
                            exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + scaleOut(tween(200, easing = FastOutSlowInEasing), targetScale = 0.82f)
                        ) {
                            Surface(
                                modifier = Modifier.widthIn(max = 104.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xE6121212),
                                contentColor = Color.White,
                                shadowElevation = 6.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TimerPanelButton(
                                        text = if (isRunning) "Pause" else "Start",
                                        fontSize = 13.sp,
                                        horizontalPadding = 14.dp,
                                        verticalPadding = 10.dp
                                    ) {
                                        isRunning = !isRunning
                                        if (isRunning) hasStarted = true
                                    }
                                    if (hasStarted) {
                                        TimerPanelButton(
                                            text = "Reset",
                                            fontSize = 11.sp,
                                            horizontalPadding = 11.dp,
                                            verticalPadding = 8.dp
                                        ) {
                                            isRunning = false
                                            finished = false
                                            hasStarted = false
                                            if (mode == "stopwatch") elapsedMillis = 0L else remainingMillis = countdownTotalMillis
                                        }
                                    }
                                    if (showTimeSetPanel) {
                                        CountdownTimeSetPanel(
                                            hours = pickerHours,
                                            minutes = pickerMinutes,
                                            onHoursChange = { pickerHours = it; applyPickerToCountdown() },
                                            onMinutesChange = { pickerMinutes = it; applyPickerToCountdown() },
                                            onCustomTap = { showCustomTimeDialog = true },
                                            columnWidth = 34.dp,
                                            panelMaxWidth = 78.dp,
                                            wheelItemHeight = 28.dp,
                                            colonFontSize = 15.sp
                                        )
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                TimerPanelButton(
                                    text = if (isRunning) "Pause" else "Start",
                                    modifier = Modifier.padding(bottom = if (hasStarted || showTimeSetPanel) 16.dp else 0.dp)
                                ) {
                                    isRunning = !isRunning
                                    if (isRunning) hasStarted = true
                                }
                                if (hasStarted) {
                                    TimerPanelButton(
                                        text = "Reset",
                                        modifier = Modifier.padding(bottom = if (showTimeSetPanel) 16.dp else 0.dp)
                                    ) {
                                        isRunning = false
                                        finished = false
                                        hasStarted = false
                                        if (mode == "stopwatch") elapsedMillis = 0L else remainingMillis = countdownTotalMillis
                                    }
                                }
                                if (showTimeSetPanel) {
                                    CountdownTimeSetPanel(
                                        hours = pickerHours,
                                        minutes = pickerMinutes,
                                        onHoursChange = { pickerHours = it; applyPickerToCountdown() },
                                        onMinutesChange = { pickerMinutes = it; applyPickerToCountdown() },
                                        onCustomTap = { showCustomTimeDialog = true }
                                    )
                                }
                            }
                        }
                    }
                    StudyCelebrationHost()
                }
            }
        }
    }
    if (showCustomTimeDialog) {
        Dialog(onDismissRequest = { showCustomTimeDialog = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = TimerCardBg, contentColor = Color.White) {
                var hourText by remember { mutableStateOf(pickerHours.toString()) }
                var minuteText by remember { mutableStateOf(pickerMinutes.toString()) }
                Column(modifier = Modifier.padding(18.dp).imePadding().width(260.dp)) {
                    Text("Customise countdown", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter(Char::isDigit) },
                            label = { Text("Hours") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter(Char::isDigit) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showCustomTimeDialog = false }) { Text("Cancel", color = Color.LightGray) }
                        TextButton(onClick = {
                            pickerHours = (hourText.toIntOrNull() ?: 0).coerceIn(0, 99)
                            pickerMinutes = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                            applyPickerToCountdown()
                            showCustomTimeDialog = false
                        }) { Text("Save", color = TimerAccent, fontWeight = FontWeight.Bold) }
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
    LaunchedEffect(Unit) { StudyTimerState.ensureLoaded(context) }
    StudyTimerTicker()
    val subjects = StudyTimerState.subjects
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
        StudyTimerState.persist(context, updated)
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

    var studyControlsVisible by rememberSaveable { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSystemBars(studyControlsVisible)
        KeepScreenOn()
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("study-tap") { detectTapGestures(onTap = { studyControlsVisible = !studyControlsVisible }) }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
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
                                        val strikeCount = strikeCountForElapsed(subject.currentElapsedMillis(now))
                                        if (strikeCount > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                                Text("★", color = Color(0xFFFFD700), fontSize = 13.sp)
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "$strikeCount Strike${if (strikeCount > 1) "s" else ""}",
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
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

                StudyCelebrationHost()
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