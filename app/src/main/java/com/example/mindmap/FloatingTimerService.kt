package com.example.mindmap

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    p.x = (p.x + dx).roundToInt()
                    p.y = (p.y + dy).roundToInt()
                    runCatching { windowManager?.updateViewLayout(view, p) }
                },
                onClose = { stopSelf() },
                onOpenApp = { openApp() }
            )
        }
        composeView = view
        runCatching { wm.addView(view, params) }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("open_timer", true)
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
    onOpenApp: () -> Unit
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
    val runningSubject = StudyTimerState.subjects.firstOrNull { it.isRunning }
    val timeUp = QuickTimerState.timeUp
    val strikeActive = StudyTimerState.pendingCelebration != null

    // Nothing left to show → auto-dismiss so the popup never sits orphaned.
    LaunchedEffect(quickRunning, runningSubject, timeUp) {
        if (!quickRunning && runningSubject == null && !timeUp) {
            onClose()
        }
    }

    val label: String
    val timeText: String
    when {
        timeUp -> {
            label = "COUNTDOWN"
            timeText = ""
        }
        quickRunning -> {
            val millis = if (QuickTimerState.mode == "stopwatch") {
                (now - QuickTimerState.startTimestamp).coerceAtLeast(0L)
            } else {
                (QuickTimerState.countdownTotalMillis - (now - QuickTimerState.startTimestamp)).coerceAtLeast(0L)
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
                    onTap = { expanded = !expanded },
                    onDoubleTap = { onOpenApp() }
                )
            }
            .padding(10.dp)
    ) {
        Text(
            text = if (strikeActive) "★ STRIKE" else label,
            color = if (strikeActive) Color(0xFFFFD700) else Color(0xFF64FFDA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
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
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "toggle",
                    tint = Color(0xFF64FFDA),
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                when {
                                    quickRunning || (QuickTimerState.hasStarted && runningSubject == null) -> {
                                        if (QuickTimerState.isRunning) {
                                            QuickTimerState.pause()
                                        } else {
                                            QuickTimerState.isRunning = true
                                            QuickTimerState.startTimestamp = System.currentTimeMillis() -
                                                    (if (QuickTimerState.mode == "stopwatch") QuickTimerState.elapsedMillis
                                                    else QuickTimerState.countdownTotalMillis - QuickTimerState.remainingMillis)
                                        }
                                    }
                                    runningSubject != null -> {
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
                            })
                        }
                )
                Spacer(Modifier.width(14.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "close",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
                )
            }
        }
    }
}