package com.example.mindmap.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.mindmap.ui.theme.SoftNeutral
import com.example.mindmap.ui.viewmodel.CalendarViewModel
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import com.example.mindmap.ui.viewmodel.SectionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.TextStyle

private val ProdBg = Color(0xFF0B0B12)
private val ProdCard = Color(0xFF171826)
private val ProdCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
private val ProdAccent = Color(0xFF64FFDA)
private val ProdAccent2 = Color(0xFFBB86FC)
private val ProdTextMuted = Color(0xFFB7B7C6)

private data class ProductivityStyle(
    val backgroundArgb: Long? = null,
    val cardArgb: Long? = null,
    val accentArgb: Long? = null,
    val textArgb: Long? = null
)

private fun loadProductivityStyle(context: android.content.Context): ProductivityStyle {
    val prefs = context.getSharedPreferences("productivity_home", android.content.Context.MODE_PRIVATE)
    fun color(key: String): Long? = prefs.getLong(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
    return ProductivityStyle(
        backgroundArgb = color("background_argb"),
        cardArgb = color("card_argb"),
        accentArgb = color("accent_argb"),
        textArgb = color("text_argb")
    )
}

private fun saveProductivityStyle(context: android.content.Context, style: ProductivityStyle) {
    val editor = context.getSharedPreferences("productivity_home", android.content.Context.MODE_PRIVATE).edit()
    fun put(key: String, value: Long?) { if (value == null) editor.remove(key) else editor.putLong(key, value) }
    put("background_argb", style.backgroundArgb)
    put("card_argb", style.cardArgb)
    put("accent_argb", style.accentArgb)
    put("text_argb", style.textArgb)
    editor.apply()
}

private fun loadProductivityCardOrder(context: android.content.Context): List<String> {
    val defaultOrder = listOf("mindmap", "files", "calendar", "timer")
    val prefs = context.getSharedPreferences("productivity_home", android.content.Context.MODE_PRIVATE)
    val saved = prefs.getString("card_order", null) ?: return defaultOrder
    val savedList = saved.split(",").filter { it.isNotBlank() && it in defaultOrder }
    val missing = defaultOrder.filterNot { it in savedList }
    return savedList + missing
}

private fun saveProductivityCardOrder(context: android.content.Context, order: List<String>) {
    context.getSharedPreferences("productivity_home", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("card_order", order.joinToString(","))
        .apply()
}

@Composable
fun ProductivityHomeScreen(
    mindMapViewModel: MindMapViewModel,
    sectionViewModel: SectionViewModel,
    calendarViewModel: CalendarViewModel,
    onOpenMindMap: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val prodContext = LocalContext.current
    var productivityStyle by remember { mutableStateOf(loadProductivityStyle(prodContext)) }
    var cardOrder by remember { mutableStateOf(loadProductivityCardOrder(prodContext)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val effectiveBackground = Color(productivityStyle.backgroundArgb ?: 0xFF0B0B12)
    val effectiveText = Color(productivityStyle.textArgb ?: 0xFFFFFFFF)
    val effectiveAccent = Color(productivityStyle.accentArgb ?: 0xFF64FFDA)
    val effectiveCardColor = Color(productivityStyle.cardArgb ?: 0xFF171826)

    val allSections by sectionViewModel.allSections.collectAsState()
    val currentSectionId by sectionViewModel.currentSectionId.collectAsState()
    val allNodes by mindMapViewModel.allNodes.collectAsState()
    val allEvents by calendarViewModel.allEvents.collectAsState()

    val currentSection = allSections.find { it.id == currentSectionId }
    val nodeCount = allNodes.count { it.sectionId == currentSectionId }
    val (pdfCount, recentPdfName) = ProductivityFilesSummary()
    val timerSummary = ProductivityTimerSummary()

    val todayKey = remember {
        val cal = Calendar.getInstance()
        "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    val todayLabel = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }
    val todayEvent = remember(allEvents, todayKey) {
        allEvents.firstOrNull { it.dateKey == todayKey && !it.isCompleted && (it.text.isNotBlank() || it.hasTimer) }
    }

    Surface(color = effectiveBackground, modifier = Modifier.fillMaxSize(), contentColor = effectiveText) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight

            val cardComposables: Map<String, @Composable () -> Unit> = mapOf(
                "mindmap" to {
                    ProductivityCard(title = "Mind Map", icon = Icons.Default.Hub, accent = effectiveAccent, cardColor = effectiveCardColor, onClick = onOpenMindMap) {
                        Text(
                            currentSection?.title ?: "No sections yet",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (currentSection != null) "$nodeCount boxes" else "Tap to create your first map",
                            color = ProdTextMuted, fontSize = 12.sp
                        )
                    }
                },
                "files" to {
                    ProductivityCard(title = "Files", icon = Icons.Default.Description, accent = ProdAccent2, cardColor = effectiveCardColor, onClick = onOpenFiles) {
                        Text("$pdfCount PDFs", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            recentPdfName ?: "No files yet",
                            color = ProdTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                "calendar" to {
                    ProductivityCard(title = "Calendar", icon = Icons.Default.CalendarMonth, accent = Color(0xFFFFD166), cardColor = effectiveCardColor, onClick = onOpenCalendar) {
                        Text(todayLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatOccasionsForDisplay(todayEvent).takeIf { it.isNotBlank() }
                                ?: if (todayEvent?.hasTimer == true) "Reminder set for today" else "No events today",
                            color = ProdTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                "timer" to {
                    ProductivityCard(title = "Timer", icon = Icons.Default.Timer, accent = Color(0xFFFF6E9F), cardColor = effectiveCardColor, onClick = onOpenTimer) {
                        Text(timerSummary, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
            val orderedCardIds = cardOrder.filter { it in cardComposables.keys }.let { ordered ->
                ordered + cardComposables.keys.filterNot { it in ordered }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 22.dp)
            ) {
                Text(
                    "Productivity",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(effectiveAccent, ProdAccent2)),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp
                    ),
                    modifier = Modifier.pointerInput("productivity-title-double-tap") {
                        detectTapGestures(onDoubleTap = { showThemeDialog = true })
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text("Everything in one place", color = ProdTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))

                if (isLandscape) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        orderedCardIds.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { id -> Box(modifier = Modifier.weight(1f)) { cardComposables[id]?.invoke() } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    ReorderableProductivityCards(
                        cardIds = orderedCardIds,
                        cardComposables = cardComposables,
                        onReorder = { newOrder ->
                            cardOrder = newOrder
                            saveProductivityCardOrder(prodContext, newOrder)
                        }
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        ProductivityThemeDialog(
            style = productivityStyle,
            onDismiss = { showThemeDialog = false },
            onStyleChange = { updated ->
                productivityStyle = updated
                saveProductivityStyle(prodContext, updated)
            }
        )
    }
}

@Composable
private fun ReorderableProductivityCards(
    cardIds: List<String>,
    cardComposables: Map<String, @Composable () -> Unit>,
    onReorder: (List<String>) -> Unit
) {
    val density = LocalDensity.current
    val itemHeight = 108.dp
    val itemSpacing = 14.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemSpacingPx = with(density) { itemSpacing.toPx() }
    val slotHeightPx = itemHeightPx + itemSpacingPx

    // canonical order: only re-synced from the parent-supplied cardIds while
    // nothing is currently being dragged, so an in-flight drag/settle is
    // never interrupted by an upstream recomposition.
    var order by remember { mutableStateOf(cardIds) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }
    val reorderScope = rememberCoroutineScope()
    // per-card settle Animatables keyed by id — they persist across drags
    // instead of being recreated, which is what let the gesture get "stuck"
    // after the first reorder.
    val settleOffsets = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }

    LaunchedEffect(cardIds) {
        if (draggingId == null) order = cardIds
    }

    val totalHeight = with(density) {
        (slotHeightPx * order.size - itemSpacingPx).coerceAtLeast(0f).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        order.forEachIndexed { index, id ->
            key(id) {
                val isDragging = draggingId == id
                val settle = settleOffsets.getOrPut(id) { Animatable(0f) }
                val animatedSlot = remember { Animatable((index * slotHeightPx)) }
                val targetY = index * slotHeightPx

                LaunchedEffect(targetY, isDragging) {
                    if (!isDragging) {
                        animatedSlot.animateTo(targetY, tween(220))
                    }
                }

                val currentY = if (isDragging) targetY + dragDeltaY else animatedSlot.value + settle.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .zIndex(if (isDragging) 2f else 0f)
                        .graphicsLayer { translationY = currentY }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    reorderScope.launch { settle.stop() }
                                    draggingId = id
                                    dragDeltaY = 0f
                                },
                                onDragEnd = {
                                    if (draggingId != id) return@detectDragGesturesAfterLongPress
                                    val finalOrder = order
                                    val finalIndex = finalOrder.indexOf(id).coerceAtLeast(0)
                                    val leftover = dragDeltaY
                                    draggingId = null
                                    dragDeltaY = 0f
                                    reorderScope.launch {
                                        animatedSlot.snapTo(finalIndex * slotHeightPx)
                                        settle.snapTo(leftover)
                                        settle.animateTo(0f, tween(150))
                                    }
                                    onReorder(finalOrder)
                                },
                                onDragCancel = {
                                    if (draggingId != id) return@detectDragGesturesAfterLongPress
                                    val finalIndex = order.indexOf(id).coerceAtLeast(0)
                                    val leftover = dragDeltaY
                                    draggingId = null
                                    dragDeltaY = 0f
                                    reorderScope.launch {
                                        animatedSlot.snapTo(finalIndex * slotHeightPx)
                                        settle.snapTo(leftover)
                                        settle.animateTo(0f, tween(160))
                                    }
                                }
                            ) { change, amount ->
                                change.consume()
                                if (draggingId != id) return@detectDragGesturesAfterLongPress
                                dragDeltaY += amount.y
                                val currentIndex = order.indexOf(id)
                                val direction = when {
                                    dragDeltaY >= slotHeightPx / 2 && currentIndex < order.lastIndex -> 1
                                    dragDeltaY <= -slotHeightPx / 2 && currentIndex > 0 -> -1
                                    else -> 0
                                }
                                if (direction != 0) {
                                    val reordered = order.toMutableList()
                                    val moved = reordered.removeAt(currentIndex)
                                    reordered.add(currentIndex + direction, moved)
                                    order = reordered
                                    dragDeltaY -= direction * slotHeightPx
                                }
                            }
                        }
                ) {
                    cardComposables[id]?.invoke()
                }
            }
        }
    }
}

@Composable
private fun ProductivityThemeDialog(
    style: ProductivityStyle,
    onDismiss: () -> Unit,
    onStyleChange: (ProductivityStyle) -> Unit
) {
    var picker by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ProdCard,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Productivity theme", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ProdColorRow("Background", Color(style.backgroundArgb ?: 0xFF0B0B12)) { picker = "background" }
                ProdColorRow("Card color", Color(style.cardArgb ?: 0xFF171826)) { picker = "card" }
                ProdColorRow("Text color", Color(style.textArgb ?: 0xFFFFFFFF)) { picker = "text" }
                ProdColorRow("Accent color", Color(style.accentArgb ?: 0xFF64FFDA)) { picker = "accent" }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { onStyleChange(ProductivityStyle()) }) {
                        Text("Reset to default", color = SoftNeutral)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = SoftNeutral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val config = when (picker) {
        "background" -> "Background color" to (style.backgroundArgb ?: 0xFF0B0B12)
        "card" -> "Card color" to (style.cardArgb ?: 0xFF171826)
        "text" -> "Text color" to (style.textArgb ?: 0xFFFFFFFF)
        "accent" -> "Accent color" to (style.accentArgb ?: 0xFF64FFDA)
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
private fun ProdColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
        )
    }
}

@Composable
private fun ProductivityCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    cardColor: Color,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, animationSpec = tween(120), label = "prodCardScale")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(cardColor, cardColor.copy(alpha = 0.92f))))
            .pointerInput(title) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Column(content = content)
            }
        }
    }
}