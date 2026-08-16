package com.example.mindmap.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindmap.ui.viewmodel.CalendarViewModel
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import com.example.mindmap.ui.viewmodel.SectionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ProdBg = Color(0xFF0B0B12)
private val ProdCard = Color(0xFF171826)
private val ProdCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
private val ProdAccent = Color(0xFF64FFDA)
private val ProdAccent2 = Color(0xFFBB86FC)
private val ProdTextMuted = Color(0xFFB7B7C6)

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

    Surface(color = ProdBg, modifier = Modifier.fillMaxSize(), contentColor = Color.White) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight

            val cards: List<@Composable () -> Unit> = listOf(
                {
                    ProductivityCard(title = "Mind Map", icon = Icons.Default.Hub, accent = ProdAccent, onClick = onOpenMindMap) {
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
                {
                    ProductivityCard(title = "Files", icon = Icons.Default.Description, accent = ProdAccent2, onClick = onOpenFiles) {
                        Text("$pdfCount PDFs", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            recentPdfName ?: "No files yet",
                            color = ProdTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                {
                    ProductivityCard(title = "Calendar", icon = Icons.Default.CalendarMonth, accent = Color(0xFFFFD166), onClick = onOpenCalendar) {
                        Text(todayLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            todayEvent?.text?.takeIf { it.isNotBlank() }
                                ?: if (todayEvent?.hasTimer == true) "Reminder set for today" else "No events today",
                            color = ProdTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                {
                    ProductivityCard(title = "Timer", icon = Icons.Default.Timer, accent = Color(0xFFFF6E9F), onClick = onOpenTimer) {
                        Text(timerSummary, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 22.dp)
            ) {
                Text("Productivity", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Everything in one place", color = ProdTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))

                if (isLandscape) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        cards.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { card -> Box(modifier = Modifier.weight(1f)) { card() } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        cards.forEach { it() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductivityCard(
    title: String,
    icon: ImageVector,
    accent: Color,
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
            .background(Brush.linearGradient(listOf(ProdCard, ProdCard.copy(alpha = 0.92f))))
            .border(1.dp, ProdCardBorder, RoundedCornerShape(22.dp))
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