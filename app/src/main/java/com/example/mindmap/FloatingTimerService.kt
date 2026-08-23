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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import com.example.mindmap.ui.screens.FloatingPopupVisibility
import com.example.mindmap.ui.screens.QuickTimerPopupState
import com.example.mindmap.ui.screens.StudyTimerPopupState
import com.example.mindmap.ui.screens.currentElapsedMillis
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingTimerService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null

    private var quickComposeView: ComposeView? = null
    private var quickLayoutParams: WindowManager.LayoutParams? = null
    private var studyComposeView: ComposeView? = null
    private var studyLayoutParams: WindowManager.LayoutParams? = null
    private var removeTargetView: ComposeView? = null

    private fun toggleMerge() {
        val quickView = quickComposeView
        val studyView = studyComposeView
        val quickParams = quickLayoutParams
        val studyParams = studyLayoutParams
        if (quickView == null || studyView == null || quickParams == null || studyParams == null) return
        if (!FloatingPopupVisibility.showQuick || !FloatingPopupVisibility.showStudy) return

        if (!PopupMergeState.isMerged) {
            // Snap Study directly beneath Quick, same X, zero gap — the two
            // separate windows read as one combined container.
            val targetX = quickParams.x
            val targetY = quickParams.y + quickView.height
            animateLayoutParam(studyView, studyParams, targetX, targetY) {
                PopupMergeState.isMerged = true
            }
        } else {
            // Separate back to a side-by-side position next to Quick, not stacked beneath it.
            val gap = sideBySideGapPx()
            val bounds = screenBounds()
            val preferredRight = quickParams.x + quickView.width + gap
            val fitsRight = preferredRight + studyView.width <= bounds.x
            val targetX = if (fitsRight) preferredRight else (quickParams.x - studyView.width - gap).coerceAtLeast(0)
            val targetY = quickParams.y
            val clamped = clampToScreen(targetX, targetY, studyView.width, studyView.height)
            animateLayoutParam(studyView, studyParams, clamped.x, clamped.y) {
                PopupMergeState.isMerged = false
            }
        }
    }

    private fun sideBySideGapPx(): Int = (14 * resources.displayMetrics.density).roundToInt()

    private fun reflowSideBySideIfNeeded() {
        if (PopupMergeState.isMerged) return
        val quickView = quickComposeView ?: return
        val studyView = studyComposeView ?: return
        val quickParams = quickLayoutParams ?: return
        val studyParams = studyLayoutParams ?: return
        if (!FloatingPopupVisibility.showQuick || !FloatingPopupVisibility.showStudy) return
        if (quickView.width <= 0 || studyView.width <= 0) return

        val gap = sideBySideGapPx()
        val quickRect = android.graphics.Rect(quickParams.x, quickParams.y, quickParams.x + quickView.width, quickParams.y + quickView.height)
        val studyRect = android.graphics.Rect(studyParams.x, studyParams.y, studyParams.x + studyView.width, studyParams.y + studyView.height)
        if (!android.graphics.Rect.intersects(quickRect, studyRect)) return

        val bounds = screenBounds()
        val preferredRight = quickParams.x + quickView.width + gap
        val fitsRight = preferredRight + studyView.width <= bounds.x
        val targetX: Int
        val targetY: Int
        if (fitsRight) {
            targetX = preferredRight
            targetY = quickParams.y
        } else {
            val preferredLeft = quickParams.x - studyView.width - gap
            if (preferredLeft >= 0) {
                targetX = preferredLeft
                targetY = quickParams.y
            } else {
                // Not enough horizontal room either side — nudge study slightly above quick
                // instead of leaving them overlapping.
                targetX = studyParams.x
                targetY = (quickParams.y - studyView.height - gap).coerceAtLeast(0)
            }
        }
        val clamped = clampToScreen(targetX, targetY, studyView.width, studyView.height)
        if (clamped.x == studyParams.x && clamped.y == studyParams.y) return
        animateLayoutParam(studyView, studyParams, clamped.x, clamped.y) {}
    }

    private fun animateLayoutParam(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
        onEnd: () -> Unit
    ) {
        val startX = params.x
        val startY = params.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).roundToInt()
                params.y = (startY + (targetY - startY) * fraction).roundToInt()
                runCatching { windowManager?.updateViewLayout(view, params) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    private fun reflowMergedIfNeeded() {
        if (!PopupMergeState.isMerged) return
        val quickView = quickComposeView ?: return
        val studyView = studyComposeView ?: return
        val quickParams = quickLayoutParams ?: return
        val studyParams = studyLayoutParams ?: return
        studyParams.x = quickParams.x
        studyParams.y = quickParams.y + quickView.height
        runCatching { windowManager?.updateViewLayout(studyView, studyParams) }
    }

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

    private fun clampToScreen(x: Int, y: Int, viewWidth: Int, viewHeight: Int): Point {
        val bounds = screenBounds()
        val maxX = (bounds.x - viewWidth).coerceAtLeast(0)
        val maxY = (bounds.y - viewHeight).coerceAtLeast(0)
        return Point(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    private fun nudgeAwayFromEdgeIfNeeded(view: ComposeView?, params: WindowManager.LayoutParams?) {
        val p = params ?: return
        val targetView = view ?: return
        if (targetView.width <= 0) return
        val density = resources.displayMetrics.density
        val edgeThresholdPx = (20 * density).roundToInt()
        val safeInsetPx = (28 * density).roundToInt()
        val bounds = screenBounds()
        val maxX = (bounds.x - targetView.width).coerceAtLeast(0)
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
                runCatching { windowManager?.updateViewLayout(targetView, p) }
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

    private fun removeTargetCenter(): Point {
        val bounds = screenBounds()
        val bottomMarginPx = (110 * resources.displayMetrics.density).roundToInt()
        return Point(bounds.x / 2, bounds.y - bottomMarginPx)
    }

    private fun showRemoveTarget() {
        if (removeTargetView != null) return
        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val center = removeTargetCenter()
        // Must match RemoveTargetOverlay's fixed outer box size — otherwise
        // the enlarged circle gets clipped by this overlay window's bounds.
        val sizePx = (96 * resources.displayMetrics.density).roundToInt()
        params.x = center.x - sizePx / 2
        params.y = center.y - sizePx / 2
        val view = ComposeView(this)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setContent { RemoveTargetOverlay() }
        removeTargetView = view
        runCatching { wm.addView(view, params) }
    }

    private fun hideRemoveTarget() {
        val view = removeTargetView ?: return
        runCatching { windowManager?.removeView(view) }
        removeTargetView = null
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

        // ---- Quick timer (Countdown/Stopwatch) widget: fully independent from Study Timer ----
        val quickParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        quickParams.gravity = Gravity.TOP or Gravity.START
        quickParams.x = 40
        quickParams.y = 200
        quickLayoutParams = quickParams

        val quickView = ComposeView(this)
        quickView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        quickView.setViewTreeLifecycleOwner(this)
        quickView.setViewTreeViewModelStoreOwner(this)
        quickView.setViewTreeSavedStateRegistryOwner(this)
        quickView.setContent {
            FloatingTimerPopupContent(
                onPositionChange = { dx, dy ->
                    val p = quickLayoutParams ?: return@FloatingTimerPopupContent
                    val rawX = (p.x + dx).roundToInt()
                    val rawY = (p.y + dy).roundToInt()
                    val clamped = clampToScreen(rawX, rawY, quickView.width, quickView.height)
                    p.x = clamped.x
                    p.y = clamped.y
                    runCatching { windowManager?.updateViewLayout(quickView, p) }
                    if (QuickPopupDragState.isDragging) {
                        val target = removeTargetCenter()
                        val popupCenterX = clamped.x + quickView.width / 2f
                        val popupCenterY = clamped.y + quickView.height / 2f
                        val distance = kotlin.math.hypot(popupCenterX - target.x, popupCenterY - target.y)
                        val thresholdPx = 64f * resources.displayMetrics.density
                        QuickPopupDragState.isNearRemove = distance <= thresholdPx
                    }
                },
                onClose = {
                    QuickTimerPopupState.manuallyDismissed = true
                },
                onOpenApp = { openApp("quick") },
                onEdgeNudge = { nudgeAwayFromEdgeIfNeeded(quickComposeView, quickLayoutParams) },
                onDragStart = {
                    QuickPopupDragState.isNearRemove = false
                    QuickPopupDragState.isDragging = true
                    showRemoveTarget()
                },
                onDragEnd = {
                    val shouldRemove = QuickPopupDragState.isNearRemove
                    QuickPopupDragState.isDragging = false
                    QuickPopupDragState.isNearRemove = false
                    if (!StudyPopupDragState.isDragging) hideRemoveTarget()
                    if (shouldRemove) {
                        QuickTimerPopupState.manuallyDismissed = true
                    } else {
                        reflowMergedIfNeeded()
                        reflowSideBySideIfNeeded()
                    }
                },
                onTripleTap = { toggleMerge() }
            )
        }
        quickComposeView = quickView
        runCatching { wm.addView(quickView, quickParams) }

        // ---- Study Timer widget: separate popup instance/state ----
        val studyParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        studyParams.gravity = Gravity.TOP or Gravity.START
        studyParams.x = 220
        studyParams.y = 200
        studyLayoutParams = studyParams

        val studyView = ComposeView(this)
        studyView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        studyView.setViewTreeLifecycleOwner(this)
        studyView.setViewTreeViewModelStoreOwner(this)
        studyView.setViewTreeSavedStateRegistryOwner(this)
        studyView.setContent {
            StudyTimerPopupContent(
                onPositionChange = { dx, dy ->
                    val p = studyLayoutParams ?: return@StudyTimerPopupContent
                    val rawX = (p.x + dx).roundToInt()
                    val rawY = (p.y + dy).roundToInt()
                    val clamped = clampToScreen(rawX, rawY, studyView.width, studyView.height)
                    p.x = clamped.x
                    p.y = clamped.y
                    runCatching { windowManager?.updateViewLayout(studyView, p) }
                    if (StudyPopupDragState.isDragging) {
                        val target = removeTargetCenter()
                        val popupCenterX = clamped.x + studyView.width / 2f
                        val popupCenterY = clamped.y + studyView.height / 2f
                        val distance = kotlin.math.hypot(popupCenterX - target.x, popupCenterY - target.y)
                        val thresholdPx = 64f * resources.displayMetrics.density
                        StudyPopupDragState.isNearRemove = distance <= thresholdPx
                    }
                },
                onClose = {
                    StudyTimerPopupState.manuallyDismissedSubjectId = StudyTimerPopupState.activeSubjectId
                        ?: StudyTimerState.subjects.firstOrNull { it.isRunning }?.id
                },
                onOpenApp = { openApp("study") },
                onEdgeNudge = { nudgeAwayFromEdgeIfNeeded(studyComposeView, studyLayoutParams) },
                onDragStart = {
                    StudyPopupDragState.isNearRemove = false
                    StudyPopupDragState.isDragging = true
                    showRemoveTarget()
                },
                onDragEnd = {
                    val shouldRemove = StudyPopupDragState.isNearRemove
                    StudyPopupDragState.isDragging = false
                    StudyPopupDragState.isNearRemove = false
                    if (!QuickPopupDragState.isDragging) hideRemoveTarget()
                    if (shouldRemove) {
                        val dismissedId = StudyTimerPopupState.activeSubjectId
                            ?: StudyTimerState.subjects.firstOrNull { it.isRunning }?.id
                        StudyTimerPopupState.manuallyDismissedSubjectId = dismissedId
                        PopupMergeState.isMerged = false
                    } else if (PopupMergeState.isMerged) {
                        // Dragging Study away while merged breaks the merge.
                        PopupMergeState.isMerged = false
                    } else {
                        reflowSideBySideIfNeeded()
                    }
                },
                onTripleTap = { toggleMerge() }
            )
        }
        studyComposeView = studyView
        runCatching { wm.addView(studyView, studyParams) }
        studyView.post { reflowSideBySideIfNeeded() }
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
        quickComposeView?.let { view -> runCatching { windowManager?.removeView(view) } }
        studyComposeView?.let { view -> runCatching { windowManager?.removeView(view) } }
        hideRemoveTarget()
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

private object QuickPopupDragState {
    var isDragging by mutableStateOf(false)
    var isNearRemove by mutableStateOf(false)
}

private object StudyPopupDragState {
    var isDragging by mutableStateOf(false)
    var isNearRemove by mutableStateOf(false)
}

// Triple-tap either popup toggles a shared "merged" visual state. Merging
// never touches QuickTimerState/StudyTimerState — only window position/size,
// so the two timers stay fully independent as required.
private object PopupMergeState {
    var isMerged by mutableStateOf(false)
}

@Composable
private fun RemoveTargetOverlay() {
    val isDragging = QuickPopupDragState.isDragging || StudyPopupDragState.isDragging
    val isNear = QuickPopupDragState.isNearRemove || StudyPopupDragState.isNearRemove
    // Animate the actual layout size instead of a graphicsLayer scale — a
    // scale transform doesn't grow this overlay window's own bounds, so the
    // circle used to get clipped square by the window edge once it grew
    // past 72dp. The outer box below is fixed at the max footprint and never
    // resizes; only the inner circle's real size animates within it.
    val circleSize by animateDpAsState(
        targetValue = if (isNear) 88.dp else 72.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "removeTargetSize"
    )
    val removeCoreColor = Color(0xFFE53E3E)
    val removeGlowColor = Color(0xFFFF6B5C)
    AnimatedVisibility(
        visible = isDragging,
        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.6f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.6f)
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .shadow(
                        elevation = if (isNear) 22.dp else 10.dp,
                        shape = CircleShape,
                        ambientColor = removeGlowColor,
                        spotColor = removeGlowColor,
                        clip = true
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isNear) {
                                listOf(removeGlowColor, removeCoreColor)
                            } else {
                                listOf(removeCoreColor.copy(alpha = 0.85f), removeCoreColor.copy(alpha = 0.72f))
                            }
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove timer popup",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingTimerPopupContent(
    onPositionChange: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onEdgeNudge: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTripleTap: () -> Unit = {}
) {
    if (!FloatingPopupVisibility.showQuick) return
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
    val currentQuickRunning by rememberUpdatedState(quickRunning)
    val timeUp = QuickTimerState.timeUp

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

    val quickTapScope = rememberCoroutineScope()
    var quickTapCount by remember { mutableStateOf(0) }
    var quickTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (timeUp) Color(0xFF7A0E0E) else Color(0xEE171A2B))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
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
                        quickTapCount += 1
                        quickTapJob?.cancel()
                        quickTapJob = quickTapScope.launch {
                            delay(260)
                            when (quickTapCount) {
                                1 -> { expanded = !expanded; onEdgeNudge() }
                                2 -> onOpenApp()
                                else -> onTripleTap()
                            }
                            quickTapCount = 0
                        }
                    }
                )
            }
            .padding(10.dp)
    ) {
        if (FloatingPopupLabelSettingsState.enabled) {
            Text(
                text = label,
                color = Color(0xFFEDE6DA),
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
                val isRunning = currentQuickRunning
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
                    tint = Color(0xFFEDE6DA),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = playPauseScale; scaleY = playPauseScale }
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFEDE6DA).copy(alpha = playPauseRippleAlpha))
                        .padding(2.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    playPausePressed = true
                                    tryAwaitRelease()
                                    playPausePressed = false
                                },
                                onTap = {
                                    if (QuickTimerState.isRunning) {
                                        QuickTimerState.pause(context)
                                    } else {
                                        QuickTimerState.resume()
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

@Composable
private fun StudyTimerPopupContent(
    onPositionChange: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onEdgeNudge: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTripleTap: () -> Unit = {}
) {
    if (!FloatingPopupVisibility.showStudy) return
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    // The pinned popup subject — NOT "currently running". This is what keeps
    // the popup visible through pause/resume: the popup's identity no longer
    // disappears just because isRunning flips to false.
    val activeSubject = StudyTimerState.subjects.firstOrNull { it.id == StudyTimerPopupState.activeSubjectId }
        ?: StudyTimerState.subjects.firstOrNull { it.isRunning }
    val currentActiveSubject by rememberUpdatedState(activeSubject)
    val strikeActive = StudyTimerState.pendingCelebration != null

    if (activeSubject == null) return

    val elapsed = activeSubject.currentElapsedMillis(now)
    val totalSeconds = elapsed / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds / 60) % 60
    val s = totalSeconds % 60
    val label = activeSubject.name.take(14)
    val timeText = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    var expanded by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(140),
        label = "studyFloatingPopupPressScale"
    )

    val studyTapScope = rememberCoroutineScope()
    var studyTapCount by remember { mutableStateOf(0) }
    var studyTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val mergedShape = if (PopupMergeState.isMerged) {
        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(16.dp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(mergedShape)
            .background(Color(0xEE171A2B))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
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
                        studyTapCount += 1
                        studyTapJob?.cancel()
                        studyTapJob = studyTapScope.launch {
                            delay(260)
                            when (studyTapCount) {
                                1 -> { expanded = !expanded; onEdgeNudge() }
                                2 -> onOpenApp()
                                else -> onTripleTap()
                            }
                            studyTapCount = 0
                        }
                    }
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
                color = Color(0xFFEDE6DA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Row {
                var playPausePressed by remember { mutableStateOf(false) }
                val playPauseScale by animateFloatAsState(
                    targetValue = if (playPausePressed) 0.86f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "studyFloatingPopupPlayPauseScale"
                )
                val playPauseRippleAlpha by animateFloatAsState(
                    targetValue = if (playPausePressed) 0.28f else 0f,
                    animationSpec = tween(if (playPausePressed) 80 else 220),
                    label = "studyFloatingPopupPlayPauseRipple"
                )
                Icon(
                    imageVector = if (currentActiveSubject?.isRunning == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "toggle",
                    tint = Color(0xFFEDE6DA),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = playPauseScale; scaleY = playPauseScale }
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFEDE6DA).copy(alpha = playPauseRippleAlpha))
                        .padding(2.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    playPausePressed = true
                                    tryAwaitRelease()
                                    playPausePressed = false
                                },
                                onTap = {
                                    currentActiveSubject?.let { subject ->
                                        val nowMillis = System.currentTimeMillis()
                                        val updated = StudyTimerState.subjects.map { s ->
                                            when {
                                                s.id == subject.id && s.isRunning -> s.copy(
                                                    isRunning = false,
                                                    accumulatedMillis = s.currentElapsedMillis(nowMillis)
                                                )
                                                s.id == subject.id && !s.isRunning -> s.copy(
                                                    isRunning = true,
                                                    startedAtMillis = nowMillis
                                                )
                                                s.isRunning -> s.copy(
                                                    isRunning = false,
                                                    accumulatedMillis = s.currentElapsedMillis(nowMillis)
                                                )
                                                else -> s
                                            }
                                        }
                                        StudyTimerState.persist(context, updated)
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
                    label = "studyFloatingPopupCloseScale"
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