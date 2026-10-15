package com.example.mindmap.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindmap.FocusAppDayUsage
import com.example.mindmap.FocusUsageStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val UsageCard = Color(0xFF13203B)
private val UsageBlue = Color(0xFF3B82F6)
private val UsageBlueSoft = Color(0xFF93C5FD)
private val UsageMuted = Color(0xFFB7C4DE)
private val UsageLine = Color.White.copy(alpha = 0.10f)
private val UsagePalette = listOf(
    Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF1D4ED8), Color(0xFF38BDF8),
    Color(0xFF2563EB), Color(0xFF93C5FD), Color(0xFF1E40AF), Color(0xFF0EA5E9),
    Color(0xFF6366F1), Color(0xFFBFDBFE)
)

private fun usageDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))

private fun formatUsageDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec / 60) % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        else -> "${totalSec}s"
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
}

private fun countLabel(app: FocusAppDayUsage): String =
    if (app.count == 0) "Continued from previous day"
    else "Blocked ${app.count} time${if (app.count == 1) "" else "s"}"

@Composable
internal fun FocusUsageDashboard() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            tick++
        }
    }
    val data = remember(tick) { FocusUsageStore.compute(context) }
    val days = remember(data) { data.keys.sortedDescending() }
    val today = days.firstOrNull() ?: LocalDate.now()

    // Ekta-i selected date + ekta-i chart-expanded state
    var chartExpanded by remember { mutableStateOf(false) }
    var visibleDays by remember { mutableIntStateOf(7) }
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selected = today
    val selectedApps = data[selected].orEmpty()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") {
            UsageSummaryCard(
                date = selected,
                today = today,
                apps = selectedApps
            )
        }
        item(key = "charts") {
            UsageChartsCard(selectedApps, chartExpanded) { chartExpanded = !chartExpanded }
        }
        item(key = "timeline_title") {
            Text(
                "Timeline",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
        items(days.take(visibleDays), key = { it.toEpochDay() }) { date ->
            UsageTimelineDay(date, today, data[date].orEmpty(), date == expandedDate) {
                expandedDate = if (expandedDate == date) null else date
            }
        }
        if (visibleDays < days.size) {
            item(key = "see_more") {
                UsageSeeMore { visibleDays = minOf(visibleDays + 7, days.size) }
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(
    date: LocalDate,
    today: LocalDate,
    apps: List<FocusAppDayUsage>
) {
    val total = apps.sumOf { it.millis }
    val maxMs = apps.maxOfOrNull { it.millis } ?: 0L
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UsageCard)
            .animateContentSize()
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(usageDate(date), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(dayLabel(date, today), color = UsageBlueSoft, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (apps.isEmpty()) {
            Text(
                "No apps were blocked on this day.",
                color = UsageMuted, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total blocked", color = UsageMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(formatUsageDuration(total), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            apps.forEachIndexed { i, app ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                UsageAppRow(app, maxMs)
            }
        }
    }
}

@Composable
private fun UsageAppRow(app: FocusAppDayUsage, maxMs: Long) {
    val target = if (maxMs > 0L) (app.millis.toFloat() / maxMs).coerceIn(0.03f, 1f) else 0f
    val frac by animateFloatAsState(target, tween(500), label = "usageBar")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FocusAppIcon(app.pkg, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(countLabel(app), color = UsageMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(UsageBlue)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsageDuration(app.millis), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("blocked", color = UsageMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UsageChartsCard(apps: List<FocusAppDayUsage>, expanded: Boolean, onToggle: () -> Unit) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chartsChevron")
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UsageCard)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Charts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore, contentDescription = "Toggle charts",
                tint = UsageBlueSoft, modifier = Modifier.rotate(angle)
            )
        }
        AnimatedVisibility(visible = expanded) {
            UsageDonut(apps)
        }
    }
}

@Composable
private fun UsageDonut(apps: List<FocusAppDayUsage>) {
    val items = apps.filter { it.millis > 0L }.sortedByDescending { it.millis }
    val total = items.sumOf { it.millis }
    if (items.isEmpty() || total <= 0L) {
        Text(
            "No blocking time recorded for this day.",
            color = UsageMuted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        return
    }
    val progress = remember(items) { Animatable(0f) }
    LaunchedEffect(items) { progress.animateTo(1f, tween(800)) }

    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                items.forEachIndexed { i, app ->
                    val sweep = 360f * app.millis / total * progress.value
                    val gap = if (items.size > 1) 2f else 0f
                    drawArc(
                        color = UsagePalette[i % UsagePalette.size],
                        startAngle = start,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatUsageDuration(total), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("blocked", color = UsageMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        items.forEachIndexed { i, app ->
            val p = app.millis * 100f / total
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(UsagePalette[i % UsagePalette.size])
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    app.name, color = Color.White, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(formatUsageDuration(app.millis), color = UsageMuted, fontSize = 13.sp)
                Text(
                    if (p < 1f) "<1%" else "${p.roundToInt()}%",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End, modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}

@Composable
private fun UsageTimelineDay(
    date: LocalDate,
    today: LocalDate,
    apps: List<FocusAppDayUsage>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "timelineChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(usageDate(date), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (date == today || date == today.minusDays(1)) {
                Spacer(Modifier.width(10.dp))
                Text(dayLabel(date, today), color = UsageBlueSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore, contentDescription = null,
                tint = UsageBlueSoft, modifier = Modifier.rotate(angle)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                if (apps.isEmpty()) {
                    Text("No blocked apps on this date", color = UsageMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    apps.forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FocusAppIcon(app.pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(countLabel(app), color = UsageMuted, fontSize = 11.sp)
                            }
                            Text(formatUsageDuration(app.millis), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageSeeMore(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(
            "See more",
            color = UsageBlueSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }
}