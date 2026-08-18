package com.example.mindmap

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.graphics.Point
import android.view.animation.DecelerateInterpolator
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.mindmap.ui.screens.QuickTimerState
import com.example.mindmap.ui.screens.StudyTimerState
import com.example.mindmap.ui.screens.FloatingPopupLabelSettingsState
import com.example.mindmap.ui.screens.currentElapsedMillis
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class FloatingTimerService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Real screen size (portrait/landscape aware, no hardcoded pixels) used to
    // clamp the floating popup so it can never be dragged off-screen.
    private fun screenBounds(): Point {
        val point = Point()
        runCatching { windowManager?.defaultDisplay?.getRealSize(point) }
        if (point.x <= 0 || point.y <= 0) {
            val metrics = resources.displayMetrics
            point.x = metrics.widthPixels
            point.y = metrics.heightPixels
        }
        return point
    }

    // Clamps the popup's top-left (x, y) so the FULL rectangle (using the
    // popup's actual measured width/height) stays within the screen bounds.
    private fun clampToScreen(x: Int, y: Int, viewWidth: Int, viewHeight: Int): Point {
        val bounds = screenBounds()
        val maxX = (bounds.x - viewWidth).coerceAtLeast(0)
        val maxY = (bounds.y - viewHeight).coerceAtLeast(0)
        return Point(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    // On a single tap, if the popup is currently sitting close enough to the
    // LEFT or RIGHT edge that its controls (e.g. the X button) are hard to
    // reach, smoothly slide it inward just enough to expose them. Does
    // nothing if the popup is already comfortably inside the screen.
    private fun nudgeAwayFromEdgeIfNeeded() {
        val p = layoutParams ?: return
        val view = composeView ?: return
        if (view.width <= 0) return
        val density = resources.displayMetrics.density
        val edgeThresholdPx = (20 * density).roundToInt()
        val safeInsetPx = (28 * density).roundToInt()
        val bounds = screenBounds()
        val maxX = (bounds.x - view.width).coerceAtLeast(0)
        val targetX = when {
            p.x <= edgeThresholdPx -> safeInsetPx.coerceAtMost(maxX)
            p.x >= maxX - edgeThresholdPx -> (maxX - safeInsetPx).coerceAtLeast(0)
            else -> return
        }
        if (targetX == p.x) return
        ValueAnimator.ofInt(p.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                p.x = animator.animatedValue as Int
                runCatching { windowManager?.updateViewLayout(view, p) }
            }
            start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 200
        layoutParams = params

        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setContent {
            FloatingTimerPopupContent(
                onPositionChange = { dx, dy ->
                    val p = layoutParams ?: return@FloatingTimerPopupContent
                    val rawX = (p.x + dx).roundToInt()
                    val rawY = (p.y + dy).roundToInt()
                    val clamped = clampToScreen(rawX, rawY, view.width, view.height)
                    p.x = clamped.x
                    p.y = clamped.y
                    runCatching { windowManager?.updateViewLayout(view, p) }
                },
                onClose = {
                    stopSelf()
                },
                onOpenApp = { section -> openApp(section) },
                onEdgeNudge = { nudgeAwayFromEdgeIfNeeded() }
            )
        }
        composeView = view
        runCatching { wm.addView(view, params) }
    }

    private fun pauseActiveTimer() {
        if (QuickTimerState.isRunning) {
            QuickTimerState.pause(this)
        }
        StudyTimerState.subjects.firstOrNull { it.isRunning }?.let { running ->
            val now = System.currentTimeMillis()
            val updated = StudyTimerState.subjects.map { s ->
                if (s.id == running.id) s.copy(isRunning = false, accumulatedMillis = s.currentElapsedMillis(now)) else s
            }
            StudyTimerState.persist(this, updated)
        }
    }

    private fun openApp(section: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("open_timer", true)
            putExtra("open_timer_section", section)
        }
        startActivity(intent)
    }
    override fun onDestroy() {
        composeView?.let { view -> runCatching { windowManager?.removeView(view) } }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: android.content.Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { context.startService(Intent(context, FloatingTimerService::class.java)) }
        }

        fun stop(context: android.content.Context) {
            runCatching { context.stopService(Intent(context, FloatingTimerService::class.java)) }
        }
    }
}

@Composable
private fun FloatingTimerPopupContent(
    onPositionChange: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onOpenApp: (String) -> Unit,
    onEdgeNudge: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val quickRunning = QuickTimerState.isRunning
    val quickActive = QuickTimerState.hasStarted
    val runningSubject = StudyTimerState.subjects.firstOrNull { it.isRunning }
    val currentQuickRunning by rememberUpdatedState(quickRunning)
    val currentQuickActive by rememberUpdatedState(quickActive)
    val currentRunningSubject by rememberUpdatedState(runningSubject)
    val timeUp = QuickTimerState.timeUp
    val strikeActive = StudyTimerState.pendingCelebration != null
    val currentSection = if (quickActive) "quick" else if (runningSubject != null) "study" else "quick"

    val label: String
    val timeText: String
    when {
        timeUp -> {
            label = "COUNTDOWN"
            timeText = ""
        }
        quickActive -> {
            val millis = if (QuickTimerState.mode == "stopwatch") {
                if (quickRunning) (now - QuickTimerState.startTimestamp).coerceAtLeast(0L) else QuickTimerState.elapsedMillis
            } else {
                if (quickRunning) (QuickTimerState.countdownTotalMillis - (now - QuickTimerState.startTimestamp)).coerceAtLeast(0L) else QuickTimerState.remainingMillis
            }
            val totalSeconds = millis / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds / 60) % 60
            val s = totalSeconds % 60
            label = if (QuickTimerState.mode == "stopwatch") "STOPWATCH" else "COUNTDOWN"
            timeText = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
        runningSubject != null -> {
            val elapsed = runningSubject.currentElapsedMillis(now)
            val totalSeconds = elapsed / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds / 60) % 60
            val s = totalSeconds % 60
            label = runningSubject.name.take(14)
            timeText = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
        else -> {
            label = "TIMER"
            timeText = "--:--"
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(140),
        label = "floatingPopupPressScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (timeUp) Color(0xFF7A0E0E) else Color(0xEE171A2B))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPositionChange(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        expanded = !expanded
                        onEdgeNudge()
                    },
                    onDoubleTap = { onOpenApp(currentSection) }
                )
            }
            .padding(10.dp)
    ) {
        if (strikeActive) {
            Text(
                text = "★",
                color = Color(0xFFFFD700),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (FloatingPopupLabelSettingsState.enabled) {
            Text(
                text = label,
                color = Color(0xFF64FFDA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = if (timeUp) "TIME UP" else timeText,
            color = Color.White,
            fontSize = if (timeUp) 16.sp else 18.sp,
            fontWeight = FontWeight.Black
        )
        if (expanded && !timeUp) {
            Spacer(Modifier.height(6.dp))
            Row {
                val isRunning = if (quickRunning) QuickTimerState.isRunning else runningSubject?.isRunning == true
                var playPausePressed by remember { mutableStateOf(false) }
                val playPauseScale by animateFloatAsState(
                    targetValue = if (playPausePressed) 0.86f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "floatingPopupPlayPauseScale"
                )
                val playPauseRippleAlpha by animateFloatAsState(
                    targetValue = if (playPausePressed) 0.28f else 0f,
                    animationSpec = tween(if (playPausePressed) 80 else 220),
                    label = "floatingPopupPlayPauseRipple"
                )
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "toggle",
                    tint = Color(0xFF64FFDA),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = playPauseScale; scaleY = playPauseScale }
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF64FFDA).copy(alpha = playPauseRippleAlpha))
                        .padding(2.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    playPausePressed = true
                                    tryAwaitRelease()
                                    playPausePressed = false
                                },
                                onTap = {
                                    val tappedRunningSubject = currentRunningSubject
                                    when {
                                        currentQuickRunning || (currentQuickActive && tappedRunningSubject == null) -> {
                                            if (QuickTimerState.isRunning) {
                                                QuickTimerState.pause(context)
                                            } else {
                                                QuickTimerState.resume()
                                            }
                                        }
                                        tappedRunningSubject != null -> {
                                            val runningSubject = tappedRunningSubject
                                            val nowMillis = System.currentTimeMillis()
                                            val updated = StudyTimerState.subjects.map { s ->
                                                if (s.id == runningSubject.id) {
                                                    s.copy(
                                                        isRunning = false,
                                                        accumulatedMillis = s.currentElapsedMillis(nowMillis)
                                                    )
                                                } else s
                                            }
                                            StudyTimerState.persist(context, updated)
                                        }
                                    }
                                }
                            )
                        }
                )
                Spacer(Modifier.width(14.dp))
                var closePressed by remember { mutableStateOf(false) }
                val closeScale by animateFloatAsState(
                    targetValue = if (closePressed) 0.85f else 1f,
                    animationSpec = tween(120),
                    label = "floatingPopupCloseScale"
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "close",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    closePressed = true
                                    tryAwaitRelease()
                                    closePressed = false
                                },
                                onTap = { onClose() }
                            )
                        }
                )
            }
        }
    }
}