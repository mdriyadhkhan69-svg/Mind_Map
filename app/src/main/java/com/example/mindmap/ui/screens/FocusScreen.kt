package com.example.mindmap.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.mindmap.FocusAppConfig
import com.example.mindmap.FocusStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle

private val FocusBg = Color(0xFF0B0B12)
private val FocusCard = Color(0xFF171826)
private val FocusAccent = Color(0xFF64FFDA)
private val FocusMuted = Color(0xFFB7B7C6)
private val FocusBlue = Color(0xFF3B82F6)
private val FocusGreen = Color(0xFF4CAF50)
private val FocusRed = Color(0xFFFF6E6E)
private val FocusLine = Color.White.copy(alpha = 0.12f)
private val CapsuleShape = RoundedCornerShape(50)

private data class FocusAppEntry(val pkg: String, val label: String)

/* ---------------- helpers ---------------- */

private fun focusToast(context: Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun formatCooldown(ms: Long): String {
    val totalMinutes = ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val hText = "$h hour${if (h == 1L) "" else "s"}"
    val mText = "$m minute${if (m == 1L) "" else "s"}"
    return when {
        h > 0 && m > 0 -> "$hText $mText"
        h > 0 -> hText
        else -> mText
    }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Suppress("DEPRECATION")
private fun loadLaunchableApps(context: Context): List<FocusAppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        pm.queryIntentActivities(intent, 0)
    }
    return list.map { FocusAppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun Modifier.pressableSurface(
    shape: Shape,
    background: Color,
    borderColor: Color = FocusLine,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .background(background)
        .border(1.dp, borderColor, shape)
        .clickable(
            interactionSource = source,
            indication = ripple(bounded = true, color = Color.White),
            onClick = onClick
        )
}

private object FocusIconCache {
    val map = HashMap<String, ImageBitmap>()
}

@Composable
private fun FocusAppIcon(pkg: String, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(FocusIconCache.map[pkg], pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()
            }?.also { FocusIconCache.map[pkg] = it }
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/* ---------------- panel building blocks ---------------- */

@Composable
private fun PanelHost(visible: Boolean, onScrim: (() -> Unit)?, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(onScrim) { detectTapGestures(onTap = { onScrim?.invoke() }) }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = FocusCard,
                    contentColor = Color.White,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .animateEnterExit(
                            enter = scaleIn(tween(220), initialScale = 0.94f) + fadeIn(tween(200)),
                            exit = scaleOut(tween(160), targetScale = 0.96f) + fadeOut(tween(140))
                        )
                        .pointerInput(Unit) { detectTapGestures { } }
                ) { content() }
            }
        }
    }
}

@Composable
private fun PanelFrame(
    top: @Composable (() -> Unit)? = null,
    bottom: @Composable (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
        top?.invoke()
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            content = body
        )
        if (bottom != null) {
            Spacer(Modifier.height(14.dp))
            bottom()
        }
    }
}

@Composable
private fun PanelPrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).pressableSurface(CapsuleShape, FocusAccent, Color.Transparent, onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = FocusBg, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}


@Composable
private fun PanelBottomBar(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String? = null,
    blueDone: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.height(44.dp).pressableSurface(CapsuleShape, Color.Transparent, Color.Transparent, onCancel)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) { Text("Cancel", color = FocusMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        if (doneLabel != null) {
            PanelPrimaryButton(doneLabel, onDone)
        } else {
            Box(
                Modifier.size(44.dp).pressableSurface(
                    CircleShape,
                    if (blueDone) FocusBlue else FocusAccent,
                    Color.Transparent,
                    onDone
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    tint = if (blueDone) Color.White else FocusBg
                )
            }
        }
    }
}

@Composable
private fun PanelBackButton(onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).pressableSurface(CapsuleShape, Color.Transparent, Color.Transparent, onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text("Back", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PanelError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, color = FocusRed, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusPasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    last: Boolean = false,
    onDone: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = Color.White, unfocusedLabelColor = Color.LightGray,
        cursorColor = Color.White
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .defaultMinSize(minHeight = 56.dp),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                interactionSource = interactionSource,
                label = { Text(label) },
                colors = fieldColors,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = fieldColors,
                        shape = fieldShape,
                        focusedBorderThickness = 1.dp,
                        unfocusedBorderThickness = 0.75.dp
                    )
                }
            )
        }
    )
}

/* ---------------- switch ---------------- */

@Composable
private fun FocusSwitch(checked: Boolean, onToggle: () -> Unit) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(240), label = "focusSwitch")
    val trackW = 52.dp
    val trackH = 28.dp
    val thumb = 22.dp
    val pad = 3.dp
    val thumbX = pad + (trackW - thumb - pad * 2) * progress
    Box(
        Modifier.width(trackW).height(trackH)
            .pressableSurface(CapsuleShape, Color.Transparent, Color.White.copy(alpha = 0.55f), onToggle)
    ) {
        Box(Modifier.fillMaxSize().padding(1.dp).clip(CapsuleShape)) {
            Box(
                Modifier.fillMaxHeight()
                    .width((thumbX + thumb + pad) * progress)
                    .background(FocusBlue)
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = thumbX)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/* ---------------- mini wheel ---------------- */

@Composable
private fun MiniWheel(range: IntRange, initial: Int, onChange: (Int) -> Unit) {
    val itemHeight = 28.dp
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, values.lastIndex))
    val fling = rememberSnapFlingBehavior(listState)
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onChange)
    val centered by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex +
                    if (listState.firstVisibleItemScrollOffset > itemPx / 2f) 1 else 0).coerceIn(0, values.lastIndex)
        }
    }
    var firstEmission by remember { mutableStateOf(true) }
    LaunchedEffect(centered) {
        currentOnChange(values[centered])
        if (firstEmission) firstEmission = false
        else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        Modifier
            .width(52.dp)
            .height(itemHeight * 3)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, FocusLine, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().height(itemHeight).background(FocusAccent.copy(alpha = 0.12f)))
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { index, value ->
                val isCenter = index == centered
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        "%02d".format(value),
                        color = if (isCenter) Color(0xFFD0D0D8) else Color.White.copy(alpha = if (abs(index - centered) == 1) 0.38f else 0.15f),
                        fontSize = if (isCenter) 19.sp else 14.sp,
                        fontWeight = if (isCenter) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/* ---------------- config panel stages ---------------- */

@Composable
private fun OptionCapsule(label: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .pressableSurface(CapsuleShape, Color.White.copy(alpha = 0.05f), FocusLine, onClick)
            .padding(start = 26.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (trailing != null) Text(trailing, color = FocusMuted, fontSize = 13.sp)
    }
}

@Composable
private fun OptionsStage(
    app: FocusAppEntry,
    minutes: Int?,
    priority: String?,
    onTimer: () -> Unit,
    onPriority: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    PanelFrame(bottom = { PanelBottomBar(onCancel, onDone) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusAppIcon(app.pkg, 36.dp)
            Spacer(Modifier.width(12.dp))
            PanelTitle(app.label)
        }
        Spacer(Modifier.height(18.dp))
        OptionCapsule(
            label = if (minutes != null) "%02d H : %02d M".format(minutes / 60, minutes % 60) else "Set Timer",
            trailing = null,
            onClick = onTimer
        )
        Spacer(Modifier.height(10.dp))
        OptionCapsule(
            label = "Set Priority",
            trailing = when (priority) {
                FocusStore.PRIORITY_HIGH -> "High"
                FocusStore.PRIORITY_LOW -> "Low"
                else -> null
            },
            onClick = onPriority
        )
    }
}

@Composable
private fun TimerStage(
    initialMinutes: Int?,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf((initialMinutes ?: 0) / 60) }
    var minutes by remember { mutableIntStateOf((initialMinutes ?: 0) % 60) }
    var error by remember { mutableStateOf<String?>(null) }
    PanelFrame(
        top = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelBackButton(onBack)
                Spacer(Modifier.width(8.dp))
                PanelTitle("Set Timer")
            }
        },
        bottom = {
            PanelBottomBar(onCancel, onDone = {
                val total = hours * 60 + minutes
                if (total <= 0) error = "Set a duration greater than zero." else onConfirm(total)
            }, doneBlue = true)
        }
    ) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniWheel(0..72, hours) { hours = it; error = null }
            Spacer(Modifier.width(6.dp))
            Text("h", color = FocusMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
            }
            Spacer(Modifier.width(10.dp))
            MiniWheel(0..59, minutes) { minutes = it; error = null }
            Spacer(Modifier.width(6.dp))
            Text("m", color = FocusMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        PanelError(error)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PriorityCapsule(label: String, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) FocusGreen else FocusLine, tween(200), label = "prioBorder")
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .pressableSurface(CapsuleShape, Color.White.copy(alpha = 0.05f), border, onClick)
    ) {
        Text(
            label,
            Modifier.align(Alignment.CenterStart).padding(start = 26.dp),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.2f) + fadeIn(tween(100)),
                exit = scaleOut(tween(160), targetScale = 0.2f) + fadeOut(tween(140))
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(FocusGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PriorityStage(
    selected: String?,
    onTapLow: () -> Unit,
    onTapHigh: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    PanelFrame(
        top = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelBackButton(onBack)
                Spacer(Modifier.width(8.dp))
                PanelTitle("Set Priority")
            }
        },
        bottom = { PanelBottomBar(onCancel, onDone) }
    ) {
        Spacer(Modifier.height(18.dp))
        PriorityCapsule("Low Priority", selected == FocusStore.PRIORITY_LOW, onTapLow)
        Spacer(Modifier.height(10.dp))
        PriorityCapsule("High Priority", selected == FocusStore.PRIORITY_HIGH, onTapHigh)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PasswordSetupStage(onCancel: () -> Unit, onDone: (String) -> Unit) {
    var newPw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        when {
            newPw.isBlank() || confirm.isBlank() -> error = "Both fields are required."
            newPw != confirm -> error = "Passwords do not match."
            else -> onDone(newPw)
        }
    }
    PanelFrame(bottom = { PanelBottomBar(onCancel, { submit() }, blueDone = true) }) {
        PanelTitle("Set Low Priority Password")
        Spacer(Modifier.height(14.dp))
        FocusPasswordField(newPw, { newPw = it; error = null }, "New Password")
        Spacer(Modifier.height(10.dp))
        FocusPasswordField(confirm, { confirm = it; error = null }, "Confirm Password", last = true, onDone = { submit() })
        PanelError(error)
    }
}

@Composable
private fun ConfigPanel(app: FocusAppEntry, existing: FocusAppConfig?, onClose: (Boolean) -> Unit) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf("options") }
    var minutes by remember { mutableStateOf<Int?>(existing?.durationMinutes) }
    var priority by remember { mutableStateOf<String?>(existing?.priority) }
    var draftPriority by remember { mutableStateOf<String?>(existing?.priority) }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            val back = targetState == "options" || (initialState == "password" && targetState == "priority")
            if (back) {
                (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(160)))
            }
        },
        label = "focusConfigStage"
    ) { s ->
        when (s) {
            "timer" -> TimerStage(
                initialMinutes = minutes,
                onBack = { stage = "options" },
                onCancel = { stage = "options" },
                onConfirm = { minutes = it; stage = "options" }
            )
            "priority" -> PriorityStage(
                selected = draftPriority,
                onTapLow = {
                    when {
                        draftPriority == FocusStore.PRIORITY_LOW -> draftPriority = null
                        FocusStore.hasPassword(context) -> draftPriority = FocusStore.PRIORITY_LOW
                        else -> stage = "password"
                    }
                },
                onTapHigh = {
                    draftPriority = if (draftPriority == FocusStore.PRIORITY_HIGH) null else FocusStore.PRIORITY_HIGH
                },
                onBack = { stage = "options" },
                onCancel = { stage = "options" },
                onDone = { priority = draftPriority; stage = "options" }
            )
            "password" -> PasswordSetupStage(
                onCancel = { stage = "priority" },
                onDone = { pw ->
                    FocusStore.setPassword(context, pw)
                    draftPriority = FocusStore.PRIORITY_LOW
                    focusToast(context, "Your password has been set.")
                    stage = "priority"
                }
            )
            else -> OptionsStage(
                app = app,
                minutes = minutes,
                priority = priority,
                onTimer = { stage = "timer" },
                onPriority = { draftPriority = priority; stage = "priority" },
                onCancel = { onClose(false) },
                onDone = {
                    val m = minutes
                    val p = priority
                    when {
                        m == null || m <= 0 || p == null -> focusToast(context, "Set a timer and a priority first.")
                        existing?.isActive == true && existing.expiresAt > System.currentTimeMillis() ->
                            focusToast(context, "Turn off the block before changing this app.")
                        p == FocusStore.PRIORITY_LOW && !FocusStore.hasPassword(context) ->
                            focusToast(context, "Set a Low Priority password first.")
                        else -> {
                            FocusStore.saveConfigured(context, FocusAppConfig(app.pkg, app.label, m, p))
                            onClose(true)
                        }
                    }
                }
            )
        }
    }
}

/* ---------------- other panels ---------------- */

@Composable
private fun SettingsPanel(onReset: () -> Unit, onChange: () -> Unit, onClose: () -> Unit) {
    PanelFrame(
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.height(44.dp).pressableSurface(CapsuleShape, Color.Transparent, Color.Transparent, onClose)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Close", color = FocusMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    ) {
        PanelTitle("Focus Settings")
        Spacer(Modifier.height(16.dp))
        OptionCapsule("Reset Password", null, onReset)
        Spacer(Modifier.height(10.dp))
        OptionCapsule("Change Password", null, onChange)
    }
}

@Composable
private fun ChangePasswordPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun fail(message: String) {
        error = message
        focusToast(context, message)
    }
    fun submit() {
        when {
            !FocusStore.verifyPassword(context, current) -> fail("Current password is incorrect.")
            newPw.isBlank() || confirm.isBlank() -> fail("Both new password fields are required.")
            newPw != confirm -> fail("Passwords do not match.")
            else -> {
                FocusStore.setPassword(context, newPw)
                focusToast(context, "Password changed successfully.")
                onClose()
            }
        }
    }
    PanelFrame(bottom = { PanelBottomBar(onClose, { submit() }, blueDone = true) }) {
        PanelTitle("Change Password")
        Spacer(Modifier.height(14.dp))
        FocusPasswordField(current, { current = it; error = null }, "Current Password")
        Spacer(Modifier.height(10.dp))
        FocusPasswordField(newPw, { newPw = it; error = null }, "New Password")
        Spacer(Modifier.height(10.dp))
        FocusPasswordField(confirm, { confirm = it; error = null }, "Confirm New Password", last = true, onDone = { submit() })
        PanelError(error)
    }
}

@Composable
private fun UnblockPanel(appName: String, onCancel: () -> Unit, onVerified: () -> Unit) {
    val context = LocalContext.current
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (FocusStore.verifyPassword(context, pw)) onVerified()
        else error = "Incorrect password. The block stays active."
    }
    PanelFrame(bottom = { PanelBottomBar(onCancel, { submit() }, blueDone = true) }) {
        PanelTitle("Turn off $appName block")
        Spacer(Modifier.height(14.dp))
        FocusPasswordField(pw, { pw = it; error = null }, "Password", last = true, onDone = { submit() })
        PanelError(error)
    }
}

@Composable
private fun MessagePanel(message: String, buttonLabel: String, onCancel: (() -> Unit)?, onOk: () -> Unit) {
    PanelFrame(
        bottom = {
            if (onCancel != null) {
                PanelBottomBar(onCancel, onOk, doneLabel = buttonLabel)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PanelPrimaryButton(buttonLabel, onOk)
                }
            }
        }
    ) {
        Text(message, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

/* ---------------- main screen pieces ---------------- */

@Composable
private fun FocusAppRow(cfg: FocusAppConfig, now: Long, onToggle: (FocusAppConfig) -> Unit, onOpenConfig: (FocusAppConfig) -> Unit) {
    val active = cfg.isActive && cfg.expiresAt > now
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = { onOpenConfig(cfg) }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FocusAppIcon(cfg.packageName, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
            Text(cfg.appName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (active) "Blocked · ${FocusStore.formatRemaining(cfg.expiresAt - now)} left"
                else "%02d H : %02d M · %s".format(
                    cfg.durationMinutes / 60, cfg.durationMinutes % 60,
                    if (cfg.priority == FocusStore.PRIORITY_HIGH) "High Priority" else "Low Priority"
                ),
                color = if (active) Color.White else FocusMuted,
                fontSize = 12.sp
            )
            }
        }
        Spacer(Modifier.width(10.dp))
        FocusSwitch(checked = active, onToggle = { onToggle(cfg) })
    }
}

@Composable
private fun AddAppCapsule(onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(54.dp)
            .pressableSurface(CapsuleShape, FocusCard, FocusLine, onClick)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Add Your Distracted App", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(FocusAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, contentDescription = "Add", tint = FocusAccent) }
    }
}

@Composable
private fun AppPickRow(app: FocusAppEntry, configured: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .pressableSurface(RoundedCornerShape(12.dp), Color.Transparent, Color.Transparent, onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusAppIcon(app.pkg, 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(app.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (configured) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(FocusBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
@Composable
private fun FocusSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CapsuleShape)
            .background(FocusCard)
            .border(1.dp, FocusLine, CapsuleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = FocusMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", color = FocusMuted, fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}

/* ---------------- entry point ---------------- */

@Composable
fun FocusHomeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    FocusStore.ensureLoaded(context)
    val configs = FocusStore.configs

    var screen by remember { mutableStateOf("main") }
    var showMenu by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<String?>(null) }
    var configApp by remember { mutableStateOf<FocusAppEntry?>(null) }
    var unblockCfg by remember { mutableStateOf<FocusAppConfig?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var serviceEnabled by remember { mutableStateOf(FocusStore.isServiceEnabled(context)) }
    var apps by remember { mutableStateOf<List<FocusAppEntry>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lastResetTapAt by remember { mutableLongStateOf(0L) }
    val focusManager = LocalFocusManager.current
    val visibleConfigs = remember(configs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) configs
        else configs.filter { it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            FocusStore.refreshExpired(context)
            serviceEnabled = FocusStore.isServiceEnabled(context)
            delay(1000)
        }
    }
    LaunchedEffect(screen) {
        if (screen != "apps") searchQuery = ""
        if (screen == "apps") {
            appsLoading = apps.isEmpty()
            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
            appsLoading = false
        }
    }

    val onToggle: (FocusAppConfig) -> Unit = { cfg ->
        val active = cfg.isActive && cfg.expiresAt > System.currentTimeMillis()
        if (!active) {
            if (!FocusStore.isServiceEnabled(context)) {
                activePanel = "permission"
            } else {
                FocusStore.activate(context, cfg.packageName)
            }
        } else if (cfg.priority == FocusStore.PRIORITY_HIGH) {
            activePanel = "high"
        } else {
            unblockCfg = cfg
            activePanel = "unblock"
        }
    }

    Dialog(
        onDismissRequest = {
            when {
                activePanel != null -> activePanel = null
                screen == "apps" -> screen = "main"
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {}
        }

        Surface(color = FocusBg, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    // ---- top bar ----
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (screen == "apps") {
                            PanelBackButton { screen = "main" }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (screen == "apps") "Select App" else "Focus",
                            Modifier.padding(start = if (screen == "apps") 0.dp else 8.dp),
                            fontSize = 24.sp, fontWeight = FontWeight.Bold
                        )
                        if (screen == "main") {
                            IconButton(onClick = {
                                if (searchVisible) {
                                    searchVisible = false
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                } else {
                                    searchVisible = true
                                }
                            }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search apps",
                                    tint = if (searchVisible) FocusAccent else Color.White
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Focus menu", tint = Color.White)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Focus Settings") },
                                    onClick = { showMenu = false; activePanel = "settings" }
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = searchVisible && screen == "main",
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
                    ) {
                        FocusSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }


                    // ---- content ----
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            if (targetState == "apps") {
                                (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith
                                        (slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(180)))
                            } else {
                                (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(260))) togetherWith
                                        (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(180)))
                            }
                        },
                        label = "focusScreen"
                    ) { s ->
                        if (s == "main") {
                            Column(Modifier.fillMaxSize()) {
                                AddAppCapsule { screen = "apps" }
                                if (configs.any { it.isActive } && !serviceEnabled) {
                                    Text(
                                        "Accessibility access is off. Blocking is paused. Tap to enable.",
                                        color = FocusRed, fontSize = 12.sp,
                                        modifier = Modifier
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                            .clickable { openAccessibilitySettings(context) }
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                                    if (visibleConfigs.isEmpty() && searchQuery.isNotBlank()) {
                                        item {
                                            Text(
                                                "No apps found",
                                                color = FocusMuted,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                                            )
                                        }
                                    }
                                    items(visibleConfigs, key = { it.packageName }) { cfg ->
                                        FocusAppRow(cfg, now, onToggle)
                                    }
                                }
                            }
                        } else {
                            if (appsLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = FocusAccent)
                                }
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        Modifier
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .clip(CapsuleShape)
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .border(1.dp, FocusLine, CapsuleShape)
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = FocusMuted, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                            cursorBrush = SolidColor(FocusAccent),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (searchQuery.isEmpty()) {
                                                        Text("Search apps", color = FocusMuted.copy(alpha = 0.7f), fontSize = 14.sp)
                                                    }
                                                    inner()
                                                }
                                            }
                                        )
                                    }
                                    if (filteredApps.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                            Text("No apps found", color = FocusMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp))
                                        }
                                    }
                                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                                        items(filteredApps, key = { it.pkg }) { app ->
                                        val existing = configs.firstOrNull { it.packageName == app.pkg }
                                        AppPickRow(app, configured = existing != null) {
                                            if (existing?.isActive == true && existing.expiresAt > System.currentTimeMillis()) {
                                                focusToast(context, "Turn off the block before changing this app.")
                                            } else {
                                                configApp = app
                                                activePanel = "config"
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- panels ----
                PanelHost(visible = activePanel == "config", onScrim = null) {
                    configApp?.let { app ->
                        ConfigPanel(
                            app = app,
                            existing = configs.firstOrNull { it.packageName == app.pkg },
                            onClose = { saved ->
                                activePanel = null
                                if (saved) screen = "main"
                            }
                        )
                    }
                }
                PanelHost(visible = activePanel == "settings", onScrim = { activePanel = null }) {
                    SettingsPanel(
                        onReset = {
                            val tapAt = System.currentTimeMillis()
                            if (tapAt - lastResetTapAt >= 1000L) {
                                lastResetTapAt = tapAt
                                val remaining = FocusStore.resetCooldownRemainingMs(context)
                                if (remaining > 0L) {
                                    activePanel = null
                                    focusToast(
                                        context,
                                        "You can reset your password again in ${formatCooldown(remaining)}."
                                    )
                                } else {
                                    FocusStore.startResetCooldown(context)
                                    if (FocusStore.hasPassword(context)) {
                                        activePanel = "resetConfirm"
                                    } else {
                                        activePanel = null
                                        focusToast(context, "No password has been set yet.")
                                    }
                                }
                            }
                        },
                        onChange = {
                            if (FocusStore.hasPassword(context)) {
                                activePanel = "change"
                            } else {
                                activePanel = null
                                focusToast(context, "No password has been set yet.")
                            }
                        },
                        onClose = { activePanel = null }
                    )
                }
                PanelHost(visible = activePanel == "resetConfirm", onScrim = null) {
                    MessagePanel(
                        message = "Reset your Low Priority password? The current password will be removed and you will need to set a new one.",
                        buttonLabel = "Reset",
                        onCancel = { activePanel = null },
                        onOk = {
                            if (activePanel == "resetConfirm") {
                                FocusStore.clearPassword(context)
                                activePanel = null
                                focusToast(context, "Password has been reset successfully.")
                            }
                        }
                    )
                }
                PanelHost(visible = activePanel == "change", onScrim = null) {
                    ChangePasswordPanel(onClose = { activePanel = null })
                }
                PanelHost(visible = activePanel == "unblock", onScrim = null) {
                    unblockCfg?.let { cfg ->
                        UnblockPanel(
                            appName = cfg.appName,
                            onCancel = { activePanel = null },
                            onVerified = {
                                FocusStore.deactivate(context, cfg.packageName)
                                activePanel = null
                                focusToast(context, "Block turned off.")
                            }
                        )
                    }
                }
                PanelHost(visible = activePanel == "high", onScrim = { activePanel = null }) {
                    MessagePanel(
                        message = "You set high priority. You cannot open this app until the timer ends.",
                        buttonLabel = "OK",
                        onCancel = null,
                        onOk = { activePanel = null }
                    )
                }
                PanelHost(visible = activePanel == "permission", onScrim = { activePanel = null }) {
                    MessagePanel(
                        message = "Focus needs Accessibility access to detect when a blocked app opens and show the blocking screen. It is used only for this purpose. Focus does not read or collect anything on your screen.",
                        buttonLabel = "Open Settings",
                        onCancel = { activePanel = null },
                        onOk = {
                            activePanel = null
                            openAccessibilitySettings(context)
                        }
                    )
                }
            }
        }
    }
}