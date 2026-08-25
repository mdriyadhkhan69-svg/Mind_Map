package com.example.mindmap.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.mindmap.ui.theme.SoftNeutral
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


private val TimerBg = Color(0xFF0B0B0F)
private val TimerCardBg = Color(0xFF15151A)
private val TimerDigit = Color(0xFFB7B7BF)
private val TimerAccent = Color(0xFF64FFDA)

/* ---------------- persistence ---------------- */

internal data class StudySubject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accumulatedMillis: Long = 0L,
    val isRunning: Boolean = false,
    val startedAtMillis: Long = 0L,
    val popupEnabled: Boolean = true
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
/* ---------------- strike animation settings (master + character + quote toggles) ---------------- */

private fun loadStrikeAnimationEnabled(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("strike_animation_enabled", true)

private fun saveStrikeAnimationEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("strike_animation_enabled", value).apply()
}

private fun loadCharacterEnabled(context: Context, characterId: StrikeCharacterId): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("strike_${characterId.name}_enabled", true)

private fun saveCharacterEnabled(context: Context, characterId: StrikeCharacterId, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("strike_${characterId.name}_enabled", value).apply()
}

private fun loadQuoteEnabled(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("strike_quote_enabled", false)

private fun saveQuoteEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("strike_quote_enabled", value).apply()
}

internal object StrikeAnimationSettingsState {
    var animationEnabled by mutableStateOf(true)
    var character1Enabled by mutableStateOf(true)
    var character2Enabled by mutableStateOf(true)
    var quoteEnabled by mutableStateOf(false)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            animationEnabled = loadStrikeAnimationEnabled(context)
            character1Enabled = loadCharacterEnabled(context, StrikeCharacterId.CHARACTER_1)
            character2Enabled = loadCharacterEnabled(context, StrikeCharacterId.CHARACTER_2)
            quoteEnabled = loadQuoteEnabled(context)
            loaded = true
        }
    }

    fun setAnimationEnabled(context: Context, value: Boolean) {
        animationEnabled = value
        saveStrikeAnimationEnabled(context, value)
    }

    fun setCharacterEnabled(context: Context, characterId: StrikeCharacterId, value: Boolean) {
        when (characterId) {
            StrikeCharacterId.CHARACTER_1 -> character1Enabled = value
            StrikeCharacterId.CHARACTER_2 -> character2Enabled = value
        }
        saveCharacterEnabled(context, characterId, value)
    }

    fun setQuoteEnabled(context: Context, value: Boolean) {
        quoteEnabled = value
        saveQuoteEnabled(context, value)
    }

    fun isCharacterEnabled(characterId: StrikeCharacterId): Boolean = when (characterId) {
        StrikeCharacterId.CHARACTER_1 -> character1Enabled
        StrikeCharacterId.CHARACTER_2 -> character2Enabled
    }
}

/* ---------------- strike quotes (multiple, each with optional mp3) ---------------- */

internal data class StrikeQuote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val mp3ResourceName: String = ""
)

private fun loadStrikeQuotes(context: Context): List<StrikeQuote> = runCatching {
    val raw = context.getSharedPreferences("strike_quotes", Context.MODE_PRIVATE).getString("quotes", "[]") ?: "[]"
    val array = org.json.JSONArray(raw)
    buildList {
        repeat(array.length()) { i ->
            val o = array.getJSONObject(i)
            add(
                StrikeQuote(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    text = o.optString("text"),
                    mp3ResourceName = o.optString("mp3ResourceName", "")
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveStrikeQuotes(context: Context, quotes: List<StrikeQuote>) {
    val array = org.json.JSONArray()
    quotes.forEach { q ->
        array.put(
            org.json.JSONObject()
                .put("id", q.id)
                .put("text", q.text)
                .put("mp3ResourceName", q.mp3ResourceName)
        )
    }
    context.getSharedPreferences("strike_quotes", Context.MODE_PRIVATE).edit().putString("quotes", array.toString()).apply()
}

internal object StrikeQuoteState {
    var quotes by mutableStateOf<List<StrikeQuote>>(emptyList())
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            quotes = loadStrikeQuotes(context)
            loaded = true
        }
    }

    fun persist(context: Context, updated: List<StrikeQuote>) {
        quotes = updated
        saveStrikeQuotes(context, updated)
    }
}
private enum class DigitTransitionStyle { FLIP, SLIDE, FADE_SCALE, BOUNCE, WAVE }

private val TopHalfShape = GenericShape { size, _ ->
    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height / 2f))
}
private val BottomHalfShape = GenericShape { size, _ ->
    addRect(androidx.compose.ui.geometry.Rect(0f, size.height / 2f, size.width, size.height))
}

private fun loadDigitTransitionStyle(context: Context): DigitTransitionStyle {
    val name = context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE)
        .getString("digit_transition_style", DigitTransitionStyle.FLIP.name)
    return DigitTransitionStyle.entries.firstOrNull { it.name == name } ?: DigitTransitionStyle.FLIP
}

private fun saveDigitTransitionStyle(context: Context, style: DigitTransitionStyle) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE)
        .edit().putString("digit_transition_style", style.name).apply()
}

private object DigitStyleState {
    var current by mutableStateOf(DigitTransitionStyle.FLIP)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            current = loadDigitTransitionStyle(context)
            loaded = true
        }
    }

    fun update(context: Context, style: DigitTransitionStyle) {
        current = style
        saveDigitTransitionStyle(context, style)
    }
}

// Timer box (digit size/spacing) settings previously only reloaded from prefs
// on orientation flip, so changing digit size in Settings didn't visibly
// resize a currently-open timer/clock until the screen rotated. This holds
// the live value so any open screen recomposes immediately on change.
internal object TimerBoxLiveSettingsState {
    private val portraitByScope = mutableStateMapOf<String, TimerBoxSettings>()
    private val landscapeByScope = mutableStateMapOf<String, TimerBoxSettings>()

    fun get(context: Context, scope: String, isLandscape: Boolean): TimerBoxSettings {
        val map = if (isLandscape) landscapeByScope else portraitByScope
        return map[scope] ?: loadTimerBoxSettings(context, isLandscape, scope).also { map[scope] = it }
    }

    fun update(scope: String, isLandscape: Boolean, settings: TimerBoxSettings) {
        val map = if (isLandscape) landscapeByScope else portraitByScope
        map[scope] = settings
    }
}

internal enum class ClockFace {
    CLASSIC, MINIMAL_PREMIUM, DARK_ELEGANT, GLASS_GLOSSY, NEON, DIGITAL_FUTURISTIC,
    CLEAN_PRODUCTIVITY, SOFT_STUDY, RETRO_DIGITAL, MODERN_DASHBOARD, FLIP_BOARD_INSPIRED,
    MONOCHROME, AMBIENT, STARLIGHT_PREMIUM
}

private fun clockFaceLabel(face: ClockFace): String = when (face) {
    ClockFace.CLASSIC -> "Default"
    ClockFace.MINIMAL_PREMIUM -> "Minimal Premium"
    ClockFace.DARK_ELEGANT -> "Dark Elegant"
    ClockFace.GLASS_GLOSSY -> "Glass"
    ClockFace.NEON -> "Neon"
    ClockFace.DIGITAL_FUTURISTIC -> "Futuristic"
    ClockFace.CLEAN_PRODUCTIVITY -> "Productivity"
    ClockFace.SOFT_STUDY -> "Soft Study"
    ClockFace.RETRO_DIGITAL -> "Retro"
    ClockFace.MODERN_DASHBOARD -> "Dashboard"
    ClockFace.FLIP_BOARD_INSPIRED -> "Flip Board"
    ClockFace.MONOCHROME -> "Mono B/W"
    ClockFace.AMBIENT -> "Ambient"
    ClockFace.STARLIGHT_PREMIUM -> "Starlight Premium"
}

private data class ClockFaceStyle(
    val screenBackground: Brush,
    val cardBackground: Color,
    val digitColor: Color,
    val cornerRadius: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val dividerColor: Color,
    val labelColor: Color,
    val glowColor: Color,
    val glowAlpha: Float,
    val splitDigitGapDp: Dp = 0.dp,
    val hasCutMask: Boolean = false
)

private fun clockFaceStyle(face: ClockFace): ClockFaceStyle = when (face) {
    ClockFace.CLASSIC -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(TimerBg, TimerBg)),
        cardBackground = TimerCardBg, digitColor = TimerDigit, cornerRadius = 34.dp,
        borderColor = Color.Transparent, borderWidth = 0.dp,
        dividerColor = Color.Black.copy(alpha = 0.75f), labelColor = Color.White.copy(alpha = 0.78f),
        glowColor = Color.Transparent, glowAlpha = 0f
    )
    ClockFace.MINIMAL_PREMIUM -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF121214), Color(0xFF1A1A1D))),
        cardBackground = Color(0xFF1C1C1F), digitColor = Color(0xFFF5F5F0), cornerRadius = 18.dp,
        borderColor = Color.White.copy(alpha = 0.06f), borderWidth = 1.dp,
        dividerColor = Color.Black.copy(alpha = 0.55f), labelColor = Color.White.copy(alpha = 0.45f),
        glowColor = Color.Transparent, glowAlpha = 0f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.DARK_ELEGANT -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF0A0A12), Color(0xFF14101F))),
        cardBackground = Color(0xFF17131F), digitColor = Color(0xFFE8D9B5), cornerRadius = 10.dp,
        borderColor = Color(0xFFE8D9B5).copy(alpha = 0.25f), borderWidth = 1.dp,
        dividerColor = Color.Black.copy(alpha = 0.55f), labelColor = Color(0xFFE8D9B5).copy(alpha = 0.55f),
        glowColor = Color(0xFFE8D9B5), glowAlpha = 0.12f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.GLASS_GLOSSY -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF1B2436), Color(0xFF0E141F))),
        cardBackground = Color.White.copy(alpha = 0.08f), digitColor = Color.White, cornerRadius = 28.dp,
        borderColor = Color.White.copy(alpha = 0.28f), borderWidth = 1.2.dp,
        dividerColor = Color(0xFF11161F), labelColor = Color.White.copy(alpha = 0.7f),
        glowColor = Color.White, glowAlpha = 0.10f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.NEON -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF07050F), Color(0xFF120A1F))),
        cardBackground = Color(0xFF0D0716), digitColor = Color(0xFF64FFDA), cornerRadius = 14.dp,
        borderColor = Color(0xFF64FFDA).copy(alpha = 0.7f), borderWidth = 1.4.dp,
        dividerColor = Color(0xFF0D0716), labelColor = Color(0xFFBB86FC),
        glowColor = Color(0xFF64FFDA), glowAlpha = 0.35f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.DIGITAL_FUTURISTIC -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF03080C), Color(0xFF061620))),
        cardBackground = Color(0xFF071319), digitColor = Color(0xFF00E5FF), cornerRadius = 4.dp,
        borderColor = Color(0xFF00E5FF).copy(alpha = 0.45f), borderWidth = 1.dp,
        dividerColor = Color(0xFF071319), labelColor = Color(0xFF00E5FF).copy(alpha = 0.6f),
        glowColor = Color(0xFF00E5FF), glowAlpha = 0.18f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.CLEAN_PRODUCTIVITY -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFFF4F4F8), Color(0xFFE9E9F2))),
        cardBackground = Color.White, digitColor = Color(0xFF1A1A1A), cornerRadius = 20.dp,
        borderColor = Color.Black.copy(alpha = 0.08f), borderWidth = 1.dp,
        dividerColor = Color.White, labelColor = Color(0xFF6B6B76),
        glowColor = Color.Transparent, glowAlpha = 0f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.CLEAN_PRODUCTIVITY -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFFF4F4F8), Color(0xFFE9E9F2))),
        cardBackground = Color.White, digitColor = Color(0xFF1A1A1A), cornerRadius = 20.dp,
        borderColor = Color.Black.copy(alpha = 0.08f), borderWidth = 1.dp,
        dividerColor = Color.White, labelColor = Color(0xFF6B6B76),
        glowColor = Color.Transparent, glowAlpha = 0f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.SOFT_STUDY -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF15181C), Color(0xFF1B2420))),
        cardBackground = Color(0xFF1E2621), digitColor = Color(0xFFBFE3D0), cornerRadius = 24.dp,
        borderColor = Color(0xFFBFE3D0).copy(alpha = 0.14f), borderWidth = 1.dp,
        dividerColor = Color(0xFF1E2621), labelColor = Color(0xFFBFE3D0).copy(alpha = 0.55f),
        glowColor = Color(0xFF6FCF97), glowAlpha = 0.08f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.RETRO_DIGITAL -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF1A0F08), Color(0xFF2A150A))),
        cardBackground = Color(0xFF241207), digitColor = Color(0xFFFF8C32), cornerRadius = 6.dp,
        borderColor = Color(0xFFFF8C32).copy(alpha = 0.4f), borderWidth = 1.dp,
        dividerColor = Color(0xFF241207), labelColor = Color(0xFFFF8C32).copy(alpha = 0.6f),
        glowColor = Color(0xFFFF8C32), glowAlpha = 0.15f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.MODERN_DASHBOARD -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF10131C), Color(0xFF171B27))),
        cardBackground = Color(0xFF1D212E), digitColor = Color.White, cornerRadius = 16.dp,
        borderColor = Color(0xFF3B82F6).copy(alpha = 0.35f), borderWidth = 1.2.dp,
        dividerColor = Color(0xFF1D212E), labelColor = Color(0xFF3B82F6),
        glowColor = Color(0xFF3B82F6), glowAlpha = 0.12f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.FLIP_BOARD_INSPIRED -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color(0xFF06060A), Color(0xFF0C0C12))),
        cardBackground = Color(0xFF17171D), digitColor = Color(0xFFF2F2F2), cornerRadius = 8.dp,
        borderColor = Color.Black, borderWidth = 2.dp,
        dividerColor = Color(0xFF17171D), labelColor = Color.White.copy(alpha = 0.5f),
        glowColor = Color.Transparent, glowAlpha = 0f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.MONOCHROME -> ClockFaceStyle(
        screenBackground = Brush.linearGradient(listOf(Color.Black, Color(0xFF0A0A0A))),
        cardBackground = Color(0xFF0F0F0F), digitColor = Color.White, cornerRadius = 0.dp,
        borderColor = Color.White.copy(alpha = 0.9f), borderWidth = 1.5.dp,
        dividerColor = Color(0xFF0F0F0F), labelColor = Color.White.copy(alpha = 0.6f),
        glowColor = Color.Transparent, glowAlpha = 0f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.AMBIENT -> ClockFaceStyle(
        screenBackground = Brush.radialGradient(listOf(Color(0xFF1E2A3A), Color(0xFF090D14))),
        cardBackground = Color(0xFF141B26).copy(alpha = 0.8f), digitColor = Color(0xFFCFE0F5), cornerRadius = 40.dp,
        borderColor = Color(0xFFCFE0F5).copy(alpha = 0.1f), borderWidth = 1.dp,
        dividerColor = Color(0xFF101722), labelColor = Color(0xFFCFE0F5).copy(alpha = 0.5f),
        glowColor = Color(0xFF6FA8DC), glowAlpha = 0.2f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
    ClockFace.STARLIGHT_PREMIUM -> ClockFaceStyle(
        screenBackground = Brush.radialGradient(listOf(Color(0xFF141B33), Color(0xFF05070F))),
        cardBackground = Color(0xFF10142A).copy(alpha = 0.85f), digitColor = Color(0xFFF4E9D8), cornerRadius = 26.dp,
        borderColor = Color(0xFFFFD98A).copy(alpha = 0.24f), borderWidth = 1.2.dp,
        dividerColor = Color(0xFF0B0E20), labelColor = Color(0xFFFFD98A).copy(alpha = 0.7f),
        glowColor = Color(0xFFFFD98A), glowAlpha = 0.2f,
        splitDigitGapDp = 7.dp,
        hasCutMask = true
    )
}

private data class PremiumStarSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val phase: Float
)

@Composable
private fun PremiumStarfieldBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = kotlin.random.Random(7)
        List(80) {
            PremiumStarSpec(
                xFrac = random.nextFloat(),
                yFrac = random.nextFloat(),
                radius = 1f + random.nextFloat() * 2.4f,
                driftX = (random.nextFloat() - 0.5f) * 0.5f,
                driftY = (random.nextFloat() - 0.5f) * 0.5f,
                phase = random.nextFloat() * 6.283f
            )
        }
    }
    val infinite = rememberInfiniteTransition(label = "premiumStarfield")
    val time by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "premiumStarfieldTime"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        stars.forEach { star ->
            val x = (((star.xFrac + star.driftX * time) % 1f + 1f) % 1f) * w
            val y = (((star.yFrac + star.driftY * time) % 1f + 1f) % 1f) * h
            val twinkle = 0.3f + 0.7f * ((kotlin.math.sin(time * 6.283f * 3f + star.phase) + 1f) / 2f)
            drawCircle(color = Color.White.copy(alpha = twinkle), radius = star.radius, center = Offset(x, y))
            drawCircle(
                color = Color(0xFFFFD98A).copy(alpha = twinkle * 0.35f),
                radius = star.radius * 2.4f,
                center = Offset(x, y)
            )
        }
    }
}

private fun loadClockFace(context: Context): ClockFace {
    val name = context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE)
        .getString("clock_face", ClockFace.CLASSIC.name)
    return ClockFace.entries.firstOrNull { it.name == name } ?: ClockFace.CLASSIC
}

private fun saveClockFace(context: Context, face: ClockFace) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putString("clock_face", face.name).apply()
}

internal object ClockFaceState {
    var current by mutableStateOf(ClockFace.CLASSIC)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            current = loadClockFace(context)
            loaded = true
        }
    }

    fun update(context: Context, face: ClockFace) {
        current = face
        saveClockFace(context, face)
    }
}

private fun loadFloatingTimerPopupEnabled(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("floating_popup_enabled", false)

private fun saveFloatingTimerPopupEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("floating_popup_enabled", value).apply()
}

internal object FloatingPopupSettingsState {
    var enabled by mutableStateOf(false)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            enabled = loadFloatingTimerPopupEnabled(context)
            loaded = true
        }
    }

    fun update(context: Context, value: Boolean) {
        enabled = value
        saveFloatingTimerPopupEnabled(context, value)
    }
}

private fun loadFloatingPopupLabelEnabled(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("floating_popup_label_enabled", false)

private fun saveFloatingPopupLabelEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("floating_popup_label_enabled", value).apply()
}

internal object FloatingPopupLabelSettingsState {
    var enabled by mutableStateOf(false)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            enabled = loadFloatingPopupLabelEnabled(context)
            loaded = true
        }
    }

    fun update(context: Context, value: Boolean) {
        enabled = value
        saveFloatingPopupLabelEnabled(context, value)
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
                    startedAtMillis = o.optLong("startedAtMillis", 0L),
                    popupEnabled = o.optBoolean("popupEnabled", true)
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
                .put("popupEnabled", s.popupEnabled)
        )
    }
    context.getSharedPreferences("study_subjects", Context.MODE_PRIVATE).edit().putString("subjects", array.toString()).apply()
}

internal fun StudySubject.currentElapsedMillis(nowMillis: Long): Long =
    accumulatedMillis + if (isRunning) (nowMillis - startedAtMillis).coerceAtLeast(0L) else 0L

internal object StudyTimerState {
    var subjects by mutableStateOf<List<StudySubject>>(emptyList())
    var pendingCelebration by mutableStateOf<Pair<String, Int>?>(null)
    var pendingCelebrationCharacterId by mutableStateOf<StrikeCharacterId?>(null)
    var pendingCelebrationQuoteId by mutableStateOf<String?>(null)
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

    // Strike counting/detection is untouched below — only the resulting
    // presentation (which character/quote, or nothing) is decided here.
    fun checkStrikes(context: Context, nowMillis: Long) {
        StrikeAnimationSettingsState.ensureLoaded(context)
        StrikeQuoteState.ensureLoaded(context)
        subjects.forEach { s ->
            val currentCount = strikeCountForElapsed(s.currentElapsedMillis(nowMillis))
            val priorCount = previousStrikeCounts[s.id] ?: currentCount
            if (currentCount > priorCount && pendingCelebration == null) {
                if (StrikeAnimationSettingsState.animationEnabled) {
                    val enabledCharacterIds = StrikeCharacters
                        .filter { StrikeAnimationSettingsState.isCharacterEnabled(it.id) }
                        .map { it.id }
                    val quoteAvailable = StrikeAnimationSettingsState.quoteEnabled && StrikeQuoteState.quotes.isNotEmpty()

                    val pool = buildList<Any> {
                        addAll(enabledCharacterIds)
                        if (quoteAvailable) add("QUOTE")
                    }
                    if (pool.isNotEmpty()) {
                        pendingCelebration = s.id to currentCount
                        when (val picked = pool.random()) {
                            "QUOTE" -> {
                                pendingCelebrationQuoteId = StrikeQuoteState.quotes.random().id
                                pendingCelebrationCharacterId = null
                            }
                            is StrikeCharacterId -> {
                                pendingCelebrationCharacterId = picked
                                pendingCelebrationQuoteId = null
                            }
                        }
                    }
                }
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

private enum class CharacterReaction { DANCE, CLAP, JUMP, SURPRISED, WAVE }

private fun reactionForStrike(strikeCount: Int): CharacterReaction {
    val reactions = CharacterReaction.entries
    return reactions[(strikeCount - 1).coerceAtLeast(0) % reactions.size]
}

@Composable
private fun StudyCelebrationHost() {
    StudyTimerState.pendingCelebration?.let { (_, count) ->
        val quoteId = StudyTimerState.pendingCelebrationQuoteId
        if (quoteId != null) {
            val quote = StrikeQuoteState.quotes.firstOrNull { it.id == quoteId }
            if (quote != null) {
                StrikeQuoteOverlay(quote = quote) {
                    StudyTimerState.pendingCelebration = null
                    StudyTimerState.pendingCelebrationQuoteId = null
                }
            } else {
                StudyTimerState.pendingCelebration = null
                StudyTimerState.pendingCelebrationQuoteId = null
            }
        } else {
            val characterId = StudyTimerState.pendingCelebrationCharacterId ?: StrikeCharacters.first().id
            StudyStrikeCelebrationOverlay(strikeCount = count, characterId = characterId) {
                StudyTimerState.pendingCelebration = null
                StudyTimerState.pendingCelebrationCharacterId = null
            }
        }
    }
}

/* ---------------- responsive timer box settings ---------------- */

internal data class TimerBoxSettings(
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

internal enum class StrikeCharacterId { CHARACTER_1, CHARACTER_2 }

internal data class StrikeCharacterConfig(
    val id: StrikeCharacterId,
    val name: String,
    val mp3ResourcePrefix: String,
    val messages: List<String>
)

private val StrikeCharacter1Messages = listOf(
    "স্বপ্নের চেয়ে তোমার চেষ্টার জোর যেন সবসময় বেশি হয়। ⭐",
    "দারুণ! এভাবেই এগিয়ে যা। 💪",
    "অসাধারণ পরিশ্রম! গর্বিত তোর জন্য।",
    "থামিস না, তুই একদম ঠিক পথে আছিস! 🚀",
    "চমৎকার! আরেকটা স্ট্রাইক তোর ঝুলিতে। 🏆"
)

private val StrikeCharacter2Messages = listOf(
    "নিঃশব্দে পথ চলো, ফলাফলই কথা বলবে। 🖤",
    "ক্লান্তি সাময়িক, শক্তি চিরস্থায়ী। 🔥",
    "একজন প্রকৃত যোদ্ধা থামে না। 🐦",
    "আরেকটা স্ট্রাইক—সিংহাসনের আরও কাছে। 👑",
    "নিয়ম নিজে তৈরি করো। 💀"
)

// Character 2-এর mp3 ফাইল res/raw ফোল্ডারে "strike_c2_1", "strike_c2_2"... নামে যোগ করলেই এখানে auto-detect হবে।
// ভবিষ্যতে নতুন character যোগ করতে হলে শুধু এই লিস্টে নতুন StrikeCharacterConfig entry যোগ করলেই হবে।
internal val StrikeCharacters = listOf(
    StrikeCharacterConfig(
        id = StrikeCharacterId.CHARACTER_1,
        name = "Character 1",
        mp3ResourcePrefix = "strike_",
        messages = StrikeCharacter1Messages
    ),
    StrikeCharacterConfig(
        id = StrikeCharacterId.CHARACTER_2,
        name = "Character 2",
        mp3ResourcePrefix = "strike_c2_",
        messages = StrikeCharacter2Messages
    )
)

private fun strikeMp3Count(context: Context, prefix: String): Int {
    var index = 1
    while (context.resources.getIdentifier("$prefix$index", "raw", context.packageName) != 0) index++
    return index - 1
}

private fun studyStrikeSoundResName(context: Context, prefix: String, strikeIndex: Int): String? {
    val available = strikeMp3Count(context, prefix)
    if (available <= 0) return null
    val cyclicIndex = ((strikeIndex - 1) % available) + 1
    return "$prefix$cyclicIndex"
}


private object TimerSoundPlayer {
    private var player: android.media.MediaPlayer? = null

    /** Plays a raw sound. onComplete fires when playback finishes naturally, or immediately if the resource is missing/fails. */
    fun play(context: Context, resName: String, onComplete: (() -> Unit)? = null) {
        stop()
        var started = false
        runCatching {
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId != 0) {
                val afd = context.resources.openRawResourceFd(resId)
                player = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    if (afd != null) {
                        afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    } else {
                        setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
                    }
                    setOnCompletionListener { mp ->
                        mp.release()
                        if (player == mp) player = null
                        onComplete?.invoke()
                    }
                    setOnErrorListener { mp, _, _ ->
                        mp.release()
                        if (player == mp) player = null
                        onComplete?.invoke()
                        true
                    }
                    prepare()
                    setVolume(1f, 1f)
                    start()
                }
                started = player != null
            }
        }
        if (!started) onComplete?.invoke()
    }

    fun stop() {
        runCatching {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        }
        player = null
    }
}

private fun playStrikeSound(context: Context, prefix: String, strikeIndex: Int, onComplete: (() -> Unit)? = null) {
    val resName = studyStrikeSoundResName(context, prefix, strikeIndex)
    if (resName != null) {
        TimerSoundPlayer.play(context, resName, onComplete)
    } else {
        onComplete?.invoke()
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

private fun DrawScope.drawSparkleStar(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(
            center.x + radius * 0.12f, center.y - radius * 0.12f,
            center.x + radius * 0.88f, center.y - radius * 0.12f,
            center.x + radius, center.y
        )
        cubicTo(
            center.x + radius * 0.12f, center.y + radius * 0.12f,
            center.x + radius * 0.12f, center.y + radius * 0.88f,
            center.x, center.y + radius
        )
        cubicTo(
            center.x - radius * 0.12f, center.y + radius * 0.12f,
            center.x - radius * 0.88f, center.y + radius * 0.12f,
            center.x - radius, center.y
        )
        cubicTo(
            center.x - radius * 0.12f, center.y - radius * 0.12f,
            center.x - radius * 0.12f, center.y - radius * 0.88f,
            center.x, center.y - radius
        )
        close()
    }
    drawPath(path, color = color.copy(alpha = alpha))
}

@Composable
private fun ChubbyCelebrationCharacter(reaction: CharacterReaction) {
    val infinite = rememberInfiniteTransition(label = "animeGirlCharacter")
    val bounce by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (reaction == CharacterReaction.JUMP) 260 else 420, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val sway by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )
    val armFlap by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(220, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "armFlap"
    )
    val blink by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                1f at 0; 1f at 2300; 0.1f at 2400; 1f at 2500; 1f at 2600
            },
            RepeatMode.Restart
        ),
        label = "blink"
    )
    val twinkle by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "twinkle"
    )
    val twinkle2 by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(820, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "twinkle2"
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(reaction) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 180f))
    }

    // Secondary motion: hair/cloth lag a beat behind the body so movement
    // reads as organic instead of every part moving in lockstep.
    val hairLag by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hairLag"
    )
    // Squash-stretch tied to the same bounce cycle: stretch on the way up,
    // squash on landing — this alone kills most of the "robotic" feel.
    val stretch by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(if (reaction == CharacterReaction.JUMP) 260 else 420, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "stretch"
    )

    val bounceAmount = when (reaction) {
        CharacterReaction.JUMP -> bounce * 34f
        CharacterReaction.CLAP -> bounce * 6f
        else -> bounce * 16f
    }
    val swayAmount = if (reaction == CharacterReaction.DANCE) sway * 18f else sway * 4f
    val tilt = if (reaction == CharacterReaction.DANCE) sway * 8f else sway * 2f

    Canvas(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                translationX = swayAmount
                translationY = (-bounceAmount) + ((1f - entrance.value) * 80f)
                rotationZ = tilt
                // Rotate around the feet, not the geometric center — otherwise
                // a "tilt" reads as the whole body sliding sideways.
                transformOrigin = TransformOrigin(0.5f, 0.92f)
                val squash = 1f + (bounceAmount / 40f) * (stretch - 1f)
                scaleX = (0.5f + entrance.value * 0.5f) * (2f - squash)
                scaleY = (0.5f + entrance.value * 0.5f) * squash
                alpha = entrance.value
            }
    ) {
        val w = size.width
        val h = size.height
        val skinColor = Color(0xFFFFE3D6)
        val hairColor = Color(0xFFFFC2D1)
        val hairShadow = Color(0xFFFFA8C0)
        val sweaterColor = Color(0xFFC9BEEB)
        val sweaterShadow = Color(0xFFAE9EDD)
        val sleeveColor = Color(0xFFB6E8D3)
        val hemColor = Color(0xFFB6E8D3)
        val shoeColor = Color(0xFF5B4A47)
        val blushColor = Color(0xFFFF9FB5)
        val darkLine = Color(0xFF4A3A3E)
        val legSwing = sway * 5f
        val legLift = if (reaction == CharacterReaction.JUMP) bounce * 10f else 0f

        // twinkling sparkle stars around her
        drawSparkleStar(Offset(w * 0.14f, h * 0.18f), w * 0.05f, Color(0xFFFFF3C4), twinkle)
        drawSparkleStar(Offset(w * 0.86f, h * 0.30f), w * 0.035f, Color(0xFFFFF3C4), twinkle2)
        drawSparkleStar(Offset(w * 0.80f, h * 0.10f), w * 0.028f, Color.White, twinkle)
        drawSparkleStar(Offset(w * 0.20f, h * 0.72f), w * 0.022f, Color.White, twinkle2)

        // legs / socks / shoes
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.37f - legSwing * 0.01f * w, h * 0.80f - legLift),
            size = Size(w * 0.10f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.04f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.55f + legSwing * 0.01f * w, h * 0.80f - legLift),
            size = Size(w * 0.10f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.04f)
        )
        drawOval(color = shoeColor, topLeft = Offset(w * 0.34f - legSwing * 0.01f * w, h * 0.90f - legLift), size = Size(w * 0.15f, h * 0.065f))
        drawOval(color = shoeColor, topLeft = Offset(w * 0.53f + legSwing * 0.01f * w, h * 0.90f - legLift), size = Size(w * 0.15f, h * 0.065f))

        // arms — reaction dependent (mint sleeve + skin hand)
        when (reaction) {
            CharacterReaction.CLAP -> {
                val clapOffset = (1f - armFlap) * w * 0.09f
                drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.5f - w * 0.055f - clapOffset, h * 0.46f), size = Size(w * 0.10f, h * 0.18f), cornerRadius = CornerRadius(w * 0.045f))
                drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.5f - w * 0.045f + clapOffset, h * 0.46f), size = Size(w * 0.10f, h * 0.18f), cornerRadius = CornerRadius(w * 0.045f))
                drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.5f - clapOffset, h * 0.45f))
                drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.5f + clapOffset, h * 0.45f))
            }
            CharacterReaction.WAVE -> {
                drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.10f, h * 0.50f), size = Size(w * 0.10f, h * 0.20f), cornerRadius = CornerRadius(w * 0.045f))
                drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.15f, h * 0.71f))
                val waveAngle = armFlap * 30f - 15f
                rotate(degrees = waveAngle, pivot = Offset(w * 0.81f, h * 0.43f)) {
                    drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.77f, h * 0.22f), size = Size(w * 0.10f, h * 0.22f), cornerRadius = CornerRadius(w * 0.045f))
                    drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.825f, h * 0.22f))
                }
            }
            else -> {
                drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.09f, h * 0.48f - legSwing * 0.008f * h), size = Size(w * 0.10f, h * 0.22f), cornerRadius = CornerRadius(w * 0.045f))
                drawRoundRect(color = sleeveColor, topLeft = Offset(w * 0.81f, h * 0.48f + legSwing * 0.008f * h), size = Size(w * 0.10f, h * 0.22f), cornerRadius = CornerRadius(w * 0.045f))
                drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.14f, h * 0.72f - legSwing * 0.008f * h))
                drawCircle(color = skinColor, radius = w * 0.05f, center = Offset(w * 0.86f, h * 0.72f + legSwing * 0.008f * h))
            }
        }

        // sweater body + mint hem
        drawRoundRect(
            color = sweaterShadow,
            topLeft = Offset(w * 0.28f, h * 0.235f),
            size = Size(w * 0.44f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.13f)
        )
        drawRoundRect(
            color = sweaterColor,
            topLeft = Offset(w * 0.29f, h * 0.22f),
            size = Size(w * 0.42f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.13f)
        )
        drawRoundRect(
            color = hemColor,
            topLeft = Offset(w * 0.29f, h * 0.66f),
            size = Size(w * 0.42f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.05f)
        )

        // long flowing hair behind head
        val headCenter = Offset(w * 0.5f, h * 0.32f)
        val headRadius = w * 0.235f
        val hairSwing = hairLag * headRadius * 0.18f
        drawPath(
            path = Path().apply {
                moveTo(headCenter.x - headRadius * 0.85f, headCenter.y - headRadius * 0.3f)
                cubicTo(
                    headCenter.x - headRadius * 1.35f + hairSwing, headCenter.y + headRadius * 0.6f,
                    headCenter.x - headRadius * 1.15f + hairSwing, h * 0.78f,
                    headCenter.x - headRadius * 0.55f, h * 0.86f
                )
                cubicTo(
                    headCenter.x - headRadius * 0.75f, h * 0.55f,
                    headCenter.x - headRadius * 0.95f, headCenter.y + headRadius * 0.2f,
                    headCenter.x - headRadius * 0.55f, headCenter.y - headRadius * 0.55f
                )
                close()
            },
            color = hairColor
        )
        drawPath(
            path = Path().apply {
                moveTo(headCenter.x + headRadius * 0.85f, headCenter.y - headRadius * 0.3f)
                cubicTo(
                    headCenter.x + headRadius * 1.35f + hairSwing, headCenter.y + headRadius * 0.6f,
                    headCenter.x + headRadius * 1.15f + hairSwing, h * 0.78f,
                    headCenter.x + headRadius * 0.55f, h * 0.86f
                )
                cubicTo(
                    headCenter.x + headRadius * 0.75f, h * 0.55f,
                    headCenter.x + headRadius * 0.95f, headCenter.y + headRadius * 0.2f,
                    headCenter.x + headRadius * 0.55f, headCenter.y - headRadius * 0.55f
                )
                close()
            },
            color = hairShadow
        )

        // head
        drawCircle(color = skinColor, radius = headRadius, center = headCenter)

        // hair top / bangs
        drawArc(
            color = hairColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(headCenter.x - headRadius, headCenter.y - headRadius * 1.05f),
            size = Size(headRadius * 2f, headRadius * 1.5f)
        )
        drawPath(
            path = Path().apply {
                moveTo(headCenter.x - headRadius * 0.9f, headCenter.y - headRadius * 0.1f)
                lineTo(headCenter.x - headRadius * 0.55f, headCenter.y - headRadius * 0.55f)
                lineTo(headCenter.x - headRadius * 0.25f, headCenter.y - headRadius * 0.12f)
                lineTo(headCenter.x, headCenter.y - headRadius * 0.6f)
                lineTo(headCenter.x + headRadius * 0.25f, headCenter.y - headRadius * 0.12f)
                lineTo(headCenter.x + headRadius * 0.55f, headCenter.y - headRadius * 0.55f)
                lineTo(headCenter.x + headRadius * 0.9f, headCenter.y - headRadius * 0.1f)
                lineTo(headCenter.x + headRadius * 0.9f, headCenter.y + headRadius * 0.05f)
                lineTo(headCenter.x - headRadius * 0.9f, headCenter.y + headRadius * 0.05f)
                close()
            },
            color = hairColor
        )

        // blush
        drawCircle(color = blushColor.copy(alpha = 0.55f), radius = headRadius * 0.22f, center = Offset(headCenter.x - headRadius * 0.55f, headCenter.y + headRadius * 0.28f))
        drawCircle(color = blushColor.copy(alpha = 0.55f), radius = headRadius * 0.22f, center = Offset(headCenter.x + headRadius * 0.55f, headCenter.y + headRadius * 0.28f))

        // big sparkly eyes
        val eyeHeight = if (reaction == CharacterReaction.SURPRISED) headRadius * 0.62f else headRadius * 0.42f * blink
        val eyeWidth = headRadius * 0.32f
        val leftEyeCenter = Offset(headCenter.x - headRadius * 0.42f, headCenter.y + headRadius * 0.06f)
        val rightEyeCenter = Offset(headCenter.x + headRadius * 0.42f, headCenter.y + headRadius * 0.06f)
        listOf(leftEyeCenter, rightEyeCenter).forEach { eyeCenter ->
            drawOval(
                color = darkLine,
                topLeft = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - eyeHeight / 2f),
                size = Size(eyeWidth, eyeHeight)
            )
            drawOval(
                color = Color(0xFF8A5A46),
                topLeft = Offset(eyeCenter.x - eyeWidth * 0.36f, eyeCenter.y - eyeHeight * 0.36f),
                size = Size(eyeWidth * 0.72f, eyeHeight * 0.72f)
            )
            drawCircle(color = Color.White, radius = eyeWidth * 0.16f, center = Offset(eyeCenter.x - eyeWidth * 0.14f, eyeCenter.y - eyeHeight * 0.22f))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = eyeWidth * 0.08f, center = Offset(eyeCenter.x + eyeWidth * 0.16f, eyeCenter.y + eyeHeight * 0.14f))
        }

        // mouth
        if (reaction == CharacterReaction.SURPRISED) {
            drawOval(color = darkLine, topLeft = Offset(headCenter.x - headRadius * 0.06f, headCenter.y + headRadius * 0.45f), size = Size(headRadius * 0.12f, headRadius * 0.14f))
        } else {
            drawArc(
                color = darkLine,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(headCenter.x - headRadius * 0.20f, headCenter.y + headRadius * 0.32f),
                size = Size(headRadius * 0.40f, headRadius * 0.24f),
                style = Stroke(width = w * 0.014f, cap = StrokeCap.Round)
            )
        }
    }
}
@Composable
private fun ThroneDarkCelebrationCharacter(reaction: CharacterReaction) {
    val infinite = rememberInfiniteTransition(label = "throneDarkCharacter")
    val glow by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eyeGlow"
    )
    val crowFlap by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crowFlap"
    )
    val sway by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloakSway"
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(reaction) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f))
    }

    Canvas(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                translationY = (1f - entrance.value) * 80f
                scaleX = 0.5f + entrance.value * 0.5f
                scaleY = 0.5f + entrance.value * 0.5f
                alpha = entrance.value
            }
    ) {
        val w = size.width
        val h = size.height
        val throneColor = Color(0xFF1C1418)
        val throneShadow = Color(0xFF0B0708)
        val cloakColor = Color(0xFF15111A)
        val cloakTrim = Color(0xFF7A0E0E)
        val skinColor = Color(0xFFE9C4A8)
        val hairColor = Color(0xFF0E0B10)
        val eyeGlowColor = Color(0xFFE23B3B)
        val crowColor = Color(0xFF0A0A0C)

        drawRoundRect(
            color = throneShadow,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.72f, h * 0.78f),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        drawRoundRect(
            color = throneColor,
            topLeft = Offset(w * 0.17f, h * 0.20f),
            size = Size(w * 0.66f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        listOf(0.20f, 0.34f, 0.50f, 0.66f, 0.80f).forEach { xf ->
            drawPath(
                path = Path().apply {
                    moveTo(w * xf - w * 0.035f, h * 0.20f)
                    lineTo(w * xf, h * 0.06f)
                    lineTo(w * xf + w * 0.035f, h * 0.20f)
                    close()
                },
                color = throneColor
            )
        }

        val crowLift = crowFlap * h * 0.03f
        listOf(Offset(w * 0.12f, h * 0.10f - crowLift), Offset(w * 0.86f, h * 0.16f + crowLift), Offset(w * 0.72f, h * 0.04f - crowLift)).forEach { c ->
            drawPath(
                path = Path().apply {
                    moveTo(c.x - w * 0.05f, c.y)
                    quadraticTo(c.x, c.y - h * 0.03f * (1f + crowFlap), c.x + w * 0.05f, c.y)
                    quadraticTo(c.x, c.y + h * 0.008f, c.x - w * 0.05f, c.y)
                    close()
                },
                color = crowColor
            )
        }

        drawRoundRect(color = Color(0xFFCFCFCF), topLeft = Offset(w * 0.40f, h * 0.82f), size = Size(w * 0.08f, h * 0.08f), cornerRadius = CornerRadius(w * 0.02f))
        drawRoundRect(color = Color(0xFFCFCFCF), topLeft = Offset(w * 0.52f, h * 0.82f), size = Size(w * 0.08f, h * 0.08f), cornerRadius = CornerRadius(w * 0.02f))
        drawRoundRect(color = Color(0xFFB8C4CC), topLeft = Offset(w * 0.80f, h * 0.28f), size = Size(w * 0.025f, h * 0.42f), cornerRadius = CornerRadius(w * 0.01f))
        drawRoundRect(color = Color(0xFF8A2020), topLeft = Offset(w * 0.775f, h * 0.68f), size = Size(w * 0.08f, h * 0.02f), cornerRadius = CornerRadius(w * 0.008f))
        drawRoundRect(color = Color(0xFF1A1010), topLeft = Offset(w * 0.798f, h * 0.70f), size = Size(w * 0.034f, h * 0.10f), cornerRadius = CornerRadius(w * 0.01f))

        // Cloak now lags the shoulders by a wider margin at the hem than at
        // the collar — cloth billows instead of moving as one rigid slab.
        val swayShift = sway * w * 0.012f
        val hemLag = sway * w * 0.028f
        drawPath(
            path = Path().apply {
                moveTo(w * 0.32f + swayShift, h * 0.42f)
                cubicTo(w * 0.24f + hemLag, h * 0.55f, w * 0.26f + hemLag, h * 0.78f, w * 0.36f, h * 0.86f)
                lineTo(w * 0.64f, h * 0.86f)
                cubicTo(w * 0.74f - hemLag, h * 0.78f, w * 0.76f - hemLag, h * 0.55f, w * 0.68f - swayShift, h * 0.42f)
                close()
            },
            color = cloakColor
        )
        drawArc(
            color = cloakTrim,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.30f, h * 0.36f),
            size = Size(w * 0.40f, h * 0.14f)
        )

        drawRoundRect(color = cloakColor, topLeft = Offset(w * 0.30f, h * 0.50f), size = Size(w * 0.16f, h * 0.20f), cornerRadius = CornerRadius(w * 0.05f))
        drawRoundRect(color = cloakColor, topLeft = Offset(w * 0.54f, h * 0.50f), size = Size(w * 0.16f, h * 0.20f), cornerRadius = CornerRadius(w * 0.05f))
        drawCircle(color = skinColor, radius = w * 0.035f, center = Offset(w * 0.40f, h * 0.66f))
        drawCircle(color = skinColor, radius = w * 0.035f, center = Offset(w * 0.60f, h * 0.66f))

        val headCenter = Offset(w * 0.5f, h * 0.32f)
        val headRadius = w * 0.17f
        drawCircle(color = skinColor, radius = headRadius, center = headCenter)

        drawPath(
            path = Path().apply {
                moveTo(headCenter.x - headRadius * 0.95f, headCenter.y - headRadius * 0.5f)
                cubicTo(
                    headCenter.x - headRadius * 1.3f, headCenter.y + headRadius * 0.8f,
                    headCenter.x - headRadius * 1.1f, h * 0.60f,
                    headCenter.x - headRadius * 0.6f, h * 0.62f
                )
                cubicTo(
                    headCenter.x - headRadius * 0.85f, h * 0.30f,
                    headCenter.x - headRadius * 0.95f, headCenter.y,
                    headCenter.x - headRadius * 0.6f, headCenter.y - headRadius * 0.7f
                )
                close()
            },
            color = hairColor
        )
        drawPath(
            path = Path().apply {
                moveTo(headCenter.x + headRadius * 0.95f, headCenter.y - headRadius * 0.5f)
                cubicTo(
                    headCenter.x + headRadius * 1.3f, headCenter.y + headRadius * 0.8f,
                    headCenter.x + headRadius * 1.1f, h * 0.60f,
                    headCenter.x + headRadius * 0.6f, h * 0.62f
                )
                cubicTo(
                    headCenter.x + headRadius * 0.85f, h * 0.30f,
                    headCenter.x + headRadius * 0.95f, headCenter.y,
                    headCenter.x + headRadius * 0.6f, headCenter.y - headRadius * 0.7f
                )
                close()
            },
            color = hairColor
        )
        drawArc(
            color = hairColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(headCenter.x - headRadius, headCenter.y - headRadius * 1.05f),
            size = Size(headRadius * 2f, headRadius * 1.4f)
        )

        val eyeWidth = headRadius * 0.34f
        val eyeHeight = headRadius * 0.14f
        val leftEye = Offset(headCenter.x - headRadius * 0.38f, headCenter.y + headRadius * 0.05f)
        val rightEye = Offset(headCenter.x + headRadius * 0.38f, headCenter.y + headRadius * 0.05f)
        listOf(leftEye, rightEye).forEach { eyeCenter ->
            drawOval(
                color = eyeGlowColor.copy(alpha = glow),
                topLeft = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - eyeHeight / 2f),
                size = Size(eyeWidth, eyeHeight)
            )
            drawCircle(color = Color.Black, radius = eyeHeight * 0.3f, center = eyeCenter)
        }

        drawLine(
            color = Color(0xFF4A3830),
            start = Offset(headCenter.x - headRadius * 0.14f, headCenter.y + headRadius * 0.42f),
            end = Offset(headCenter.x + headRadius * 0.14f, headCenter.y + headRadius * 0.42f),
            strokeWidth = w * 0.010f,
            cap = StrokeCap.Round
        )

        drawRoundRect(
            color = Color(0xFF9AA0A6),
            topLeft = Offset(headCenter.x - headRadius * 0.9f, headCenter.y - headRadius * 0.55f),
            size = Size(headRadius * 1.8f, headRadius * 0.28f),
            cornerRadius = CornerRadius(headRadius * 0.06f)
        )
    }
}

@Composable
private fun StudyStrikeCelebrationOverlay(strikeCount: Int, characterId: StrikeCharacterId, onFinished: () -> Unit) {
    StrikeCharacterOverlay(strikeCount = strikeCount, characterId = characterId, onFinished = onFinished)
}

@Composable
private fun StrikeCharacterOverlay(strikeCount: Int, characterId: StrikeCharacterId, onFinished: () -> Unit) {
    val context = LocalContext.current
    val config = remember(characterId) { StrikeCharacters.first { it.id == characterId } }
    val message = remember(strikeCount, characterId) { config.messages[(strikeCount - 1).coerceAtLeast(0) % config.messages.size] }
    val reaction = remember(strikeCount) { reactionForStrike(strikeCount) }
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    var finishedOnce by remember(strikeCount) { mutableStateOf(false) }
    val overlayScope = rememberCoroutineScope()
    val backgroundColor = if (characterId == StrikeCharacterId.CHARACTER_2) Color(0xFF120404) else Color(0xFF0B3D91)

    fun finishOnce() {
        if (finishedOnce) return
        finishedOnce = true
        overlayScope.launch {
            alpha.animateTo(0f, tween(220))
            TimerSoundPlayer.stop()
            onFinished()
        }
    }

    LaunchedEffect(strikeCount, characterId) {
        scale.snapTo(0.3f)
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(220))
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))

        // Character keeps reacting for exactly as long as the strike sound plays.
        // If there's no sound resource, fall back to a minimum visible duration.
        val soundFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
        playStrikeSound(context, config.mp3ResourcePrefix, strikeCount) { soundFinished.complete(Unit) }
        kotlinx.coroutines.withTimeoutOrNull(30000) { soundFinished.await() }

        finishOnce()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .graphicsLayer { this.alpha = alpha.value }
            .zIndex(500f)
            .pointerInput("strike-celebration-block") { detectTapGestures(onDoubleTap = { finishOnce() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        ) {
            when (characterId) {
                StrikeCharacterId.CHARACTER_1 -> ChubbyCelebrationCharacter(reaction = reaction)
                StrikeCharacterId.CHARACTER_2 -> ThroneDarkCelebrationCharacter(reaction = reaction)
            }
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
@Composable
private fun StrikeQuoteOverlay(quote: StrikeQuote, onFinished: () -> Unit) {
    val context = LocalContext.current
    val alpha = remember { Animatable(0f) }
    val textScale = remember { Animatable(0.85f) }
    var finishedOnce by remember(quote.id) { mutableStateOf(false) }
    val overlayScope = rememberCoroutineScope()

    fun finishOnce() {
        if (finishedOnce) return
        finishedOnce = true
        overlayScope.launch {
            alpha.animateTo(0f, tween(260))
            TimerSoundPlayer.stop()
            onFinished()
        }
    }

    LaunchedEffect(quote.id) {
        alpha.snapTo(0f)
        textScale.snapTo(0.85f)
        alpha.animateTo(1f, tween(320))
        textScale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f))

        if (quote.mp3ResourceName.isNotBlank()) {
            TimerSoundPlayer.play(context, quote.mp3ResourceName)
        }

        val readMillis = (quote.text.length * 55L).coerceIn(2600L, 9000L)
        delay(readMillis)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { this.alpha = alpha.value }
            .zIndex(500f)
            .pointerInput("strike-quote-block") { detectTapGestures(onDoubleTap = { finishOnce() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quote.text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .graphicsLayer { scaleX = textScale.value; scaleY = textScale.value }
        )
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
    val context = LocalContext.current
    LaunchedEffect(Unit) { DigitStyleState.ensureLoaded(context) }
    val style = DigitStyleState.current

    @Composable
    fun DigitGlyph(value: Char, modifier: Modifier = Modifier) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            // Split-Flap নিজেই প্রতি digit-কে top-half + bottom-half + flap overlay মিলিয়ে
            // একাধিকবার কম্পোজ করে; তার উপর extraBold-এর ৩টা extra offset Text যোগ হলে
            // একটা digit-এই প্রায় ১৬টা Text draw হয়ে যায় — এটাই কম শক্তিশালী device-এ
            // real-time/countdown কয়েক সেকেন্ডের জন্য "আটকে" থাকার (main-thread জ্যাম) আসল কারণ।
            // তাই Split-Flap-এ থাকলে এই extra faux-bold copy বাদ দেওয়া হলো — flap-এর নিজের
            // divider/shadow দিয়েই যথেষ্ট bold/premium ফিল আসে।
            if (extraBold) {
                Text(
                    value.toString(),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    modifier = Modifier.offset(x = 1.6.dp)
                )
                Text(
                    value.toString(),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    modifier = Modifier.offset(x = (-1.6).dp)
                )
                Text(
                    value.toString(),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    modifier = Modifier.offset(y = 0.8.dp)
                )
            }
            Text(value.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight)
        }
    }

    when (style) {
        DigitTransitionStyle.FLIP -> {
            var shown by remember { mutableStateOf(char) }
            val rotation = remember { Animatable(0f) }
            LaunchedEffect(char) {
                if (char != shown) {
                    rotation.animateTo(90f, tween(150, easing = FastOutLinearInEasing))
                    shown = char
                    rotation.animateTo(0f, tween(160, easing = LinearOutSlowInEasing))
                }
            }
            DigitGlyph(
                shown,
                modifier = Modifier.graphicsLayer {
                    rotationX = rotation.value
                    cameraDistance = 32f * density
                }
            )
        }

        DigitTransitionStyle.SLIDE -> {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(
                        tween(180)
                    )) togetherWith
                            (slideOutVertically(
                                tween(
                                    220,
                                    easing = FastOutSlowInEasing
                                )
                            ) { -it } + fadeOut(tween(140)))
                },
                label = "slideDigit"
            ) { value -> DigitGlyph(value) }
        }

        DigitTransitionStyle.FADE_SCALE -> {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.35f))
                },
                label = "fadeScaleDigit"
            ) { value -> DigitGlyph(value) }
        }

        DigitTransitionStyle.BOUNCE -> {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (scaleIn(
                        spring(dampingRatio = 0.45f, stiffness = 380f),
                        initialScale = 0.3f
                    ) + fadeIn(tween(120))) togetherWith
                            (scaleOut(tween(120), targetScale = 0.3f) + fadeOut(tween(100)))
                },
                label = "bounceDigit"
            ) { value -> DigitGlyph(value) }
        }

        DigitTransitionStyle.WAVE -> {
            var shown by remember { mutableStateOf(char) }
            val offsetY = remember { Animatable(0f) }
            val squash = remember { Animatable(1f) }
            val waveDensity = LocalDensity.current
            LaunchedEffect(char) {
                if (char != shown) {
                    val dropPx = with(waveDensity) { fontSize.toDp().toPx() * 0.32f }
                    launch { offsetY.animateTo(dropPx, tween(130, easing = FastOutLinearInEasing)) }
                    squash.animateTo(0.5f, tween(130, easing = FastOutLinearInEasing))
                    shown = char
                    offsetY.snapTo(-dropPx)
                    squash.snapTo(0.5f)
                    launch { offsetY.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 240f)) }
                    squash.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 280f))
                }
            }
            DigitGlyph(
                shown,
                modifier = Modifier.graphicsLayer {
                    translationY = offsetY.value
                    scaleY = squash.value
                    scaleX = 1f + (1f - squash.value) * 0.4f
                }
            )
        }

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
    topInset: Dp = 0.dp,
    cardColor: Color = TimerCardBg,
    digitColor: Color = TimerDigit,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    dividerColor: Color = Color.Black.copy(alpha = 0.75f),
    splitGapDp: Dp = 0.dp,
    cutMaskEnabled: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(cardColor)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
                else Modifier
            )
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
            if (cutMaskEnabled) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.clip(TopHalfShape)) {
                        FlipText(text = mainText, fontSize = safeFontSize, color = digitColor, extraBold = extraBold)
                    }
                    Box(modifier = Modifier.clip(BottomHalfShape)) {
                        FlipText(text = mainText, fontSize = safeFontSize, color = digitColor, extraBold = extraBold)
                    }
                }
            } else {
                FlipText(text = mainText, fontSize = safeFontSize, color = digitColor, extraBold = extraBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (splitGapDp > dividerThickness) splitGapDp else dividerThickness)
                .background(dividerColor)
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
    modifier: Modifier = Modifier,
    faceStyle: ClockFaceStyle? = null
) {
    Box(modifier = modifier.fillMaxWidth()) {
        FlipDigitCard(
            mainText,
            modifier = Modifier.fillMaxSize(),
            fontSize = fontSize,
            dividerThickness = dividerThickness,
            extraBold = true,
            topInset = if (topLabel.isNotEmpty()) 1.dp else 0.dp,
            cardColor = faceStyle?.cardBackground ?: TimerCardBg,
            digitColor = faceStyle?.digitColor ?: TimerDigit,
            borderColor = faceStyle?.borderColor ?: Color.Transparent,
            borderWidth = faceStyle?.borderWidth ?: 0.dp,
            dividerColor = faceStyle?.dividerColor ?: Color.Black.copy(alpha = 0.75f),
            splitGapDp = faceStyle?.splitDigitGapDp ?: 0.dp,
            cutMaskEnabled = faceStyle?.hasCutMask ?: false
        )
        if (topLabel.isNotEmpty()) {
            Text(
                topLabel,
                color = faceStyle?.labelColor ?: Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
        if (cornerLabel.isNotEmpty()) {
            Text(
                text = cornerLabel,
                color = faceStyle?.digitColor ?: TimerDigit,
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
    modifier: Modifier = Modifier,
    faceStyle: ClockFaceStyle? = null
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
                    topInset = 1.dp,
                    cardColor = faceStyle?.cardBackground ?: TimerCardBg,
                    digitColor = faceStyle?.digitColor ?: TimerDigit,
                    borderColor = faceStyle?.borderColor ?: Color.Transparent,
                    borderWidth = faceStyle?.borderWidth ?: 0.dp,
                    dividerColor = faceStyle?.dividerColor ?: Color.Black.copy(alpha = 0.75f),
                    splitGapDp = faceStyle?.splitDigitGapDp ?: 0.dp,
                    cutMaskEnabled = faceStyle?.hasCutMask ?: false
                )
                Text(
                    label,
                    color = faceStyle?.labelColor ?: Color.White.copy(alpha = 0.78f),
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
private fun LiveClockDisplay(is24Hour: Boolean, isLandscape: Boolean, boxSettings: TimerBoxSettings, clockFace: ClockFace = ClockFace.CLASSIC) {
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
    val faceStyle = remember(clockFace) { clockFaceStyle(clockFace) }

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
            modifier = Modifier.weight(1f).height(boxHeightDp),
            faceStyle = faceStyle
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
            modifier = Modifier.weight(1f).height(boxHeightDp),
            faceStyle = faceStyle
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
    val haptics = LocalHapticFeedback.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val editFocusRequester = remember { FocusRequester() }
    var centeredIndex by remember { mutableStateOf(values.indexOf(selected).coerceAtLeast(0)) }

    LaunchedEffect(selected, values) {
        val targetIndex = values.indexOf(selected).coerceAtLeast(0)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
        centeredIndex = targetIndex
    }

    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }
            .collect { (index, offset, inProgress) ->
                val idx = (index + if (offset > itemHeightPx / 2) 1 else 0).coerceIn(values.indices)
                centeredIndex = idx
                val value = values[idx]
                if (value != selected) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectedChange(value)
                }
                // scroll পুরোপুরি থামার (settle) মুহূর্তে fresh listState পড়ে আবার জোর করে
                // final commit করা হচ্ছে — এটাই guarantee দেয় যে wheel visually যা দেখাচ্ছে,
                // ঠিক সেই value-ই সবসময় আসল selected state-এ যাবে, কোনো frame miss হলেও।
                if (!inProgress) {
                    val settledIndex = (listState.firstVisibleItemIndex +
                            if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                            ).coerceIn(values.indices)
                    val settledValue = values[settledIndex]
                    centeredIndex = settledIndex
                    if (settledValue != selected) {
                        onSelectedChange(settledValue)
                    }
                }
            }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            editText = selected.toString()
            editFocusRequester.requestFocus()
        }
    }

    fun commitEdit() {
        editText.toIntOrNull()?.coerceIn(range.first, range.last)?.let(onSelectedChange)
        isEditing = false
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxHeight().width(columnWidth)
        ) {
            itemsIndexed(values) { index, value ->
                val isCentered = index == centeredIndex
                val distance = kotlin.math.abs(index - centeredIndex).coerceAtMost(2)
                val scale = 1f - distance * 0.18f
                val itemAlpha = (1f - distance * 0.32f).coerceIn(0.28f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                        }
                        .pointerInput(value, isCentered) {
                            detectTapGestures(
                                onTap = {
                                    if (isCentered) isEditing = true else onSelectedChange(value)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!(isCentered && isEditing)) {
                        Text(
                            "%02d".format(value),
                            color = if (isCentered) Color.White else Color.White.copy(alpha = 0.42f),
                            fontSize = if (isCentered) 23.sp else 17.sp,
                            fontWeight = if (isCentered) FontWeight.Black else FontWeight.Normal,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = if (isCentered) androidx.compose.ui.graphics.Shadow(
                                    color = Color.White.copy(alpha = 0.9f),
                                    offset = Offset.Zero,
                                    blurRadius = 18f
                                ) else null
                            )
                        )
                    }
                }
            }
        }
        // শুধু visual highlight — কোনো pointerInput নেই, তাই drag সবসময় LazyColumn পর্যন্ত পৌঁছায়
        Box(
            Modifier
                .zIndex(-1f)
                .fillMaxWidth()
                .height(itemHeight)
                .shadow(
                    elevation = if (isEditing) 10.dp else 6.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = Color.White.copy(alpha = 0.35f),
                    spotColor = Color.White.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1C1C1E),
                            Color(0xFF000000)
                        )
                    )
                )
                .border(1.dp, if (isEditing) TimerAccent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
        ) {
            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it.filter(Char::isDigit).take(2) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(editFocusRequester)
                        .onFocusChanged { focusState -> if (!focusState.isFocused && isEditing) commitEdit() }
                )
            }
        }
    }
}
@Composable
private fun StyledTimerSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accentColor: Color = TimerAccent
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    val rangeSize = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSize == 0f) 0f else ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f)

    fun updateFromX(positionX: Float) {
        if (trackWidthPx <= 0f) return
        val newFraction = (positionX / trackWidthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + rangeSize * newFraction)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .pointerInput(valueRange.start, valueRange.endInclusive) {
                detectTapGestures(onTap = { updateFromX(it.x) })
            }
            .pointerInput(valueRange.start, valueRange.endInclusive) {
                detectDragGestures(
                    onDragStart = { updateFromX(it.x) }
                ) { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF16324A))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((trackWidthPx * fraction).roundToInt() - 14, 0) }
                .size(28.dp)
                .shadow(6.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
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
                StyledTimerSlider(
                    value = widthPercent,
                    valueRange = if (isLandscape) 0.5f..3f else 0.4f..1f,
                    onValueChange = { widthPercent = it; push() }
                )

                Text("Box height  ${boxHeight.toInt()}dp", color = Color.LightGray, fontSize = 13.sp)
                StyledTimerSlider(
                    value = boxHeight,
                    valueRange = 60f..420f,
                    onValueChange = { boxHeight = it; push() }
                )

                Text("Number size  ${fontSize.toInt()}sp", color = Color.LightGray, fontSize = 13.sp)
                StyledTimerSlider(
                    value = fontSize,
                    valueRange = 24f..420f,
                    onValueChange = { fontSize = it; push() }
                )

                Text("Number weight  $fontWeightValue", color = Color.LightGray, fontSize = 13.sp)
                StyledTimerSlider(
                    value = fontWeightValue.toFloat(),
                    valueRange = 100f..900f,
                    onValueChange = { fontWeightValue = it.toInt(); push() }
                )

                Text("Spacing  ${spacing.toInt()}dp", color = Color.LightGray, fontSize = 13.sp)
                StyledTimerSlider(
                    value = spacing,
                    valueRange = 0f..40f,
                    onValueChange = { spacing = it; push() }
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
                    TextButton(onClick = onDismiss) { Text("Done", color = SoftNeutral, fontWeight = FontWeight.Bold) }
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
    panelMaxWidth: Dp = 176.dp,
    wheelItemHeight: Dp = 34.dp,
    colonFontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    Row(
        modifier = modifier.widthIn(max = panelMaxWidth),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(columnWidth)) {
            Text(
                "HR", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(columnWidth), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            NumberWheelColumn(range = 0..99, selected = hours, onSelectedChange = onHoursChange, itemHeight = wheelItemHeight, visibleCount = 3, columnWidth = columnWidth)
        }
        Text(
            ":", color = Color.White, fontSize = colonFontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false,
            modifier = Modifier.padding(bottom = (wheelItemHeight - 8.dp) / 2)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(columnWidth)) {
            Text(
                "MIN", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(columnWidth), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            NumberWheelColumn(range = 0..99, selected = minutes, onSelectedChange = onMinutesChange, itemHeight = wheelItemHeight, visibleCount = 3, columnWidth = columnWidth)
        }
    }
}

/* ---------------- Timer home ---------------- */

@Composable
fun TimerHomeDialog(
    onDismiss: () -> Unit,
    onNavigateToMindMap: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToCalendar: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        QuickTimerState.ensureLoaded(context)
        ClockFaceState.ensureLoaded(context)
    }
    var is24Hour by remember { mutableStateOf(loadIs24Hour(context)) }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStudy by rememberSaveable { mutableStateOf(false) }
    var showQuickTimer by rememberSaveable { mutableStateOf(false) }
    var showClockFacePicker by remember { mutableStateOf(false) }
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
                // ei jaygay age StudyTimerState.pauseAll() call kora hoto internal navigation e o,
                // jeta app background na hoyeo Study Timer pause kore dito. Actual background pause
                // ekhon MindMapApp er global LaunchedEffect theke hoy (AppForegroundState onujayi).
                activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        // App গিয়ে background/অন্য app-এ চলে গেলে (home button, app-switch, screen off)
        // Compose dispose হয় না, তাই আলাদাভাবে ON_STOP শুনে Study Timer pause করা হচ্ছে।
        // Orientation change-এ ON_STOP fire করবে না, কারণ manifest configChanges handle করছে।
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !FloatingPopupSettingsState.enabled) {
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
            val activeFaceStyle = remember(ClockFaceState.current) { clockFaceStyle(ClockFaceState.current) }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(activeFaceStyle.screenBackground)
                    .pointerInput("timer-home-tap") {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                StudyTimerTicker()
                if (ClockFaceState.current == ClockFace.STARLIGHT_PREMIUM) {
                    PremiumStarfieldBackground(modifier = Modifier.fillMaxSize())
                }
                val isLandscape = maxWidth > maxHeight
                val activeClockBoxSettings = TimerBoxLiveSettingsState.get(context, "clock", isLandscape)
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
                    LiveClockDisplay(is24Hour = is24Hour, isLandscape = isLandscape, boxSettings = activeClockBoxSettings, clockFace = ClockFaceState.current)
                }

                // Central-only brightness gesture zone: bounded box so a drag
                // starting near the edges/top/bottom never reaches this handler.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight(0.5f)
                        .pointerInput("timer-home-brightness") {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                applyHomeBrightness(homeBrightnessLevel - dragAmount.y / 600f)
                            }
                        }
                )

                LaunchedEffect(com.example.mindmap.TimerNavigationState.requestOpenSection.value) {
                    when (com.example.mindmap.TimerNavigationState.requestOpenSection.value) {
                        "quick" -> { showQuickTimer = true; com.example.mindmap.TimerNavigationState.requestOpenSection.value = null }
                        "study" -> { showStudy = true; com.example.mindmap.TimerNavigationState.requestOpenSection.value = null }
                    }
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
                                DropdownMenuItem(text = { Text("Calendar") }, onClick = { showMenu = false; onNavigateToCalendar() })
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

    if (showClockFacePicker) {
        ClockFacePickerDialog(
            current = ClockFaceState.current,
            onSelect = { face -> ClockFaceState.update(context, face) },
            onDismiss = { showClockFacePicker = false }
        )
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
    DigitStyleState.ensureLoaded(context)
    FloatingPopupSettingsState.ensureLoaded(context)
    FloatingPopupLabelSettingsState.ensureLoaded(context)
    StrikeAnimationSettingsState.ensureLoaded(context)
    StrikeQuoteState.ensureLoaded(context)
    var floatingPopupEnabled by remember { mutableStateOf(FloatingPopupSettingsState.enabled) }
    var floatingPopupLabelEnabled by remember { mutableStateOf(FloatingPopupLabelSettingsState.enabled) }
    var boxEditTarget by remember { mutableStateOf<BoxEditTarget?>(null) }
    var showClockFacePickerInSettings by remember { mutableStateOf(false) }
    var showQuoteManager by remember { mutableStateOf(false) }
    ClockFaceState.ensureLoaded(context)
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
                Text("Clock face", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showClockFacePickerInSettings = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        clockFaceLabel(ClockFaceState.current),
                        color = SoftNeutral,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("24-hour time", fontSize = 15.sp)
                    }
                    Switch(
                        checked = is24Hour,
                        onCheckedChange = onIs24HourChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating timer popup", fontSize = 15.sp)
                    }
                    Switch(
                        checked = floatingPopupEnabled,
                        onCheckedChange = { value ->
                            if (value && !android.provider.Settings.canDrawOverlays(context)) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                            floatingPopupEnabled = value
                            FloatingPopupSettingsState.update(context, value)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show timer type in popup", fontSize = 15.sp)
                    }
                    Switch(
                        checked = floatingPopupLabelEnabled,
                        onCheckedChange = { value ->
                            floatingPopupLabelEnabled = value
                            FloatingPopupLabelSettingsState.update(context, value)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Digit change style", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        DigitTransitionStyle.FLIP to "Flip",
                        DigitTransitionStyle.SLIDE to "Slide",
                        DigitTransitionStyle.FADE_SCALE to "Fade & Pop",
                        DigitTransitionStyle.BOUNCE to "Bounce",
                        DigitTransitionStyle.WAVE to "Wave",
                    ).forEach { (styleOption, label) ->
                        val selected = DigitStyleState.current == styleOption
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) TimerAccent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (selected) TimerAccent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .pointerInput(styleOption) {
                                    detectTapGestures(onTap = { DigitStyleState.update(context, styleOption) })
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(label, color = Color.White) }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Strike Animation", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Strike Animation", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = StrikeAnimationSettingsState.animationEnabled,
                        onCheckedChange = { StrikeAnimationSettingsState.setAnimationEnabled(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                    )
                }
                if (StrikeAnimationSettingsState.animationEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Character 1", fontSize = 14.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
                        Switch(
                            checked = StrikeAnimationSettingsState.character1Enabled,
                            onCheckedChange = { StrikeAnimationSettingsState.setCharacterEnabled(context, StrikeCharacterId.CHARACTER_1, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Character 2", fontSize = 14.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
                        Switch(
                            checked = StrikeAnimationSettingsState.character2Enabled,
                            onCheckedChange = { StrikeAnimationSettingsState.setCharacterEnabled(context, StrikeCharacterId.CHARACTER_2, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Quote", fontSize = 14.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
                        Switch(
                            checked = StrikeAnimationSettingsState.quoteEnabled,
                            onCheckedChange = { StrikeAnimationSettingsState.setQuoteEnabled(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                        )
                    }
                    if (StrikeAnimationSettingsState.quoteEnabled) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showQuoteManager = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Manage Quotes (${StrikeQuoteState.quotes.size})", color = SoftNeutral, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Strike timer", color = Color.LightGray, fontSize = 14.sp)
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
                        Text("Save", color = SoftNeutral, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                        Text(target.label, color = SoftNeutral, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = SoftNeutral, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showClockFacePickerInSettings) {
        ClockFacePickerDialog(
            current = ClockFaceState.current,
            onSelect = { face -> ClockFaceState.update(context, face) },
            onDismiss = { showClockFacePickerInSettings = false }
        )
    }

    boxEditTarget?.let { target ->
        val currentSettings = remember(target) { TimerBoxLiveSettingsState.get(context, target.scope, target.isLandscape) }
        TimerBoxSettingsPanel(
            settings = currentSettings,
            isLandscape = target.isLandscape,
            scopeLabel = target.label,
            onSettingsChange = { updated ->
                saveTimerBoxSettings(context, target.isLandscape, updated, scope = target.scope)
                TimerBoxLiveSettingsState.update(target.scope, target.isLandscape, updated)
            },
            onDismiss = { boxEditTarget = null },
            onResetDefaults = { defaultTimerBoxSettings(target.scope, target.isLandscape) }
        )
    }

    if (showQuoteManager) {
        QuoteManagerDialog(
            quotes = StrikeQuoteState.quotes,
            onDismiss = { showQuoteManager = false },
            onQuotesChange = { updated -> StrikeQuoteState.persist(context, updated) }
        )
    }
}
@Composable
private fun QuoteManagerDialog(
    quotes: List<StrikeQuote>,
    onDismiss: () -> Unit,
    onQuotesChange: (List<StrikeQuote>) -> Unit
) {
    var localQuotes by remember { mutableStateOf(quotes) }
    var editingQuote by remember { mutableStateOf<StrikeQuote?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    fun commit(updated: List<StrikeQuote>) {
        localQuotes = updated
        onQuotesChange(updated)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 520.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Quotes", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { isAddingNew = true }) { Text("+ Add", color = SoftNeutral, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                if (localQuotes.isEmpty()) {
                    Text("কোনো Quote নেই। + Add দিয়ে যোগ করো।", color = Color.LightGray, fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(localQuotes, key = { it.id }) { quote ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .pointerInput(quote.id) { detectTapGestures(onTap = { editingQuote = quote }) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(quote.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    if (quote.mp3ResourceName.isNotBlank()) {
                                        Text(quote.mp3ResourceName, color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                                TextButton(onClick = { commit(localQuotes.filterNot { it.id == quote.id }) }) {
                                    Text("Remove", color = Color(0xFFFF7A7A), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = SoftNeutral, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (isAddingNew) {
        QuoteEditDialog(
            initial = null,
            onDismiss = { isAddingNew = false },
            onSave = { newQuote ->
                commit(localQuotes + newQuote)
                isAddingNew = false
            }
        )
    }

    editingQuote?.let { quote ->
        QuoteEditDialog(
            initial = quote,
            onDismiss = { editingQuote = null },
            onSave = { updated ->
                commit(localQuotes.map { if (it.id == quote.id) updated else it })
                editingQuote = null
            }
        )
    }
}

@Composable
private fun QuoteEditDialog(
    initial: StrikeQuote?,
    onDismiss: () -> Unit,
    onSave: (StrikeQuote) -> Unit
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var mp3Name by remember { mutableStateOf(initial?.mp3ResourceName.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(if (initial == null) "Add Quote" else "Edit Quote", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Quote text") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = mp3Name,
                    onValueChange = { mp3Name = it.trim() },
                    label = { Text("MP3 resource name (optional, res/raw)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            onSave(
                                (initial ?: StrikeQuote(text = text)).copy(
                                    text = text,
                                    mp3ResourceName = mp3Name
                                )
                            )
                        }
                    ) { Text("Save", color = SoftNeutral, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
@Composable
private fun ClockFacePreviewMini(style: ClockFaceStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(style.screenBackground)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("12", "34").forEach { text ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape((style.cornerRadius / 3).coerceAtMost(10.dp)))
                    .background(style.cardBackground)
                    .then(
                        if (style.borderWidth > 0.dp) Modifier.border(style.borderWidth, style.borderColor, RoundedCornerShape((style.cornerRadius / 3).coerceAtMost(10.dp)))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text, color = style.digitColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ClockFacePickerDialog(
    current: ClockFace,
    onSelect: (ClockFace) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = TimerCardBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 560.dp)) {
                Text("Clock face", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Tap a face to make it active", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ClockFace.entries) { face ->
                        val style = remember(face) { clockFaceStyle(face) }
                        val selected = face == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) TimerAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    width = if (selected) 1.6.dp else 1.dp,
                                    color = if (selected) TimerAccent else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .pointerInput(face) { detectTapGestures(onTap = { onSelect(face) }) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(96.dp)) { ClockFacePreviewMini(style) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                clockFaceLabel(face),
                                color = Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text("✓", color = TimerAccent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = SoftNeutral, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ---------------- Quick stopwatch / countdown ---------------- */

private const val QUICK_TIMER_PREFS = "quick_timer_state"

private fun saveQuickTimerStateToPrefs(context: Context) {
    context.getSharedPreferences(QUICK_TIMER_PREFS, Context.MODE_PRIVATE).edit()
        .putString("mode", QuickTimerState.mode)
        .putLong("elapsed", QuickTimerState.elapsedMillis)
        .putLong("countdown_total", QuickTimerState.countdownTotalMillis)
        .putLong("remaining", QuickTimerState.remainingMillis)
        .putBoolean("has_started", QuickTimerState.hasStarted)
        .apply()
}

private fun loadQuickTimerStateFromPrefs(context: Context) {
    val prefs = context.getSharedPreferences(QUICK_TIMER_PREFS, Context.MODE_PRIVATE)
    if (!prefs.contains("has_started")) return
    QuickTimerState.mode = prefs.getString("mode", "stopwatch") ?: "stopwatch"
    QuickTimerState.elapsedMillis = prefs.getLong("elapsed", 0L)
    QuickTimerState.countdownTotalMillis = prefs.getLong("countdown_total", 5 * 60_000L)
    QuickTimerState.remainingMillis = prefs.getLong("remaining", QuickTimerState.countdownTotalMillis)
    QuickTimerState.hasStarted = prefs.getBoolean("has_started", false)
    QuickTimerState.isRunning = false
}

internal object QuickTimerState {
    var mode by mutableStateOf("stopwatch")
    var isRunning by mutableStateOf(false)
    var elapsedMillis by mutableStateOf(0L)
    var countdownTotalMillis by mutableStateOf(5 * 60_000L)
    var remainingMillis by mutableStateOf(5 * 60_000L)
    var startTimestamp by mutableStateOf(0L)
    var hasStarted by mutableStateOf(false)
    var timeUp by mutableStateOf(false)
    private var stateLoaded = false

    // App শুরুতে একবার persisted state ফিরিয়ে আনে — reset নয়, আগের paused time
    fun ensureLoaded(context: Context) {
        if (!stateLoaded) {
            loadQuickTimerStateFromPrefs(context)
            stateLoaded = true
        }
    }

    // পজ করে exact সময় preserve করে এবং persist করে — reset করে না
    fun pause(context: Context? = null) {
        if (isRunning) {
            val now = System.currentTimeMillis()
            if (mode == "stopwatch") {
                elapsedMillis = now - startTimestamp
            } else {
                val spent = now - startTimestamp
                remainingMillis = (countdownTotalMillis - spent).coerceAtLeast(0L)
            }
            isRunning = false
        }
        context?.let { saveQuickTimerStateToPrefs(it) }
    }

    // যেখান থেকে pause হয়েছিল ঠিক সেখান থেকেই resume করে — elapsed/remaining touch করে না
    fun resume() {
        if (isRunning) return
        startTimestamp = System.currentTimeMillis() -
                (if (mode == "stopwatch") elapsedMillis else countdownTotalMillis - remainingMillis)
        isRunning = true
    }
}

@Composable
private fun QuickTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { QuickTimerState.ensureLoaded(context) }
    DisposableEffect(Unit) {
        TimerForegroundState.activeScreen = "quick"
        onDispose { if (TimerForegroundState.activeScreen == "quick") TimerForegroundState.activeScreen = null }
    }
    var mode by QuickTimerState::mode
    var isRunning by QuickTimerState::isRunning
    var elapsedMillis by QuickTimerState::elapsedMillis
    var countdownTotalMillis by QuickTimerState::countdownTotalMillis
    var remainingMillis by QuickTimerState::remainingMillis
    var startTimestamp by QuickTimerState::startTimestamp
    var hasStarted by QuickTimerState::hasStarted
    var finished by QuickTimerState::timeUp
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var pickerHours by rememberSaveable { mutableStateOf(((countdownTotalMillis / 3_600_000L).toInt())) }
    var pickerMinutes by rememberSaveable { mutableStateOf(((countdownTotalMillis / 60_000L) % 60).toInt()) }
    var showCustomTimeDialog by remember { mutableStateOf(false) }
    var maxWidthLandscapeSnapshot by rememberSaveable { mutableStateOf(false) }

    fun applyPickerToCountdown() {
        val newMillis = (pickerHours * 3_600_000L + pickerMinutes * 60_000L).coerceAtLeast(1000L)
        countdownTotalMillis = newMillis
        remainingMillis = newMillis
    }
    LaunchedEffect(pickerHours, pickerMinutes, isRunning) {
        if (!isRunning) {
            delay(80)
            applyPickerToCountdown()
        }
    }
    LaunchedEffect(isRunning, mode) {
        if (isRunning) {
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
        DisposableEffect(Unit) {
            onDispose {
                // ei dialog dismiss/navigate hole (background na hoyeo) age forcibly pause kore dito.
                // ekhon shudhu current state persist kora hocche, running thakle running e i thakbe;
                // actual background-pause MindMapApp er global lifecycle logic theke hobe.
                saveQuickTimerStateToPrefs(context)
            }
        }
        val quickLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(quickLifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !FloatingPopupSettingsState.enabled) {
                    QuickTimerState.pause(context)
                }
            }
            quickLifecycleOwner.lifecycle.addObserver(observer)
            onDispose { quickLifecycleOwner.lifecycle.removeObserver(observer) }
        }
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

        val activeFaceStyle = remember(ClockFaceState.current) { clockFaceStyle(ClockFaceState.current) }
        Surface(color = TimerBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(activeFaceStyle.screenBackground)
            ) {
                StudyTimerTicker()
                if (ClockFaceState.current == ClockFace.STARLIGHT_PREMIUM) {
                    PremiumStarfieldBackground(modifier = Modifier.fillMaxSize())
                }
                val isLandscape = maxWidth > maxHeight
                maxWidthLandscapeSnapshot = isLandscape
                val activeBoxSettings = TimerBoxLiveSettingsState.get(context, "quick", isLandscape)
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
                            detectHorizontalDragGestures(
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
                                horizontalDragAccum += dragAmount
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
                                onClick = { if (!isRunning && mode != "stopwatch") { mode = "stopwatch"; finished = false } }
                            )
                            TimerPanelButton(
                                text = "Countdown",
                                selected = mode == "countdown",
                                onClick = { if (!isRunning && mode != "countdown") { mode = "countdown"; finished = false } }
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
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
                            modifier = Modifier.width(displayWidth),
                            faceStyle = activeFaceStyle
                        )
                    }

                    // Central-only brightness gesture zone: bounded box so a drag
                    // starting near the edges/top/bottom (or over side/bottom
                    // control panels, which are composed after this and stay
                    // on top) never reaches this handler. Additionally, only
                    // vertical-dominant drags are consumed here — a horizontal
                    // drag (even starting in this zone) is left unconsumed so
                    // it still reaches the outer stopwatch<->countdown swipe
                    // detector instead of being eaten as a brightness change.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(0.5f)
                            .pointerInput("quick-timer-brightness") {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isVertical: Boolean? = null
                                    var lastPosition = down.position
                                    val slop = viewConfiguration.touchSlop
                                    drag(down.id) { change ->
                                        val total = change.position - down.position
                                        if (isVertical == null &&
                                            (kotlin.math.abs(total.x) > slop || kotlin.math.abs(total.y) > slop)
                                        ) {
                                            isVertical = kotlin.math.abs(total.y) >= kotlin.math.abs(total.x)
                                        }
                                        if (isVertical == true) {
                                            change.consume()
                                            val deltaY = change.position.y - lastPosition.y
                                            applyBrightness(brightnessLevel - deltaY / 600f)
                                        }
                                        lastPosition = change.position
                                    }
                                }
                            }
                    )

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
                                        if (isRunning) {
                                            QuickTimerState.pause(context)
                                        } else {
                                            hasStarted = true
                                            QuickTimerPopupState.manuallyDismissed = false
                                            QuickTimerState.resume()
                                        }
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
                                            onHoursChange = { pickerHours = it },
                                            onMinutesChange = { pickerMinutes = it },
                                            onCustomTap = { showCustomTimeDialog = true },
                                            columnWidth = 34.dp,
                                            panelMaxWidth = 96.dp,
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
                                    if (isRunning) {
                                        QuickTimerState.pause(context)
                                    } else {
                                        hasStarted = true
                                        QuickTimerPopupState.manuallyDismissed = false
                                        QuickTimerState.resume()
                                    }
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
                        }) { Text("Save", color = SoftNeutral, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
// PiP মোডে থাকা অবস্থায় বর্তমানে কী চলছে (quick timer বা study subject) সেটা
// সবসময় TimerRunningState-এ আপডেট রাখে, যাতে minimize করার মুহূর্তে সঠিক সিদ্ধান্ত নেওয়া যায়
@Composable
fun TimerRunningWatcher() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { FloatingPopupSettingsState.ensureLoaded(context) }
    val quickRunning = QuickTimerState.isRunning
    val studySubjects = StudyTimerState.subjects
    LaunchedEffect(quickRunning, studySubjects) {
        com.example.mindmap.TimerRunningState.isAnyTimerRunning.value =
            quickRunning || studySubjects.any { it.isRunning }
    }
}

// PiP (ছোট floating) মোডে দেখানোর জন্য সরল, compact timer view
@Composable
fun PipTimerContent() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val quickRunning = QuickTimerState.isRunning
    val runningSubject = StudyTimerState.subjects.firstOrNull { it.isRunning }

    Box(
        modifier = Modifier.fillMaxSize().background(TimerBg),
        contentAlignment = Alignment.Center
    ) {
        when {
            quickRunning -> {
                val millis = if (QuickTimerState.mode == "stopwatch") {
                    (now - QuickTimerState.startTimestamp).coerceAtLeast(0L)
                } else {
                    val spent = now - QuickTimerState.startTimestamp
                    (QuickTimerState.countdownTotalMillis - spent).coerceAtLeast(0L)
                }
                val totalSeconds = millis / 1000
                val h = totalSeconds / 3600
                val m = (totalSeconds / 60) % 60
                val s = totalSeconds % 60
                Text(
                    text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s),
                    color = TimerDigit,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black
                )
            }
            runningSubject != null -> {
                val elapsed = runningSubject.currentElapsedMillis(now)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = runningSubject.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDurationDhms(elapsed),
                        color = TimerAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            else -> {
                Text("No timer running", color = Color.LightGray, fontSize = 14.sp)
            }
        }
    }
}

/* ---------------- Productivity home page summary ---------------- */

@Composable
fun ProductivityTimerSummary(): String {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        StudyTimerState.ensureLoaded(context)
        StrikeSettingsState.ensureLoaded(context)
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val quickRunning = QuickTimerState.isRunning
    val studyRunning = StudyTimerState.subjects.any { it.isRunning }
    LaunchedEffect(quickRunning, studyRunning) {
        while (quickRunning || studyRunning) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return when {
        quickRunning && QuickTimerState.mode == "stopwatch" ->
            "Stopwatch · ${formatDurationDhms((now - QuickTimerState.startTimestamp).coerceAtLeast(0L))}"
        quickRunning && QuickTimerState.mode == "countdown" -> {
            val spent = now - QuickTimerState.startTimestamp
            val remaining = (QuickTimerState.countdownTotalMillis - spent).coerceAtLeast(0L)
            "Countdown · ${formatDurationDhms(remaining)} left"
        }
        studyRunning -> {
            val running = StudyTimerState.subjects.first { it.isRunning }
            "${running.name} · ${formatDurationDhms(running.currentElapsedMillis(now))}"
        }
        else -> "No timer running"
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
        val now = System.currentTimeMillis()
        val isStarting = !subject.isRunning
        val updated = subjects.map { s ->
            when {
                s.id == subject.id && s.isRunning -> s.copy(
                    isRunning = false,
                    accumulatedMillis = s.currentElapsedMillis(now)
                )
                s.id == subject.id && !s.isRunning -> s.copy(
                    isRunning = true,
                    startedAtMillis = now
                )
                // ekbare shudhu ekta Study Timer i active thakbe — onno chalu subject-ke pause kore dao
                s.isRunning -> s.copy(
                    isRunning = false,
                    accumulatedMillis = s.currentElapsedMillis(now)
                )
                else -> s
            }
        }
        if (isStarting) {
            StudyTimerPopupState.activeSubjectId = subject.id
            if (StudyTimerPopupState.manuallyDismissedSubjectId == subject.id) {
                StudyTimerPopupState.manuallyDismissedSubjectId = null
            }
        }
        persist(updated)
    }

    DisposableEffect(Unit) {
        TimerForegroundState.activeScreen = "study"
        onDispose { if (TimerForegroundState.activeScreen == "study") TimerForegroundState.activeScreen = null }
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
                        TextButton(onClick = onDismiss) { Text("Back", color = SoftNeutral) }
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
                                            .background(if (subject.isRunning) Color(0xFFFF6E6E).copy(alpha = 0.85f) else SoftNeutral.copy(alpha = 0.92f))
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
                    }) { Text("Restart", color = SoftNeutral) }
                    TextButton(onClick = {
                        customiseForSubject = subject
                        optionsForSubject = null
                    }) { Text("Customise time", color = SoftNeutral) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Show Popup Box for Study Timer", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = subject.popupEnabled,
                            onCheckedChange = { enabled ->
                                val updated = subjects.map { s -> if (s.id == subject.id) s.copy(popupEnabled = enabled) else s }
                                persist(updated)
                                optionsForSubject = updated.firstOrNull { it.id == subject.id }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = TimerAccent, checkedTrackColor = TimerAccent.copy(alpha = 0.3f))
                        )
                    }
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
                        }) { Text("Save", color = SoftNeutral, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}