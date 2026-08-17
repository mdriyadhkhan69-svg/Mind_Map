package com.example.mindmap.ui.screens

import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.example.mindmap.data.CollapseAnimationStyle
import com.example.mindmap.data.CanvasViewport
import com.example.mindmap.data.LineEntity
import com.example.mindmap.data.MediaEntity
import com.example.mindmap.data.MediaType
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.RootCollisionBehavior
import com.example.mindmap.data.SectionEntity
import com.example.mindmap.data.SectionStyle
import com.example.mindmap.data.ThemeMode
import com.example.mindmap.ui.theme.SoftNeutral
import com.example.mindmap.ui.viewmodel.LineViewModel
import com.example.mindmap.ui.viewmodel.MediaViewModel
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import com.example.mindmap.ui.viewmodel.SectionViewModel
import com.example.mindmap.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.withTimeoutOrNull
// ---- preset color swatches for node color / glow color pickers ----
private val ColorSwatches = listOf(
    0xFF64FFDAL, 0xFFBB86FCL, 0xFFFF6E6EL, 0xFFFFD166L,
    0xFF6EC6FFL, 0xFF4CAF50L, 0xFFFFFFFFL, 0xFFFF9F1CL
)

data class MindMapColors(
    val background: Color,
    val rootBg: Color,
    val childBg: Color,
    val textPrimary: Color,
    val barBg: Color
)

private data class LayoutBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun overlaps(other: LayoutBounds, padding: Float = 0f): Boolean =
        left < other.right + padding && right > other.left - padding &&
            top < other.bottom + padding && bottom > other.top - padding

    fun translated(offset: Offset) = LayoutBounds(
        left + offset.x, top + offset.y, right + offset.x, bottom + offset.y
    )
}

private data class CopiedFormation(
    val rootId: Long,
    val nodes: List<NodeEntity>
)

private data class MediaPickerRequest(
    val node: NodeEntity,
    val replaceExisting: Boolean
)

private val DarkColors = MindMapColors(
    background = Color(0xFF0F1020),
    rootBg = Color(0xFF1E1E2E),
    childBg = Color(0xFF2A2A3C),
    textPrimary = Color.White,
    barBg = Color(0xFF1E1E2E)
)
private val WhiteColors = MindMapColors(
    background = Color(0xFFF4F4F8),
    rootBg = Color(0xFFFFFFFF),
    childBg = Color(0xFFECECF2),
    textPrimary = Color(0xFF1A1A1A),
    barBg = Color(0xFFFFFFFF)
)

private val AccentCyan = Color(0xFF64FFDA)
private val AccentPurple = Color(0xFFBB86FC)
private val GlassDark1 = Color(0xFF23243A)
private val GlassDark2 = Color(0xFF1A1B2E)
private const val MaxGlowIntensity = 1.5f

// ---- drag + tap + double-tap + press-feedback, combined; rememberUpdatedState
// use kora hoyeche jate callback stale na thake (double click e kaj na kora bug fix) ----
@Composable
fun Modifier.mindMapNode(
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit = {},
    onPressChange: (Boolean) -> Unit
): Modifier {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTripleTap by rememberUpdatedState(onTripleTap)
    val currentOnPressChange by rememberUpdatedState(onPressChange)
    val tapScope = rememberCoroutineScope()
    var tapCount by remember { mutableStateOf(0) }
    var tapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    return this.pointerInput(Unit) {
        coroutineScope {
            launch {
                detectTapGestures(
                    onPress = {
                        currentOnPressChange(true)
                        tryAwaitRelease()
                        currentOnPressChange(false)
                    },
                    onTap = {
                        tapCount += 1
                        tapJob?.cancel()
                        tapJob = tapScope.launch {
                            delay(260)
                            when (tapCount) {
                                1 -> currentOnTap()
                                2 -> currentOnDoubleTap()
                                else -> currentOnTripleTap()
                            }
                            tapCount = 0
                        }
                    }
                )
            }
            launch {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount)
                }
            }
        }
    }
}

private fun nodeCenter(
    id: Long,
    renderPositions: Map<Long, Offset>,
    livePositions: Map<Long, Offset>,
    boxSizes: Map<Long, IntSize>,
    node: NodeEntity?
): Offset {
    val pos = renderPositions[id] ?: livePositions[id] ?: node?.let { Offset(it.x, it.y) } ?: Offset.Zero
    val size = boxSizes[id] ?: IntSize(80, 40)
    return Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
}

private fun childTransitionAnchor(
    parent: NodeEntity?,
    parentRenderPos: Offset?,
    child: NodeEntity,
    boxSizes: Map<Long, IntSize>
): Offset? {
    if (parent == null || parentRenderPos == null) return null

    val parentSize = boxSizes[parent.id] ?: IntSize(100, 56)
    val childSize = boxSizes[child.id] ?: IntSize(80, 40)
    return Offset(
        x = parentRenderPos.x + parentSize.width - 8f,
        y = parentRenderPos.y + (parentSize.height - childSize.height) / 2f
    )
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq < 0.0001f) return (p - a).getDistance()
    var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
    t = t.coerceIn(0f, 1f)
    val projX = a.x + abx * t
    val projY = a.y + aby * t
    val dx = p.x - projX
    val dy = p.y - projY
    return sqrt(dx * dx + dy * dy)
}

private fun Offset.getDistance(): Float = sqrt(x * x + y * y)

@Composable
private fun LineLongPressTarget(
    start: Offset,
    end: Offset,
    onTap: () -> Unit,
    onLongPress: (isStartCloser: Boolean) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val direction = end - start
    val lengthPx = direction.getDistance()
    if (lengthPx < 1f) return

    val hitHeight = 52.dp
    val hitHeightPx = with(density) { hitHeight.toPx() }
    val angleDegrees = Math.toDegrees(atan2(direction.y.toDouble(), direction.x.toDouble())).toFloat()

    Box(
        modifier = Modifier
            .offset { IntOffset(start.x.roundToInt(), (start.y - hitHeightPx / 2f).roundToInt()) }
            .width(with(density) { lengthPx.toDp() })
            .height(hitHeight)
            .graphicsLayer {
                rotationZ = angleDegrees
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .pointerInput(start, end) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { localPosition -> onLongPress(localPosition.x <= lengthPx / 2f) }
                )
            }
            .zIndex(0.5f)
    )
}

@Composable
fun MindMapApp(
    viewModel: MindMapViewModel,
    settingsViewModel: SettingsViewModel,
    sectionViewModel: SectionViewModel,
    lineViewModel: LineViewModel,
    mediaViewModel: MediaViewModel,
    calendarViewModel: com.example.mindmap.ui.viewmodel.CalendarViewModel
) {
    val context = LocalContext.current
    TimerRunningWatcher()
    if (com.example.mindmap.PipState.isInPictureInPicture.value) {
        PipTimerContent()
        return
    }
    val homePreferences = remember(context) {
        context.getSharedPreferences("app_home", android.content.Context.MODE_PRIVATE)
    }
    var activeHome by remember { mutableStateOf("productivity") }
    var libraryPdf by remember { mutableStateOf<MediaEntity?>(null) }
    var libraryDocx by remember { mutableStateOf<MediaEntity?>(null) }
    var libraryPptx by remember { mutableStateOf<MediaEntity?>(null) }
    var libraryXlsx by remember { mutableStateOf<MediaEntity?>(null) }
    var externalPdfViewer by remember { mutableStateOf<MediaEntity?>(null) }

    fun openHome(home: String) {
        activeHome = home
        homePreferences.edit().putString("last_home", home).apply()
    }

    val pendingExternalPdfUri by com.example.mindmap.ExternalOpenState.pendingPdfUri
    LaunchedEffect(pendingExternalPdfUri) {
        val uriString = pendingExternalPdfUri ?: return@LaunchedEffect
        val externalUri = Uri.parse(uriString)
        val displayName = resolveAttachmentDisplayName(context, externalUri)
        externalPdfViewer = MediaEntity(
            sectionId = 0,
            nodeId = 0,
            type = MediaType.FILE,
            uri = uriString,
            displayName = displayName,
            mimeType = "application/pdf"
        )
        com.example.mindmap.ExternalOpenState.pendingPdfUri.value = null
    }

    val requestOpenTimer by com.example.mindmap.TimerNavigationState.requestOpenTimer
    LaunchedEffect(requestOpenTimer) {
        if (requestOpenTimer) {
            openHome("timer")
            com.example.mindmap.TimerNavigationState.requestOpenTimer.value = false
        }
    }

    LaunchedEffect(
        FloatingPopupSettingsState.enabled,
        QuickTimerState.hasStarted,
        StudyTimerState.subjects,
        TimerForegroundState.activeScreen,
        com.example.mindmap.AppForegroundState.isForeground.value
    ) {
        val quickActive = QuickTimerState.hasStarted
        val studyActive = StudyTimerState.subjects.any { it.isRunning }
        val relevantScreen = when {
            quickActive -> "quick"
            studyActive -> "study"
            else -> null
        }
        val isForeground = com.example.mindmap.AppForegroundState.isForeground.value
        val viewingOwnScreen = isForeground && relevantScreen != null && TimerForegroundState.activeScreen == relevantScreen
        val shouldShowPopup = FloatingPopupSettingsState.enabled && (quickActive || studyActive) && !viewingOwnScreen
        if (shouldShowPopup) {
            com.example.mindmap.FloatingTimerService.start(context)
        } else {
            com.example.mindmap.FloatingTimerService.stop(context)
        }
        if (!isForeground && !FloatingPopupSettingsState.enabled && QuickTimerState.isRunning) {
            QuickTimerState.pause(context)
        }
    }
    if (activeHome == "pdf_library") {
        PdfLibraryHomeDialog(
            onDismiss = { openHome("productivity") },
            onNavigateToMindMap = { openHome("mind_map") },
            onNavigateToTimer = { openHome("timer") },
            onNavigateToCalendar = { openHome("calendar") },
            onFileClick = { file ->
                val uriString = Uri.fromFile(file.file).toString()
                val mimeType = resolveAttachmentMimeType(file.name, null)
                when (file.extension) {
                    "pdf" -> libraryPdf = MediaEntity(
                        sectionId = 0, nodeId = 0, type = MediaType.FILE,
                        uri = uriString, displayName = file.name, mimeType = mimeType
                    )
                    "doc", "docx" -> libraryDocx = MediaEntity(
                        sectionId = 0, nodeId = 0, type = MediaType.FILE,
                        uri = uriString, displayName = file.name, mimeType = mimeType
                    )
                    "ppt", "pptx" -> libraryPptx = MediaEntity(
                        sectionId = 0, nodeId = 0, type = MediaType.FILE,
                        uri = uriString, displayName = file.name, mimeType = mimeType
                    )
                    "xls", "xlsx" -> libraryXlsx = MediaEntity(
                        sectionId = 0, nodeId = 0, type = MediaType.FILE,
                        uri = uriString, displayName = file.name, mimeType = mimeType
                    )
                    else -> openDeviceFileExternally(context, file)
                }
            }
        )
        libraryPdf?.let { media ->
            PdfViewerDialog(media = media, onDismiss = { libraryPdf = null })
        }
        libraryDocx?.let { media ->
            DocxViewerDialog(media = media, onDismiss = { libraryDocx = null })
        }
        libraryPptx?.let { media ->
            PptxViewerDialog(media = media, onDismiss = { libraryPptx = null })
        }
        libraryXlsx?.let { media ->
            XlsxViewerDialog(media = media, onDismiss = { libraryXlsx = null })
        }
    } else if (activeHome == "timer") {
        TimerHomeDialog(
            onDismiss = { openHome("productivity") },
            onNavigateToMindMap = { openHome("mind_map") },
            onNavigateToFiles = { openHome("pdf_library") },
            onNavigateToCalendar = { openHome("calendar") }
        )
    } else if (activeHome == "calendar") {
        CalendarHomeDialog(
            viewModel = calendarViewModel,
            onDismiss = { openHome("productivity") },
            onNavigateToMindMap = { openHome("mind_map") },
            onNavigateToFiles = { openHome("pdf_library") },
            onNavigateToTimer = { openHome("timer") }
        )
    } else if (activeHome == "mind_map") {
        androidx.activity.compose.BackHandler { openHome("productivity") }
        MindMapScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            sectionViewModel = sectionViewModel,
            lineViewModel = lineViewModel,
            mediaViewModel = mediaViewModel,
            onOpenPdfHome = { openHome("pdf_library") },
            onOpenTimer = { openHome("timer") },
            onOpenCalendar = { openHome("calendar") }
        )
    } else {
        ProductivityHomeScreen(
            mindMapViewModel = viewModel,
            sectionViewModel = sectionViewModel,
            calendarViewModel = calendarViewModel,
            onOpenMindMap = { openHome("mind_map") },
            onOpenFiles = { openHome("pdf_library") },
            onOpenTimer = { openHome("timer") },
            onOpenCalendar = { openHome("calendar") }
        )
    }

    externalPdfViewer?.let { media ->
        PdfViewerDialog(media = media, onDismiss = { externalPdfViewer = null })
    }
}

@Composable
fun MindMapScreen(
    viewModel: MindMapViewModel,
    settingsViewModel: SettingsViewModel,
    sectionViewModel: SectionViewModel,
    lineViewModel: LineViewModel,
    mediaViewModel: MediaViewModel,
    onOpenPdfHome: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = LocalContext.current

    val allNodes by viewModel.allNodes.collectAsState()
    val glow by settingsViewModel.glowIntensity.collectAsState()
    val collapseStyle by settingsViewModel.collapseAnimationStyle.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val glowColorArgb by settingsViewModel.glowColorArgb.collectAsState()
    val smartRootLayoutEnabled by settingsViewModel.smartRootLayoutEnabled.collectAsState()
    val rootCollisionBehavior by settingsViewModel.rootCollisionBehavior.collectAsState()
    val multipleRootsEnabled by settingsViewModel.multipleRootsEnabled.collectAsState()
    val applySectionStyleToAll by settingsViewModel.applySectionStyleToAll.collectAsState()
    val globalSectionStyle by settingsViewModel.globalSectionStyle.collectAsState()
    val glowColor = Color(glowColorArgb)
    val themeColors = if (themeMode == ThemeMode.WHITE) WhiteColors else DarkColors

    val allSections by sectionViewModel.allSections.collectAsState()
    val currentSectionId by sectionViewModel.currentSectionId.collectAsState()
    val currentSection = allSections.find { it.id == currentSectionId }
    val emptyZoomEnabledFlow = remember { flowOf(false) }
    val emptyZoomScaleFlow = remember { flowOf(1f) }
    val emptyLongPressPanFlow = remember { flowOf(false) }
    val zoomEnabledFlow = remember(currentSectionId) {
        currentSectionId?.let { settingsViewModel.zoomEnabled(it) } ?: emptyZoomEnabledFlow
    }
    val zoomScaleFlow = remember(currentSectionId) {
        currentSectionId?.let { settingsViewModel.zoomScale(it) } ?: emptyZoomScaleFlow
    }
    val longPressPanFlow = remember(currentSectionId) {
        currentSectionId?.let { settingsViewModel.longPressPanEnabled(it) } ?: emptyLongPressPanFlow
    }
    val zoomEnabled by zoomEnabledFlow.collectAsState(initial = false)
    val storedZoomScale by zoomScaleFlow.collectAsState(initial = 1f)
    val longPressPanEnabled by longPressPanFlow.collectAsState(initial = false)
    val emptySectionStyleFlow = remember { flowOf(SectionStyle()) }
    val sectionStyleFlow = remember(currentSectionId) {
        currentSectionId?.let { settingsViewModel.sectionStyle(it) } ?: emptySectionStyleFlow
    }
    val currentSectionStyle by sectionStyleFlow.collectAsState(initial = SectionStyle())
    val emptyViewportFlow = remember { flowOf<CanvasViewport?>(CanvasViewport()) }
    val viewportFlow = remember(currentSectionId) {
        currentSectionId?.let { sectionId ->
            settingsViewModel.canvasViewport(sectionId).map { viewport -> viewport as CanvasViewport? }
        } ?: emptyViewportFlow
    }
    val storedViewport by viewportFlow.collectAsState(initial = null)
    val effectiveSectionStyle = if (applySectionStyleToAll) globalSectionStyle else currentSectionStyle
    val sectionBackground = effectiveSectionStyle.backgroundArgb?.let { Color(it) } ?: themeColors.background
    val boxTextColor = effectiveSectionStyle.textArgb?.let { Color(it) } ?: themeColors.textPrimary
    val sectionBoxColor = effectiveSectionStyle.boxArgb?.let { Color(it) }
    val completionColor = effectiveSectionStyle.completionArgb?.let { Color(it) } ?: Color(0xFF4CAF50)

    val allLines by lineViewModel.allLines.collectAsState()
    val linesInSection = remember(allLines, currentSectionId) { allLines.filter { it.sectionId == currentSectionId } }
    val allMedia by mediaViewModel.allMedia.collectAsState()
    val mediaByNode = remember(allMedia, currentSectionId) {
        allMedia.asSequence()
            .filter { it.sectionId == currentSectionId }
            .groupBy { it.nodeId }
            .mapValues { (_, media) -> media.firstOrNull { it.type == MediaType.IMAGE } ?: media.first() }
    }

    var showAddDateDialog by remember { mutableStateOf(false) }
    var addChildDialogFor by remember { mutableStateOf<NodeEntity?>(null) }
    var addTextDialogFor by remember { mutableStateOf<NodeEntity?>(null) }
    var boxStyleDialogFor by remember { mutableStateOf<NodeEntity?>(null) }
    var textStylePanelFor by remember { mutableStateOf<NodeEntity?>(null) }
    var lineStyleDialogFor by remember { mutableStateOf<LineEntity?>(null) }
    var menuForNodeId by remember { mutableStateOf<Long?>(null) }
    var editOptionsForNodeId by remember { mutableStateOf<Long?>(null) }
    var fileOptionsForNodeId by remember { mutableStateOf<Long?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showRenameSectionDialog by remember { mutableStateOf(false) }
    var sectionListExpanded by remember { mutableStateOf(false) }
    var sectionEditExpanded by remember { mutableStateOf(false) }
    var attachLineFromId by remember { mutableStateOf<Long?>(null) }
    var lineMenu by remember { mutableStateOf<Pair<LineEntity, Boolean>?>(null) } // line, isEndACloser
    var treeLineMenuFor by remember { mutableStateOf<Pair<NodeEntity, NodeEntity>?>(null) }
    var treeLineStyleFor by remember { mutableStateOf<NodeEntity?>(null) }
    var copiedFormation by remember { mutableStateOf<CopiedFormation?>(null) }
    var copiedNoticeForId by remember { mutableStateOf<Long?>(null) }
    var actionOptionsForNodeId by remember { mutableStateOf<Long?>(null) }
    var pasteOptionsForNodeId by remember { mutableStateOf<Long?>(null) }
    var includeSourceBoxForPaste by remember { mutableStateOf<Boolean?>(null) }
    var inlineTextEditingNodeId by remember { mutableStateOf<Long?>(null) }
    var inlineTextDraft by remember { mutableStateOf("") }
    var sectionTitleStyleFor by remember { mutableStateOf<SectionEntity?>(null) }
    var mediaPickerRequest by remember { mutableStateOf<MediaPickerRequest?>(null) }
    var mediaViewer by remember { mutableStateOf<MediaEntity?>(null) }
    var pdfViewer by remember { mutableStateOf<MediaEntity?>(null) }
    var docxViewer by remember { mutableStateOf<MediaEntity?>(null) }
    var pptxViewer by remember { mutableStateOf<MediaEntity?>(null) }
    var xlsxViewer by remember { mutableStateOf<MediaEntity?>(null) }
    var mediaFocusNodeId by remember { mutableStateOf<Long?>(null) }
    var attachmentErrorMessage by remember { mutableStateOf<String?>(null) }
    val mediaPickerScope = rememberCoroutineScope()


    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = mediaPickerRequest
        if (uri != null && request != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = resolveAttachmentDisplayName(context, uri)
            val mimeType = resolveAttachmentMimeType(
                displayName,
                context.contentResolver.getType(uri)
            )
            mediaPickerScope.launch {
                val savedUri = runCatching {
                    withContext(Dispatchers.IO) { copyAttachmentToAppStorage(context, uri, displayName) }
                }.getOrNull()
                val mediaType = if (mimeType.startsWith("image/")) MediaType.IMAGE else MediaType.FILE
                val attachmentUri = savedUri ?: uri
                fun attachTo(mediaNode: NodeEntity) {
                    mediaViewModel.replaceNodeMedia(
                        MediaEntity(
                            sectionId = mediaNode.sectionId,
                            nodeId = mediaNode.id,
                            type = mediaType,
                            uri = attachmentUri.toString(),
                            displayName = displayName,
                            mimeType = mimeType
                        )
                    )
                }
                if (request.replaceExisting) {
                    attachTo(request.node)
                    if (mediaType == MediaType.FILE) viewModel.updateLabel(request.node, displayName)
                } else {
                    viewModel.addMediaChildNode(request.node, displayName, ::attachTo)
                }
                mediaViewer = null
            }
        }
        mediaPickerRequest = null
    }

    var canvasOffset by remember(currentSectionId) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var localZoomScale by remember(currentSectionId) { mutableStateOf(1f) }
    var hasZoomInteraction by remember(currentSectionId) { mutableStateOf(false) }
    var hasCanvasInteraction by remember(currentSectionId) { mutableStateOf(false) }
    var isViewportReady by remember(currentSectionId) { mutableStateOf(false) }
    var isInitialCanvasReady by remember(currentSectionId) { mutableStateOf(false) }
    var longPressPanArmed by remember(currentSectionId) { mutableStateOf(false) }
    var rootLayoutAnimationsEnabled by remember(currentSectionId) { mutableStateOf(false) }

    LaunchedEffect(zoomEnabled, storedZoomScale) {
        if (!zoomEnabled) {
            val previousScale = localZoomScale.coerceAtLeast(0.01f)
            if (previousScale != 1f && viewportSize != IntSize.Zero) {
                val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                canvasOffset = center - (center - canvasOffset) * (1f / previousScale)
                hasCanvasInteraction = true
            }
            localZoomScale = 1f
            hasZoomInteraction = false
        } else if (!hasZoomInteraction) {
            localZoomScale = storedZoomScale
        }
    }

    LaunchedEffect(localZoomScale, zoomEnabled, hasZoomInteraction) {
        if (zoomEnabled && hasZoomInteraction) {
            delay(350)
            currentSectionId?.let { settingsViewModel.setZoomScale(it, localZoomScale) }
        }
    }

    LaunchedEffect(currentSectionId, storedViewport, hasCanvasInteraction) {
        val viewport = storedViewport ?: return@LaunchedEffect
        if (!hasCanvasInteraction) {
            canvasOffset = Offset(viewport.x, viewport.y)
        }
        isViewportReady = true
    }

    LaunchedEffect(isViewportReady) {
        if (isViewportReady) {
            delay(350)
            rootLayoutAnimationsEnabled = true
        }
    }

    LaunchedEffect(currentSectionId, isViewportReady) {
        if (isViewportReady) {
            delay(420)
            isInitialCanvasReady = true
        }
    }

    LaunchedEffect(currentSectionId, canvasOffset, hasCanvasInteraction) {
        val sectionId = currentSectionId ?: return@LaunchedEffect
        if (hasCanvasInteraction) {
            delay(350)
            settingsViewModel.setCanvasViewport(sectionId, canvasOffset.x, canvasOffset.y)
        }
    }

    val livePositions = remember { mutableStateMapOf<Long, Offset>() }
    val boxSizes = remember { mutableStateMapOf<Long, IntSize>() }
    val renderPositions = remember { mutableStateMapOf<Long, Offset>() }
    val progressMap = remember { mutableStateMapOf<Long, Float>() }
    val looseDragStartPositions = remember { mutableStateMapOf<String, Offset>() }
    val manuallyPlacedRootIds = remember(currentSectionId) { mutableStateMapOf<Long, Boolean>() }

    val nodesInSection = remember(allNodes, currentSectionId) {
        allNodes.filter { it.sectionId == currentSectionId }
    }

    LaunchedEffect(nodesInSection) {
        nodesInSection.forEach { node ->
            if (!livePositions.containsKey(node.id)) livePositions[node.id] = Offset(node.x, node.y)
        }
    }

    val childrenMap = nodesInSection.groupBy { it.parentId }
    val nodesById = remember(nodesInSection) { nodesInSection.associateBy { it.id } }

    LaunchedEffect(currentSectionId, nodesInSection, mediaFocusNodeId, viewportSize) {
        val nodeId = mediaFocusNodeId ?: return@LaunchedEffect
        val node = nodesById[nodeId] ?: return@LaunchedEffect
        if (viewportSize == IntSize.Zero) return@LaunchedEffect
        delay(140)
        canvasOffset = Offset(
            viewportSize.width / 2f - node.x - 60f,
            viewportSize.height / 2f - node.y - 40f
        )
        hasCanvasInteraction = true
        mediaFocusNodeId = null
    }

    fun subtreeNodes(root: NodeEntity): List<NodeEntity> {
        val result = mutableListOf<NodeEntity>()
        fun visit(node: NodeEntity) {
            result += node
            childrenMap[node.id].orEmpty().forEach(::visit)
        }
        visit(root)
        return result
    }

    fun visibleDescendants(root: NodeEntity): List<NodeEntity> {
        val result = mutableListOf<NodeEntity>()
        fun visit(parent: NodeEntity) {
            if (!parent.isExpanded) return
            childrenMap[parent.id].orEmpty().forEach { child ->
                result += child
                visit(child)
            }
        }
        visit(root)
        return result
    }

    fun basePosition(node: NodeEntity): Offset = livePositions[node.id] ?: Offset(node.x, node.y)

    fun nodeBounds(node: NodeEntity, rootOffset: Offset): LayoutBounds {
        val position = basePosition(node) + rootOffset
        val fallbackWidth = with(density) { (if (node.parentId == null) 86.dp else 70.dp).toPx() } * node.widthScale
        val fallbackHeight = with(density) { (if (node.parentId == null) 42.dp else 32.dp).toPx() } * node.heightScale
        val size = boxSizes[node.id]
        val width = size?.width?.toFloat() ?: fallbackWidth
        val height = size?.height?.toFloat() ?: fallbackHeight
        return LayoutBounds(position.x, position.y, position.x + width, position.y + height)
    }

    val rootNodes = childrenMap[null].orEmpty().sortedBy { basePosition(it).y }
    val rootExpansionStates = rootNodes.map { it.id to it.isExpanded }
    LaunchedEffect(rootExpansionStates, smartRootLayoutEnabled) {
        manuallyPlacedRootIds.clear()
    }
    val rootAutoOffsets = mutableMapOf<Long, Offset>()
    val hiddenRootIds = mutableSetOf<Long>()
    rootNodes.forEach { rootAutoOffsets[it.id] = Offset.Zero }
    if (smartRootLayoutEnabled) {
        rootNodes.forEach { expandedRoot ->
            if (!expandedRoot.isExpanded || expandedRoot.id in hiddenRootIds) return@forEach
            val sourceOffset = rootAutoOffsets[expandedRoot.id] ?: Offset.Zero
            val occupiedBounds = (listOf(nodeBounds(expandedRoot, sourceOffset)) + visibleDescendants(expandedRoot)
                .map { nodeBounds(it, sourceOffset) })
                .let { bounds ->
                    LayoutBounds(
                        left = bounds.minOf { it.left },
                        top = bounds.minOf { it.top },
                        right = bounds.maxOf { it.right },
                        bottom = bounds.maxOf { it.bottom }
                    )
                }
            if (visibleDescendants(expandedRoot).isEmpty()) return@forEach

            rootNodes.forEach { otherRoot ->
                if (
                    otherRoot.id == expandedRoot.id ||
                    otherRoot.id in hiddenRootIds ||
                    otherRoot.id in manuallyPlacedRootIds
                ) return@forEach
                val otherBounds = nodeBounds(otherRoot, rootAutoOffsets[otherRoot.id] ?: Offset.Zero)
                if (!occupiedBounds.overlaps(otherBounds, padding = with(density) { 12.dp.toPx() })) return@forEach

                if (rootCollisionBehavior == RootCollisionBehavior.HIDE) {
                    hiddenRootIds += otherRoot.id
                } else {
                    val margin = with(density) { 24.dp.toPx() }
                    val otherObstacles = rootNodes
                        .filter { it.id != expandedRoot.id && it.id != otherRoot.id && it.id !in hiddenRootIds }
                        .map { nodeBounds(it, rootAutoOffsets[it.id] ?: Offset.Zero) }
                    val candidates = listOf(
                        Offset(0f, occupiedBounds.bottom + margin - otherBounds.top),
                        Offset(occupiedBounds.right + margin - otherBounds.left, 0f),
                        Offset(occupiedBounds.left - margin - otherBounds.right, 0f)
                    )
                    val movement = candidates.firstOrNull { candidate ->
                        val candidateBounds = otherBounds.translated(candidate)
                        !candidateBounds.overlaps(occupiedBounds) &&
                            otherObstacles.none { candidateBounds.overlaps(it, padding = margin / 2f) }
                    } ?: candidates.minBy { it.getDistance() }
                    val currentOffset = rootAutoOffsets[otherRoot.id] ?: Offset.Zero
                    rootAutoOffsets[otherRoot.id] = currentOffset + movement
                }
            }
        }
    }
    val hiddenNodeIds = hiddenRootIds.flatMap { rootId ->
        rootNodes.find { it.id == rootId }?.let(::subtreeNodes).orEmpty().map { it.id }
    }.toSet()
    val treeConnectorTargets = buildList<Pair<NodeEntity, NodeEntity>> {
        val roots = childrenMap[null].orEmpty().sortedBy { it.orderIndex }
        for (index in 1 until roots.size) {
            val target = roots[index]
            if (!target.isConnectorHidden && roots[index - 1].id !in hiddenNodeIds && target.id !in hiddenNodeIds) {
                add(roots[index - 1] to target)
            }
        }
        fun addChildConnectors(parent: NodeEntity) {
            if (!parent.isExpanded || parent.id in hiddenNodeIds) return
            childrenMap[parent.id].orEmpty().sortedBy { it.orderIndex }.forEach { child ->
                if (!child.isConnectorHidden && child.id !in hiddenNodeIds) add(parent to child)
                addChildConnectors(child)
            }
        }
        roots.forEach(::addChildConnectors)
    }

    fun nodeDisplayProgress(nodeId: Long): Float {
        if (nodeId in hiddenNodeIds) return 0f
        val node = nodesById[nodeId] ?: return 0f
        progressMap[nodeId]?.let { return it }

        var currentNode = node
        while (currentNode.parentId != null) {
            val parent = nodesById[currentNode.parentId] ?: return 0f
            if (!parent.isExpanded) return 0f
            currentNode = parent
        }
        return 1f
    }

    fun lineVisibility(line: LineEntity): Float {
        val startVisibility = line.nodeAId?.let(::nodeDisplayProgress) ?: 1f
        val endVisibility = line.nodeBId?.let(::nodeDisplayProgress) ?: 1f
        return min(startVisibility, endVisibility)
    }

    LaunchedEffect(copiedNoticeForId) {
        val copiedId = copiedNoticeForId ?: return@LaunchedEffect
        delay(1_200)
        if (copiedNoticeForId == copiedId) copiedNoticeForId = null
    }

    LaunchedEffect(attachmentErrorMessage) {
        val message = attachmentErrorMessage ?: return@LaunchedEffect
        delay(650)
        if (attachmentErrorMessage == message) attachmentErrorMessage = null
    }

    val onAddMedia: (NodeEntity) -> Unit = { node ->
        mediaPickerRequest = MediaPickerRequest(node, replaceExisting = false)
        mediaPicker.launch(arrayOf("*/*"))
    }
    val onReplaceMedia: (NodeEntity) -> Unit = { node ->
        mediaPickerRequest = MediaPickerRequest(node, replaceExisting = true)
        mediaPicker.launch(arrayOf("*/*"))
    }
    val onOpenMedia: (MediaEntity) -> Unit = { media ->
        mediaPickerScope.launch {
            val currentUri = Uri.parse(media.uri)
            val resolvedMedia = if (currentUri.authority == "${context.packageName}.attachments") {
                media
            } else {
                val copiedUri = withContext(Dispatchers.IO) {
                    runCatching { copyAttachmentToAppStorage(context, currentUri, media.displayName) }.getOrNull()
                }
                copiedUri?.let { media.copy(uri = it.toString()) } ?: media
            }
            if (resolvedMedia.uri != media.uri) mediaViewModel.update(resolvedMedia)
            val extension = resolvedMedia.displayName.substringAfterLast('.', "").lowercase()
            if (resolvedMedia.type == MediaType.IMAGE) {
                mediaViewer = resolvedMedia
            } else if (resolvedMedia.mimeType == "application/pdf" || resolvedMedia.displayName.endsWith(".pdf", ignoreCase = true)) {
                pdfViewer = resolvedMedia
            } else if (extension == "docx" || extension == "doc") {
                docxViewer = resolvedMedia
            } else if (extension == "pptx" || extension == "ppt") {
                pptxViewer = resolvedMedia
            } else if (extension == "xlsx" || extension == "xls") {
                xlsxViewer = resolvedMedia
            } else {
                runCatching {
                    val attachmentUri = Uri.parse(resolvedMedia.uri)
                    val openIntent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(attachmentUri, resolvedMedia.mimeType)
                    openIntent.clipData = ClipData.newRawUri(resolvedMedia.displayName, attachmentUri)
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.packageManager.queryIntentActivities(openIntent, 0).forEach { activity ->
                        runCatching {
                            context.grantUriPermission(
                                activity.activityInfo.packageName,
                                attachmentUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
                    context.startActivity(openIntent)
                }
            }
        }
    }

    val onCopyNode: (NodeEntity) -> Unit = { node ->
        copiedFormation = CopiedFormation(node.id, subtreeNodes(node).map { it.copy() })
        copiedNoticeForId = node.id
        includeSourceBoxForPaste = null
    }
    val onPasteNode: (NodeEntity, Boolean) -> Unit = { node, includeText ->
        copiedFormation?.let { formation ->
            viewModel.pasteSubtree(node, formation.rootId, formation.nodes, includeText, includeSourceBoxForPaste)
            includeSourceBoxForPaste = null
        }
    }
    
    val onTapNode: (NodeEntity) -> Unit = { node ->
        val pending = attachLineFromId
        if (pending != null) {
            if (pending != node.id) {
                currentSectionId?.let { lineViewModel.addLine(it, pending, node.id) }
            }
            attachLineFromId = null
        } else {
            viewModel.toggleExpand(node, multipleRootsEnabled)
        }
    }
    val onDoubleTapNode: (NodeEntity) -> Unit = { node ->
        menuForNodeId = node.id
        editOptionsForNodeId = null
        fileOptionsForNodeId = null
    }
    val onDragNode: (NodeEntity, Offset, Boolean) -> Unit = { node, delta, moveSubtree ->
        if (node.parentId == null && node.id !in manuallyPlacedRootIds) {
            val autoOffset = rootAutoOffsets[node.id] ?: Offset.Zero
            if (autoOffset != Offset.Zero) {
                subtreeNodes(node).forEach { subtreeNode ->
                    val current = livePositions[subtreeNode.id] ?: Offset(subtreeNode.x, subtreeNode.y)
                    livePositions[subtreeNode.id] = current + autoOffset
                }
            }
            manuallyPlacedRootIds[node.id] = true
        }
        val movingNodes = if (moveSubtree || (node.parentId == null && !node.isExpanded)) {
            subtreeNodes(node)
        } else {
            listOf(node)
        }
        movingNodes.forEach { movingNode ->
            val current = livePositions[movingNode.id] ?: Offset(movingNode.x, movingNode.y)
            livePositions[movingNode.id] = Offset(current.x + delta.x, current.y + delta.y)
        }
    }
    val onDragEndNode: (NodeEntity, Boolean) -> Unit = { node, moveSubtree ->
        val movingNodes = if (
            (moveSubtree ||
                (node.parentId == null && (!node.isExpanded || node.id in manuallyPlacedRootIds)))
        ) {
            subtreeNodes(node)
        } else {
            listOf(node)
        }
        viewModel.updatePositions(movingNodes.map { movingNode ->
            val finalPosition = livePositions[movingNode.id] ?: Offset(movingNode.x, movingNode.y)
            movingNode.copy(x = finalPosition.x, y = finalPosition.y)
        })
    }

    fun findLineNear(worldPos: Offset): Pair<LineEntity, Boolean>? {
        val thresholdPx = with(density) { 44.dp.toPx() }
        var best: LineEntity? = null
        var bestIsACloser = true
        var bestDist = Float.MAX_VALUE
        linesInSection.forEach { line ->
            val posA = if (line.nodeAId != null) nodeCenter(line.nodeAId, renderPositions, livePositions, boxSizes, nodesById[line.nodeAId]) else Offset(line.looseAX, line.looseAY)
            val posB = if (line.nodeBId != null) nodeCenter(line.nodeBId, renderPositions, livePositions, boxSizes, nodesById[line.nodeBId]) else Offset(line.looseBX, line.looseBY)
            val d = distanceToSegment(worldPos, posA, posB)
            if (d < thresholdPx && d < bestDist) {
                bestDist = d
                best = line
                bestIsACloser = (worldPos - posA).getDistance() < (worldPos - posB).getDistance()
            }
        }
        return best?.let { it to bestIsACloser }
    }

    fun showLineMenuAt(worldPos: Offset) {
        findLineNear(worldPos)?.let { lineMenu = it }
    }

    fun dismissFloatingPanels() {
        menuForNodeId = null
        editOptionsForNodeId = null
        attachLineFromId = null
        sectionListExpanded = false
        sectionEditExpanded = false
        lineMenu = null
        lineStyleDialogFor = null
        boxStyleDialogFor = null
        textStylePanelFor = null
        treeLineMenuFor = null
        treeLineStyleFor = null
        inlineTextEditingNodeId = null
        sectionTitleStyleFor = null
    }

    val currentShowLineMenuAt by rememberUpdatedState(::showLineMenuAt)
    val currentDismissFloatingPanels by rememberUpdatedState(::dismissFloatingPanels)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sectionBackground)
            .onGloballyPositioned { viewportSize = it.size }
            .pointerInput(currentSectionId, longPressPanEnabled) {
                detectTapGestures(
                    onPress = {
                        longPressPanArmed = false
                        tryAwaitRelease()
                    },
                    onTap = { currentDismissFloatingPanels() },
                    onDoubleTap = {
                        currentSectionId?.let { viewModel.collapseAllRoots(it) }
                        currentDismissFloatingPanels()
                    },
                    onLongPress = {
                        if (longPressPanEnabled) longPressPanArmed = true
                    }
                )
            }
            .pointerInput(currentSectionId, zoomEnabled, longPressPanEnabled) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val previousScale = localZoomScale.coerceAtLeast(0.01f)
                    val nextScale = if (zoomEnabled) {
                        (previousScale * gestureZoom).coerceIn(0.2f, 3f)
                    } else {
                        1f
                    }
                    val allowPan = !longPressPanEnabled || longPressPanArmed || gestureZoom != 1f
                    if (allowPan) {
                        canvasOffset += pan
                        hasCanvasInteraction = true
                    }
                    if (zoomEnabled && nextScale != previousScale) {
                        canvasOffset = centroid - (centroid - canvasOffset) * (nextScale / previousScale)
                        localZoomScale = nextScale
                        hasZoomInteraction = true
                        hasCanvasInteraction = true
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(canvasOffset.x.roundToInt(), canvasOffset.y.roundToInt()) }
                .graphicsLayer {
                    scaleX = localZoomScale
                    scaleY = localZoomScale
                    alpha = if (isInitialCanvasReady) 1f else 0f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            // parent-child tree connector lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val roots = childrenMap[null].orEmpty().sortedBy { it.orderIndex }
                for (i in 0 until roots.size - 1) {
                    if (roots[i].id !in hiddenNodeIds && roots[i + 1].id !in hiddenNodeIds) {
                        drawTreeConnector(roots[i], roots[i + 1], renderPositions, livePositions, boxSizes, Color.Cyan, 1f)
                    }
                }
                fun drawChildren(node: NodeEntity, color: Color) {
                    if (!node.isExpanded || node.id in hiddenNodeIds) return
                    childrenMap[node.id].orEmpty().sortedBy { it.id }.forEach { child ->
                        if (child.id in hiddenNodeIds) return@forEach
                        val alpha = progressMap[child.id] ?: 1f
                        drawTreeConnector(node, child, renderPositions, livePositions, boxSizes, color, alpha)
                        drawChildren(child, Color(0xFFBB86FC))
                    }
                }
                roots.forEach { drawChildren(it, Color.Yellow) }
            }

            // custom attach-lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                linesInSection.forEach { line ->
                    val lineAlpha = lineVisibility(line)
                    if (lineAlpha <= 0.02f) return@forEach
                    val posA = if (line.nodeAId != null) nodeCenter(line.nodeAId, renderPositions, livePositions, boxSizes, nodesById[line.nodeAId]) else Offset(line.looseAX, line.looseAY)
                    val posB = if (line.nodeBId != null) nodeCenter(line.nodeBId, renderPositions, livePositions, boxSizes, nodesById[line.nodeBId]) else Offset(line.looseBX, line.looseBY)
                    drawLine(
                        color = Color(line.colorArgb).copy(alpha = lineAlpha),
                        start = posA, end = posB, strokeWidth = line.strokeWidth
                    )
                }
            }

            treeConnectorTargets.forEach { (from, to) ->
                val visibility = min(nodeDisplayProgress(from.id), nodeDisplayProgress(to.id))
                if (visibility <= 0.02f) return@forEach
                key("tree-line-target-${from.id}-${to.id}") {
                    LineLongPressTarget(
                        start = nodeCenter(from.id, renderPositions, livePositions, boxSizes, from),
                        end = nodeCenter(to.id, renderPositions, livePositions, boxSizes, to),
                        onTap = { currentDismissFloatingPanels() },
                        onLongPress = { treeLineMenuFor = from to to }
                    )
                }
            }

            linesInSection.forEach { line ->
                if (lineVisibility(line) <= 0.02f) return@forEach
                val start = if (line.nodeAId != null) {
                    nodeCenter(line.nodeAId, renderPositions, livePositions, boxSizes, nodesById[line.nodeAId])
                } else {
                    Offset(line.looseAX, line.looseAY)
                }
                val end = if (line.nodeBId != null) {
                    nodeCenter(line.nodeBId, renderPositions, livePositions, boxSizes, nodesById[line.nodeBId])
                } else {
                    Offset(line.looseBX, line.looseBY)
                }
                key("line-target-${line.id}") {
                    LineLongPressTarget(
                        start = start,
                        end = end,
                        onTap = { currentDismissFloatingPanels() },
                        onLongPress = { isStartCloser -> lineMenu = line to isStartCloser }
                    )
                }
            }

            childrenMap[null].orEmpty().sortedBy { it.orderIndex }.forEach { root ->
                key(root.id) {
                    RenderNodeTree(
                        node = root, isRoot = true, visible = true, parentRenderPos = null,
                        parentNode = null,
                        rootAutoOffset = rootAutoOffsets[root.id] ?: Offset.Zero,
                        autoOffsetAnimationEnabled = rootLayoutAnimationsEnabled && root.id !in manuallyPlacedRootIds,
                        hiddenByLayout = root.id in hiddenRootIds,
                        childrenMap = childrenMap, mediaByNode = mediaByNode, livePositions = livePositions,
                        renderPositions = renderPositions, progressMap = progressMap,
                        boxSizes = boxSizes, glow = glow, glowColor = glowColor, themeColors = themeColors,
                        boxTextColor = boxTextColor, sectionBoxColor = sectionBoxColor, completionColor = completionColor,
                        collapseStyle = collapseStyle,
                        menuForNodeId = menuForNodeId,
                        editOptionsForNodeId = editOptionsForNodeId,
                        fileOptionsForNodeId = fileOptionsForNodeId,
                        actionOptionsForNodeId = actionOptionsForNodeId,
                        pasteOptionsForNodeId = pasteOptionsForNodeId,
                        includeSourceBoxForPaste = includeSourceBoxForPaste,
                        onSetMenu = {
                            menuForNodeId = it
                            if (it == null) {
                                editOptionsForNodeId = null
                                fileOptionsForNodeId = null
                                actionOptionsForNodeId = null
                                pasteOptionsForNodeId = null
                                includeSourceBoxForPaste = null
                            }
                        },
                        onTapNode = onTapNode, onDoubleTapNode = onDoubleTapNode,
                        onDragNode = onDragNode, onDragEndNode = onDragEndNode,
                        onRemove = { viewModel.deleteNode(it) },
                        onRemoveMedia = { mediaViewModel.delete(it.id) },
                        onComplete = { viewModel.toggleDone(it) },
                        onCopy = onCopyNode,
                        onPaste = onPasteNode,
                        copiedNoticeForId = copiedNoticeForId,
                        copiedSourceNodeIds = copiedFormation?.nodes?.mapTo(mutableSetOf()) { it.id }.orEmpty(),
                        onAddChild = { addChildDialogFor = it },
                        onAddMedia = onAddMedia,
                        onReplaceMedia = onReplaceMedia,
                        onOpenMedia = onOpenMedia,
                        inlineTextEditingNodeId = inlineTextEditingNodeId,
                        onAddText = { addTextDialogFor = it },
                        onSubmitText = { textNode, label ->
                            if (label.isNotBlank()) {
                                viewModel.updateLabel(textNode, label)
                            }
                            inlineTextEditingNodeId = null
                            inlineTextDraft = ""
                        },
                        onCancelText = { inlineTextEditingNodeId = null; inlineTextDraft = "" },
                        onInlineTextDraftChange = { inlineTextDraft = it },
                        onChangeColor = { boxStyleDialogFor = it },
                        onChangeText = { textStylePanelFor = it },
                        onShowEditOptions = { editOptionsForNodeId = it },
                        onShowFileOptions = { fileOptionsForNodeId = it },
                        onShowActionOptions = {
                            actionOptionsForNodeId = it
                            if (it != null) {
                                editOptionsForNodeId = null
                                fileOptionsForNodeId = null
                                pasteOptionsForNodeId = null
                            }
                        },
                        onShowPasteOptions = {
                            pasteOptionsForNodeId = it
                            if (it != null) {
                                editOptionsForNodeId = null
                                actionOptionsForNodeId = null
                            }
                        },
                        onSetIncludeSourceBoxForPaste = { includeSourceBoxForPaste = it },
                        onAttachLine = { attachLineFromId = it.id },
                        attachLineFromId = attachLineFromId
                    )
                }
            }

            // draggable loose-end dots for detached lines
            linesInSection.forEach { line ->
                if (lineVisibility(line) > 0.02f && line.nodeAId == null) {
                    key("lineA-${line.id}") {
                        LooseDot(
                            pos = Offset(line.looseAX, line.looseAY),
                            onDragStart = { looseDragStartPositions["lineA-${line.id}"] = it },
                            onPositionChange = { position ->
                                lineViewModel.updateLine(line.copy(looseAX = position.x, looseAY = position.y))
                            },
                            onDragEnd = { finalPos ->
                                val hit = nodesInSection.find { n ->
                                    val p = livePositions[n.id] ?: Offset(n.x, n.y)
                                    val s = boxSizes[n.id] ?: IntSize(80, 40)
                                    finalPos.x in p.x..(p.x + s.width) && finalPos.y in p.y..(p.y + s.height)
                                }
                                if (hit != null) {
                                    lineViewModel.updateLine(line.copy(nodeAId = hit.id))
                                    looseDragStartPositions.remove("lineA-${line.id}")
                                } else {
                                    val start = looseDragStartPositions.remove("lineA-${line.id}") ?: finalPos
                                    lineViewModel.updateLine(line.copy(looseAX = start.x, looseAY = start.y))
                                }
                            }
                        )
                    }
                }
                if (lineVisibility(line) > 0.02f && line.nodeBId == null) {
                    key("lineB-${line.id}") {
                        LooseDot(
                            pos = Offset(line.looseBX, line.looseBY),
                            onDragStart = { looseDragStartPositions["lineB-${line.id}"] = it },
                            onPositionChange = { position ->
                                lineViewModel.updateLine(line.copy(looseBX = position.x, looseBY = position.y))
                            },
                            onDragEnd = { finalPos ->
                                val hit = nodesInSection.find { n ->
                                    val p = livePositions[n.id] ?: Offset(n.x, n.y)
                                    val s = boxSizes[n.id] ?: IntSize(80, 40)
                                    finalPos.x in p.x..(p.x + s.width) && finalPos.y in p.y..(p.y + s.height)
                                }
                                if (hit != null) {
                                    lineViewModel.updateLine(line.copy(nodeBId = hit.id))
                                    looseDragStartPositions.remove("lineB-${line.id}")
                                } else {
                                    val start = looseDragStartPositions.remove("lineB-${line.id}") ?: finalPos
                                    lineViewModel.updateLine(line.copy(looseBX = start.x, looseBY = start.y))
                                }
                            }
                        )
                    }
                }
            }
        }

        SectionTopBar(
            sections = allSections,
            currentSection = currentSection,
            listExpanded = sectionListExpanded,
            onToggleList = { sectionListExpanded = !sectionListExpanded; sectionEditExpanded = false },
            editExpanded = sectionEditExpanded,
            onToggleEdit = { sectionEditExpanded = !sectionEditExpanded; sectionListExpanded = false },
            onSelectSection = { sectionViewModel.selectSection(it) },
            onReorder = { sectionViewModel.reorderSections(it) },
            onRename = { showRenameSectionDialog = true },
            onAddSection = { sectionViewModel.addSection() },
            onRemoveSection = { currentSection?.let { sectionViewModel.removeSection(it) } },
            sectionNameColor = currentSectionStyle.titleArgb?.let { Color(it) } ?: themeColors.textPrimary,
            onSectionNameLongPress = { currentSection?.let { sectionTitleStyleFor = it } },
            themeColors = themeColors
        )

        sectionTitleStyleFor?.let { section ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(41f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                shadowElevation = 10.dp
            ) {
                SectionTitleStylePanel(
                    initialColorArgb = currentSectionStyle.titleArgb,
                    themeMode = themeMode,
                    onDismiss = { sectionTitleStyleFor = null },
                    onUpdate = { color -> settingsViewModel.setSectionTitleColor(section.id, color) },
                    onReset = { settingsViewModel.clearSectionTitleColor(section.id) }
                )
            }
        }

        if (inlineTextEditingNodeId != null) {
            val editNodeId = inlineTextEditingNodeId!!
            val editingNode = nodesById[editNodeId]
            if (editingNode != null) {
                val nodeCanvasPos = livePositions[editNodeId] ?: Offset(editingNode.x, editingNode.y)
                val nodeVisualY = canvasOffset.y + nodeCanvasPos.y * localZoomScale
                val nodeVisualYDp = with(density) { nodeVisualY.toDp() }
                val panelTopDp = (nodeVisualYDp - 8.dp - 96.dp).coerceAtLeast(58.dp)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = panelTopDp)
                        .zIndex(50f),
                    shape = RoundedCornerShape(14.dp),
                    color = themeColors.barBg,
                    shadowElevation = 12.dp
                ) {
                    Column(modifier = Modifier.width(130.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) { detectTapGestures(onTap = {
                                    if (inlineTextDraft.isNotBlank()) {
                                        viewModel.updateLabel(editingNode, inlineTextDraft)
                                    }
                                    inlineTextEditingNodeId = null
                                    inlineTextDraft = ""
                                }) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Done",
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(themeColors.textPrimary.copy(alpha = 0.08f))
                                .height(1.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) { detectTapGestures(onTap = {
                                    inlineTextEditingNodeId = null
                                    inlineTextDraft = ""
                                }) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Cancel",
                                color = themeColors.textPrimary,
                                fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(20f)) {
            IconButton(onClick = { showMainMenu = true }) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = themeColors.textPrimary)
            }
            DropdownMenu(
                expanded = showMainMenu,
                onDismissRequest = { showMainMenu = false },
                containerColor = themeColors.barBg
            ) {
                DropdownMenuItem(
                    text = { Text("Mind map settings", color = themeColors.textPrimary) },
                    onClick = { showMainMenu = false; showSettingsDialog = true }
                )
                DropdownMenuItem(
                    text = { Text("Files", color = themeColors.textPrimary) },
                    onClick = { showMainMenu = false; onOpenPdfHome() }
                )
                DropdownMenuItem(
                    text = { Text("Timer", color = themeColors.textPrimary) },
                    onClick = { showMainMenu = false; onOpenTimer() }
                )
                DropdownMenuItem(
                    text = { Text("Calendar", color = themeColors.textPrimary) },
                    onClick = { showMainMenu = false; onOpenCalendar() }
                )
            }
        }

        GlassFab(
            onClick = { showAddDateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        )

        if (attachLineFromId != null) {
            Text(
                "একটা box-এ ট্যাপ করুন সংযুক্ত করতে (বাতিল করতে খালি জায়গায় ট্যাপ করুন)",
                color = themeColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        lineMenu?.let { (line, isACloser) ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .zIndex(40f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                contentColor = themeColors.textPrimary,
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.width(180.dp)) {
                    DropdownMenuItem(text = { Text("Remove", color = themeColors.textPrimary) }, onClick = {
                        lineViewModel.removeLine(line); lineMenu = null
                    })
                    DropdownMenuItem(text = { Text("Color & thickness", color = themeColors.textPrimary) }, onClick = {
                        lineStyleDialogFor = line
                        lineMenu = null
                    })
                    DropdownMenuItem(text = { Text("Move Attachment", color = themeColors.textPrimary) }, onClick = {
                        val posA = if (line.nodeAId != null) nodeCenter(line.nodeAId, renderPositions, livePositions, boxSizes, nodesById[line.nodeAId]) else Offset(line.looseAX, line.looseAY)
                        val posB = if (line.nodeBId != null) nodeCenter(line.nodeBId, renderPositions, livePositions, boxSizes, nodesById[line.nodeBId]) else Offset(line.looseBX, line.looseBY)
                        val updated = if (isACloser) {
                            val dir = posB - posA
                            val len = dir.getDistance().coerceAtLeast(1f)
                            val pull = Offset(dir.x / len, dir.y / len) * 28f
                            line.copy(nodeAId = null, looseAX = posA.x + pull.x, looseAY = posA.y + pull.y)
                        } else {
                            val dir = posA - posB
                            val len = dir.getDistance().coerceAtLeast(1f)
                            val pull = Offset(dir.x / len, dir.y / len) * 28f
                            line.copy(nodeBId = null, looseBX = posB.x + pull.x, looseBY = posB.y + pull.y)
                        }
                        lineViewModel.updateLine(updated)
                        lineMenu = null
                    })
                }
            }
        }

        treeLineMenuFor?.let { (sourceNode, targetNode) ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(40f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                contentColor = themeColors.textPrimary,
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.width(180.dp)) {
                    DropdownMenuItem(text = { Text("Remove", color = themeColors.textPrimary) }, onClick = {
                        viewModel.hideConnector(targetNode)
                        treeLineMenuFor = null
                    })
                    DropdownMenuItem(text = { Text("Color & thickness", color = themeColors.textPrimary) }, onClick = {
                        treeLineStyleFor = targetNode
                        treeLineMenuFor = null
                    })
                    DropdownMenuItem(text = { Text("Move Attachment", color = themeColors.textPrimary) }, onClick = {
                        val start = nodeCenter(sourceNode.id, renderPositions, livePositions, boxSizes, sourceNode)
                        val end = nodeCenter(targetNode.id, renderPositions, livePositions, boxSizes, targetNode)
                        val direction = start - end
                        val distance = direction.getDistance().coerceAtLeast(1f)
                        val pull = Offset(direction.x / distance, direction.y / distance) * 28f
                        val defaultColor = when {
                            targetNode.parentId == null -> 0xFF00FFFF
                            nodesById[targetNode.parentId]?.parentId == null -> 0xFFFFFF00
                            else -> 0xFFBB86FC
                        }
                        lineViewModel.addDetachedLine(
                            sectionId = targetNode.sectionId,
                            nodeAId = sourceNode.id,
                            looseBX = end.x + pull.x,
                            looseBY = end.y + pull.y,
                            colorArgb = targetNode.connectorColorArgb ?: defaultColor,
                            strokeWidth = targetNode.connectorStrokeWidth
                        )
                        viewModel.hideConnector(targetNode)
                        treeLineMenuFor = null
                    })
                }
            }
        }

        lineStyleDialogFor?.let { line ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(41f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                shadowElevation = 10.dp
            ) {
                LineStyleDialog(
                    line = line,
                    themeMode = themeMode,
                    onDismiss = { lineStyleDialogFor = null },
                    onUpdate = lineViewModel::updateLine
                )
            }
        }

        treeLineStyleFor?.let { targetNode ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(41f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                shadowElevation = 10.dp
            ) {
                TreeLineStylePanel(
                    node = targetNode,
                    defaultColorArgb = when {
                        targetNode.parentId == null -> 0xFF00FFFF
                        nodesById[targetNode.parentId]?.parentId == null -> 0xFFFFFF00
                        else -> 0xFFBB86FC
                    },
                    themeMode = themeMode,
                    onDismiss = { treeLineStyleFor = null },
                    onUpdate = { color, thickness -> viewModel.updateConnectorStyle(targetNode, color, thickness) },
                    onReset = { viewModel.resetConnectorStyle(targetNode) }
                )
            }
        }

        if (showAddDateDialog) {
            StyledInputDialog("Add Title", "", { showAddDateDialog = false }) { label ->
                val zoom = localZoomScale.coerceAtLeast(0.01f)
                val x = (viewportSize.width / 2f - canvasOffset.x) / zoom - 56f
                val y = (viewportSize.height / 2f - canvasOffset.y) / zoom - 26f
                currentSectionId?.let { viewModel.addRootDateNode(it, label, x, y) }
                showAddDateDialog = false
            }
        }

        addChildDialogFor?.let { parent ->
            StyledInputDialog(
                if (parent.parentId == null) "Add Task" else "Add Sub-task", "",
                { addChildDialogFor = null }
            ) { label -> viewModel.addChildNode(parent, label); addChildDialogFor = null }
        }

        addTextDialogFor?.let { node ->
            StyledInputDialog("Add Text", "", { addTextDialogFor = null }) { label ->
                viewModel.updateLabel(node, label)
                addTextDialogFor = null
            }
        }

        textStylePanelFor?.let { node ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(41f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                shadowElevation = 10.dp
            ) {
                TextStylePanel(
                    node = node,
                    themeColors = themeColors,
                    onDismiss = { textStylePanelFor = null },
                    onPreview = { label, size, weight, textColor ->
                        viewModel.updateTextStyle(node, label, size, weight, textColor)
                    }
                )
            }
        }

        boxStyleDialogFor?.let { node ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(41f),
                shape = RoundedCornerShape(18.dp),
                color = themeColors.barBg,
                shadowElevation = 10.dp
            ) {
                BoxStyleDialog(
                    node = node,
                    themeMode = themeMode,
                    onDismiss = { boxStyleDialogFor = null },
                    onStyleChange = { color, textColor, width, height ->
                        viewModel.updateBoxStyle(node, color, textColor, width, height)
                    }
                )
            }
        }

        if (showRenameSectionDialog && currentSection != null) {
            StyledInputDialog("Rename Section", currentSection.title, { showRenameSectionDialog = false }) { newTitle ->
                sectionViewModel.renameSection(currentSection, newTitle)
                showRenameSectionDialog = false
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentGlow = glow,
                currentCollapseStyle = collapseStyle,
                currentTheme = themeMode,
                currentGlowColor = glowColorArgb,
                zoomEnabled = zoomEnabled,
                longPressPanEnabled = longPressPanEnabled,
                smartRootLayoutEnabled = smartRootLayoutEnabled,
                rootCollisionBehavior = rootCollisionBehavior,
                multipleRootsEnabled = multipleRootsEnabled,
                currentSectionStyle = effectiveSectionStyle,
                applySectionStyleToAll = applySectionStyleToAll,
                sections = allSections,
                media = allMedia,
                onDismiss = { showSettingsDialog = false },
                onMediaClick = { media ->
                    viewModel.expandAncestors(media.nodeId)
                    mediaFocusNodeId = media.nodeId
                    sectionViewModel.selectSection(media.sectionId)
                    showSettingsDialog = false
                    mediaViewer = if (media.type == MediaType.IMAGE) media else null
                },
                onGlowChange = { settingsViewModel.setGlow(it) },
                onCollapseStyleChange = { settingsViewModel.setCollapseAnimationStyle(it) },
                onThemeChange = { mode ->
                    settingsViewModel.setThemeMode(mode)
                    currentSectionId?.let { sectionId ->
                        settingsViewModel.clearSectionBackground(sectionId, applySectionStyleToAll)
                    }
                },
                onGlowColorChange = { settingsViewModel.setGlowColor(it) },
                onZoomEnabledChange = { enabled ->
                    currentSectionId?.let { settingsViewModel.setZoomEnabled(it, enabled) }
                    if (enabled) currentSectionId?.let { settingsViewModel.setLongPressPanEnabled(it, false) }
                },
                onLongPressPanEnabledChange = { enabled ->
                    currentSectionId?.let { settingsViewModel.setLongPressPanEnabled(it, enabled) }
                },
                onSmartRootLayoutEnabledChange = { settingsViewModel.setSmartRootLayoutEnabled(it) },
                onRootCollisionBehaviorChange = { settingsViewModel.setRootCollisionBehavior(it) },
                onMultipleRootsEnabledChange = { settingsViewModel.setMultipleRootsEnabled(it) },
                onSectionBackgroundChange = { color ->
                    currentSectionId?.let { settingsViewModel.setSectionBackground(it, color, applySectionStyleToAll) }
                },
                onSectionTextColorChange = { color ->
                    currentSectionId?.let { settingsViewModel.setSectionTextColor(it, color, applySectionStyleToAll) }
                },
                onSectionTextColorReset = {
                    currentSectionId?.let { settingsViewModel.clearSectionTextColor(it, applySectionStyleToAll) }
                },
                onSectionBoxColorChange = { color ->
                    currentSectionId?.let { settingsViewModel.setSectionBoxColor(it, color, applySectionStyleToAll) }
                },
                onSectionBoxColorReset = {
                    currentSectionId?.let { settingsViewModel.clearSectionBoxColor(it, applySectionStyleToAll) }
                },
                onSectionCompletionColorChange = { color ->
                    currentSectionId?.let { settingsViewModel.setSectionCompletionColor(it, color, applySectionStyleToAll) }
                },
                onSectionCompletionColorReset = {
                    currentSectionId?.let { settingsViewModel.clearSectionCompletionColor(it, applySectionStyleToAll) }
                },
                onApplySectionStyleToAllChange = { enabled ->
                    settingsViewModel.setApplySectionStyleToAll(enabled, currentSectionStyle)
                }
            )
        }

        mediaViewer?.let { media ->
            MediaViewerDialog(
                media = media,
                onDismiss = { mediaViewer = null },
                onReplace = {
                    nodesById[media.nodeId]?.let { node ->
                        mediaPickerRequest = MediaPickerRequest(node, replaceExisting = true)
                        mediaPicker.launch(arrayOf("image/*"))
                    }
                },
                onRemove = {
                    mediaViewModel.delete(media.id)
                    mediaViewer = null
                },
                onRotate = { rotation -> mediaViewModel.update(media.copy(rotationDegrees = rotation)) }
            )
        }

        pdfViewer?.let { media ->
            PdfViewerDialog(media = media, onDismiss = { pdfViewer = null })
        }
        docxViewer?.let { media ->
            DocxViewerDialog(media = media, onDismiss = { docxViewer = null })
        }
        pptxViewer?.let { media ->
            PptxViewerDialog(media = media, onDismiss = { pptxViewer = null })
        }
        xlsxViewer?.let { media ->
            XlsxViewerDialog(media = media, onDismiss = { xlsxViewer = null })
        }

        attachmentErrorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
                action = {
                    TextButton(onClick = { attachmentErrorMessage = null }) {
                        Text("OK", color = AccentCyan)
                    }
                }
            ) { Text(message) }
        }
    }
}

private fun DrawScope.drawTreeConnector(
    a: NodeEntity, b: NodeEntity,
    renderPositions: Map<Long, Offset>, livePositions: Map<Long, Offset>, boxSizes: Map<Long, IntSize>,
    color: Color, alpha: Float
) {
    if (b.isConnectorHidden) return
    val posA = renderPositions[a.id] ?: livePositions[a.id] ?: Offset(a.x, a.y)
    val posB = renderPositions[b.id] ?: livePositions[b.id] ?: Offset(b.x, b.y)
    val sizeA = boxSizes[a.id] ?: IntSize(80, 40)
    val sizeB = boxSizes[b.id] ?: IntSize(80, 40)
    drawLine(
        color = (b.connectorColorArgb?.let { Color(it) } ?: color).copy(alpha = 0.6f * alpha),
        start = Offset(posA.x + sizeA.width / 2f, posA.y + sizeA.height / 2f),
        end = Offset(posB.x + sizeB.width / 2f, posB.y + sizeB.height / 2f),
        strokeWidth = b.connectorStrokeWidth
    )
}

@Composable
fun LooseDot(
    pos: Offset,
    onDragStart: (Offset) -> Unit,
    onPositionChange: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    var current by remember(pos) { mutableStateOf(pos) }
    Box(
        modifier = Modifier
            .offset { IntOffset((current.x - 10).roundToInt(), (current.y - 10).roundToInt()) }
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .border(2.dp, AccentCyan, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart(current) },
                    onDragEnd = { onDragEnd(current) },
                    onDragCancel = { onDragEnd(current) }
                ) { change, amount ->
                    change.consume()
                    current = Offset(current.x + amount.x, current.y + amount.y)
                    onPositionChange(current)
                }
            }
            .zIndex(15f)
    )
}

@Composable
fun RenderNodeTree(
    node: NodeEntity,
    isRoot: Boolean,
    visible: Boolean,
    parentRenderPos: Offset?,
    parentNode: NodeEntity?,
    rootAutoOffset: Offset,
    autoOffsetAnimationEnabled: Boolean,
    hiddenByLayout: Boolean,
    childrenMap: Map<Long?, List<NodeEntity>>,
    mediaByNode: Map<Long, MediaEntity>,
    livePositions: SnapshotStateMap<Long, Offset>,
    renderPositions: SnapshotStateMap<Long, Offset>,
    progressMap: SnapshotStateMap<Long, Float>,
    boxSizes: SnapshotStateMap<Long, IntSize>,
    glow: Float,
    glowColor: Color,
    themeColors: MindMapColors,
    boxTextColor: Color,
    sectionBoxColor: Color?,
    completionColor: Color,
    collapseStyle: CollapseAnimationStyle,
    menuForNodeId: Long?,
    editOptionsForNodeId: Long?,
    fileOptionsForNodeId: Long?,
    actionOptionsForNodeId: Long?,
    pasteOptionsForNodeId: Long?,
    includeSourceBoxForPaste: Boolean?,
    onSetMenu: (Long?) -> Unit,
    onTapNode: (NodeEntity) -> Unit,
    onDoubleTapNode: (NodeEntity) -> Unit,
    onDragNode: (NodeEntity, Offset, Boolean) -> Unit,
    onDragEndNode: (NodeEntity, Boolean) -> Unit,
    onRemove: (NodeEntity) -> Unit,
    onRemoveMedia: (MediaEntity) -> Unit,
    onComplete: (NodeEntity) -> Unit,
    onCopy: (NodeEntity) -> Unit,
    onPaste: (NodeEntity, Boolean) -> Unit,
    copiedNoticeForId: Long?,
    copiedSourceNodeIds: Set<Long>,
    onAddChild: (NodeEntity) -> Unit,
    onAddMedia: (NodeEntity) -> Unit,
    onReplaceMedia: (NodeEntity) -> Unit,
    onOpenMedia: (MediaEntity) -> Unit,
    inlineTextEditingNodeId: Long?,
    onAddText: (NodeEntity) -> Unit,
    onSubmitText: (NodeEntity, String) -> Unit,
    onCancelText: () -> Unit,
    onChangeColor: (NodeEntity) -> Unit,
    onChangeText: (NodeEntity) -> Unit,
    onShowEditOptions: (Long?) -> Unit,
    onShowFileOptions: (Long?) -> Unit,
    onShowActionOptions: (Long?) -> Unit,
    onShowPasteOptions: (Long?) -> Unit,
    onSetIncludeSourceBoxForPaste: (Boolean?) -> Unit,
    onAttachLine: (NodeEntity) -> Unit,
    attachLineFromId: Long?,
    onInlineTextDraftChange: (String) -> Unit = {}
) {
    val baseTargetPos = livePositions[node.id] ?: Offset(node.x, node.y)
    val animatedRootAutoOffset by animateOffsetAsState(
        targetValue = rootAutoOffset,
        animationSpec = tween(260),
        label = "rootAutoOffset"
    )
    val appliedRootAutoOffset = if (isRoot && autoOffsetAnimationEnabled) {
        animatedRootAutoOffset
    } else {
        rootAutoOffset
    }
    val targetPos = baseTargetPos + appliedRootAutoOffset

    val progress by animateFloatAsState(
        targetValue = if (!hiddenByLayout && (isRoot || visible)) 1f else 0f,
        animationSpec = tween(260),
        label = "nodeProgress"
    )

    val effectiveParentPos = childTransitionAnchor(parentNode, parentRenderPos, node, boxSizes) ?: targetPos
    val renderPos = when {
        isRoot -> targetPos
        visible -> lerp(effectiveParentPos, targetPos, progress)
        collapseStyle == CollapseAnimationStyle.LINE_RETRACT -> lerp(effectiveParentPos, targetPos, progress)
        else -> targetPos
    }

    LaunchedEffect(renderPos, progress, isRoot) {
        renderPositions[node.id] = renderPos
        progressMap[node.id] = if (isRoot) 1f else progress
    }

    val shouldCompose = !hiddenByLayout && (isRoot || visible || progress > 0.001f)
    val canPasteHere = copiedSourceNodeIds.isNotEmpty() && node.id !in copiedSourceNodeIds

    if (shouldCompose) {
        Box(
            modifier = Modifier
                .offset { IntOffset(renderPos.x.roundToInt(), renderPos.y.roundToInt()) }
                .graphicsLayer { alpha = if (isRoot) 1f else progress }
                .zIndex(if (isRoot) 20f else 1f)
        ) {
            NodeBox(
                node = node, isRoot = isRoot, glow = glow, glowColor = glowColor, themeColors = themeColors,
                boxTextColor = boxTextColor, sectionBoxColor = sectionBoxColor, completionColor = completionColor,
                media = mediaByNode[node.id], onOpenMedia = onOpenMedia,
                isAttachSource = (attachLineFromId == node.id),
                onSizeChanged = { size -> boxSizes[node.id] = size },
                onDrag = { delta, moveSubtree -> onDragNode(node, delta, moveSubtree) },
                onDragEnd = { moveSubtree -> onDragEndNode(node, moveSubtree) },
                onTap = { onTapNode(node) },
                onDoubleTap = { onDoubleTapNode(node) },
                isInlineTextEditing = inlineTextEditingNodeId == node.id,
                onSubmitText = { label -> onSubmitText(node, label) },
                onCancelText = onCancelText,
                onInlineTextChange = { if (inlineTextEditingNodeId == node.id) onInlineTextDraftChange(it) }
            )

            DropdownMenu(expanded = menuForNodeId == node.id, onDismissRequest = { onSetMenu(null) }) {
                if (fileOptionsForNodeId == node.id) {
                    DropdownMenuItem(text = { Text("Replace file") }, onClick = { onReplaceMedia(node); onSetMenu(null) })
                    DropdownMenuItem(text = { Text("Remove file") }, onClick = {
                        mediaByNode[node.id]?.let(onRemoveMedia)
                        onSetMenu(null)
                    })
                    DropdownMenuItem(text = { Text("Back") }, onClick = {
                        onShowFileOptions(null)
                        onShowEditOptions(node.id)
                    })
                } else if (editOptionsForNodeId == node.id) {
                    DropdownMenuItem(text = { Text("Change box") }, onClick = { onChangeColor(node); onSetMenu(null) })
                    DropdownMenuItem(text = { Text("Change text") }, onClick = { onChangeText(node); onSetMenu(null) })
                    if (mediaByNode[node.id]?.type == MediaType.FILE) {
                        DropdownMenuItem(text = { Text("Change file") }, onClick = { onShowFileOptions(node.id) })
                    }
                    DropdownMenuItem(text = { Text("Back") }, onClick = { onShowEditOptions(null) })
                } else if (pasteOptionsForNodeId == node.id) {
                    DropdownMenuItem(text = { Text("With text") }, onClick = { onPaste(node, true); onSetMenu(null) })
                    DropdownMenuItem(text = { Text("Without text") }, onClick = { onPaste(node, false); onSetMenu(null) })
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (includeSourceBoxForPaste == true) "✓ With main box" else "With main box",
                                color = if (includeSourceBoxForPaste == true) AccentCyan else themeColors.textPrimary
                            )
                        },
                        onClick = { onSetIncludeSourceBoxForPaste(true) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (includeSourceBoxForPaste == false) "✓ Without main box" else "Without main box",
                                color = if (includeSourceBoxForPaste == false) AccentCyan else themeColors.textPrimary
                            )
                        },
                        onClick = { onSetIncludeSourceBoxForPaste(false) }
                    )
                    DropdownMenuItem(text = { Text("Back") }, onClick = { onShowPasteOptions(null); onShowActionOptions(node.id) })
                } else if (actionOptionsForNodeId == node.id) {
                    DropdownMenuItem(text = { Text(if (copiedNoticeForId == node.id) "Copied" else "Copy") }, onClick = {
                        onCopy(node)
                        onShowActionOptions(node.id)
                    })
                    if (canPasteHere) {
                        DropdownMenuItem(text = { Text("Paste") }, onClick = { onShowPasteOptions(node.id) })
                    }
                    DropdownMenuItem(text = { Text("Attach line") }, onClick = { onAttachLine(node); onSetMenu(null) })
                    DropdownMenuItem(text = { Text("Add file") }, onClick = { onAddMedia(node); onSetMenu(null) })
                    DropdownMenuItem(text = { Text(if (node.isDone) "Undo complete" else "Complete") }, onClick = {
                        onComplete(node)
                        onSetMenu(null)
                    })
                    DropdownMenuItem(text = { Text("Back") }, onClick = { onShowActionOptions(null) })
                } else {
                    DropdownMenuItem(text = { Text(if (isRoot) "Add Task" else "Add Sub-task") }, onClick = { onAddChild(node); onSetMenu(null) })
                    if (node.label.isBlank()) {
                        DropdownMenuItem(text = { Text("Add text") }, onClick = { onAddText(node); onSetMenu(null) })
                    }
                    DropdownMenuItem(text = { Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold) }, onClick = { onShowActionOptions(node.id) })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { onShowEditOptions(node.id) })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(node); onSetMenu(null) })
                }
            }
        }
    }

    val childVisible = !hiddenByLayout && (isRoot || visible) && node.isExpanded
    childrenMap[node.id].orEmpty().sortedBy { it.id }.forEach { child ->
        key(child.id) {
            RenderNodeTree(
                node = child, isRoot = false, visible = childVisible, parentRenderPos = renderPos,
                parentNode = node,
                rootAutoOffset = appliedRootAutoOffset,
                autoOffsetAnimationEnabled = false,
                hiddenByLayout = hiddenByLayout,
                childrenMap = childrenMap, mediaByNode = mediaByNode, livePositions = livePositions,
                renderPositions = renderPositions, progressMap = progressMap,
                boxSizes = boxSizes, glow = glow, glowColor = glowColor, themeColors = themeColors,
                boxTextColor = boxTextColor, sectionBoxColor = sectionBoxColor, completionColor = completionColor,
                collapseStyle = collapseStyle,
                menuForNodeId = menuForNodeId, editOptionsForNodeId = editOptionsForNodeId,
                fileOptionsForNodeId = fileOptionsForNodeId,
                actionOptionsForNodeId = actionOptionsForNodeId, pasteOptionsForNodeId = pasteOptionsForNodeId,
                includeSourceBoxForPaste = includeSourceBoxForPaste,
                onSetMenu = onSetMenu,
                onTapNode = onTapNode, onDoubleTapNode = onDoubleTapNode,
                onDragNode = onDragNode, onDragEndNode = onDragEndNode,
                onRemove = onRemove, onRemoveMedia = onRemoveMedia, onComplete = onComplete, onCopy = onCopy, onPaste = onPaste,
                copiedNoticeForId = copiedNoticeForId, copiedSourceNodeIds = copiedSourceNodeIds, onAddChild = onAddChild,
                onAddMedia = onAddMedia, onReplaceMedia = onReplaceMedia, onOpenMedia = onOpenMedia,
                inlineTextEditingNodeId = inlineTextEditingNodeId, onAddText = onAddText, onSubmitText = onSubmitText,
                onCancelText = onCancelText,
                onChangeColor = onChangeColor, onChangeText = onChangeText, onShowEditOptions = onShowEditOptions,
                onShowFileOptions = onShowFileOptions,
                onShowActionOptions = onShowActionOptions, onShowPasteOptions = onShowPasteOptions,
                onSetIncludeSourceBoxForPaste = onSetIncludeSourceBoxForPaste,
                onAttachLine = onAttachLine,
                attachLineFromId = attachLineFromId,
                onInlineTextDraftChange = onInlineTextDraftChange
            )
        }
    }
}

@Composable
fun NodeBox(
    node: NodeEntity,
    isRoot: Boolean,
    glow: Float,
    glowColor: Color,
    themeColors: MindMapColors,
    boxTextColor: Color,
    sectionBoxColor: Color?,
    completionColor: Color,
    media: MediaEntity?,
    onOpenMedia: (MediaEntity) -> Unit,
    isAttachSource: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onDrag: (Offset, Boolean) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    isInlineTextEditing: Boolean,
    onSubmitText: (String) -> Unit,
    onCancelText: () -> Unit,
    onInlineTextChange: (String) -> Unit = {}
) {
    var pressed by remember { mutableStateOf(false) }
    var moveSubtree by remember(node.id) { mutableStateOf(false) }
    var inlineText by remember(node.id, isInlineTextEditing) { mutableStateOf(node.label) }
    val inlineTextFocusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "nodePressScale")

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(800)
            if (pressed) moveSubtree = true
        }
    }

    LaunchedEffect(isInlineTextEditing) {
        if (isInlineTextEditing) inlineTextFocusRequester.requestFocus()
    }

    val shape = RoundedCornerShape(if (isRoot) 12.dp else 10.dp)
    val bgColor = sectionBoxColor ?: node.colorArgb?.let { Color(it) }
        ?: (if (isRoot) themeColors.rootBg else themeColors.childBg)
    val glowStrength = glow.coerceIn(0f, MaxGlowIntensity)
    val glowElevation = glowStrength * if (isRoot) 44f else 32f
    val groupMoveColor = if (glowColor == AccentCyan) AccentPurple else AccentCyan
    val activeGlowColor = if (moveSubtree) groupMoveColor else glowColor
    val borderColor = if (isAttachSource) {
        AccentCyan
    } else {
        activeGlowColor.copy(alpha = if (moveSubtree) 0.9f else (0.04f + glowStrength * 0.2f).coerceAtMost(0.35f))
    }
    val baseBoxWidth = if (media?.type == MediaType.FILE) 126.dp else if (isRoot) 86.dp else 70.dp
    val baseBoxHeight = if (media != null) 58.dp else if (isRoot) 42.dp else 32.dp
    val boxWidth = baseBoxWidth * node.widthScale
    val boxHeight = baseBoxHeight * node.heightScale
    val textColor = if (node.isDone) {
        completionColor
    } else {
        node.textColorArgb?.let { Color(it) } ?: boxTextColor
    }

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { onSizeChanged(it.size) }
            .mindMapNode(
                onDrag = { delta -> onDrag(delta, moveSubtree) },
                onDragEnd = {
                    onDragEnd(moveSubtree)
                    moveSubtree = false
                },
                onTap = {
                    moveSubtree = false
                    onTap()
                },
                onDoubleTap = {
                    moveSubtree = false
                    onDoubleTap()
                },
                onTripleTap = {
                    moveSubtree = false
                    media?.let(onOpenMedia)
                },
                onPressChange = { isPressed ->
                    pressed = isPressed
                    if (isPressed) moveSubtree = false
                }
            )
            .shadow(
                elevation = (glowElevation * if (moveSubtree) 0.75f else 0.45f).dp,
                shape = shape,
                ambientColor = activeGlowColor,
                spotColor = activeGlowColor
            )
            .shadow(elevation = glowElevation.dp, shape = shape, ambientColor = activeGlowColor, spotColor = activeGlowColor)
            .width(if (isInlineTextEditing) maxOf(boxWidth, if (isRoot) 140.dp else 120.dp) else boxWidth)
            .heightIn(min = if (isInlineTextEditing) maxOf(boxHeight, if (isRoot) 58.dp else 50.dp) else boxHeight)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(bgColor, shape)
                .border(
                    width = if (isAttachSource || moveSubtree || isInlineTextEditing) 2.dp else 1.dp,
                    color = if (isInlineTextEditing) AccentCyan else borderColor,
                    shape = shape
                )
                .padding(horizontal = if (isRoot) 12.dp else 8.dp, vertical = if (isRoot) 8.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
        var textLayoutResult by remember(node.id, node.label, node.textSizeSp) {
            mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
        }
        val completionLineModifier = if (node.isDone) {
            Modifier.drawWithContent {
                drawContent()
                textLayoutResult?.let { layout ->
                    repeat(layout.lineCount) { lineIndex ->
                        val lineY = layout.getLineTop(lineIndex) +
                            (layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)) * 0.56f
                        drawLine(
                            color = completionColor,
                            start = Offset(layout.getLineLeft(lineIndex), lineY),
                            end = Offset(layout.getLineRight(lineIndex), lineY),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        } else {
            Modifier
        }
        val extraBoldOffset = ((node.textWeight - 900).coerceIn(0, 300) / 300f) * 1.2f
        if (media != null && !isInlineTextEditing) {
            if (media.type == MediaType.IMAGE) {
                MediaThumbnail(
                    uri = media.uri,
                    rotationDegrees = media.rotationDegrees,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = node.label.ifBlank { media.displayName },
                        color = textColor,
                        fontSize = (node.textSizeSp * 0.82f).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (isInlineTextEditing) {
            BasicTextField(
                value = inlineText,
                onValueChange = { inlineText = it; onInlineTextChange(it) },
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = textColor,
                    fontSize = node.textSizeSp.sp,
                    fontWeight = FontWeight(node.textWeight.coerceAtMost(900)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitText(inlineText) }),
                modifier = Modifier.fillMaxWidth().focusRequester(inlineTextFocusRequester)
            )
        } else {
            if (extraBoldOffset > 0f) {
                Text(
                    text = node.label, color = textColor,
                    fontSize = node.textSizeSp.sp, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = extraBoldOffset.dp)
                )
                Text(
                    text = node.label, color = textColor,
                    fontSize = node.textSizeSp.sp, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = (-extraBoldOffset).dp)
                )
            }
            Text(
                text = node.label, color = textColor,
                fontSize = node.textSizeSp.sp, fontWeight = FontWeight(node.textWeight.coerceAtMost(900)),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult = it },
                modifier = completionLineModifier
            )
        }
    }


    }
}

private object AttachmentBitmapCache {
    private const val MAX_ENTRIES = 60
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }
}

private fun attachmentBitmapCacheKey(uri: String, maxSide: Int) = "$uri:$maxSide"

@Composable
private fun MediaThumbnail(
    uri: String,
    rotationDegrees: Float = 0f,
    maxSide: Int = 640,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheKey = remember(uri, maxSide) { attachmentBitmapCacheKey(uri, maxSide) }
    var bitmap by remember(cacheKey) { mutableStateOf(AttachmentBitmapCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAttachmentBitmap(context, Uri.parse(uri), maxSide) }.getOrNull()
            }
            if (loaded != null) {
                AttachmentBitmapCache.put(cacheKey, loaded)
                bitmap = loaded
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.graphicsLayer { rotationZ = rotationDegrees }
        )
    } else {
        Box(modifier = modifier.background(Color.Black.copy(alpha = 0.16f)))
    }
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
private fun loadAttachmentBitmap(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val largestSide = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
        val scale = minOf(1f, maxSide.toFloat() / largestSide)
        decoder.setTargetSize(
            (info.size.width * scale).toInt().coerceAtLeast(1),
            (info.size.height * scale).toInt().coerceAtLeast(1)
        )
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

private fun copyAttachmentToAppStorage(context: android.content.Context, sourceUri: Uri, displayName: String): Uri {
    val attachmentsDirectory = File(context.filesDir, "attachments").apply { mkdirs() }
    check(attachmentsDirectory.isDirectory) { "Unable to create attachment storage" }
    val extension = displayName.substringAfterLast('.', "").replace(Regex("[^A-Za-z0-9]"), "")
    val target = File(attachmentsDirectory, "attachment_${System.nanoTime()}${if (extension.isBlank()) "" else ".${extension.take(12)}"}")
    val resolver = context.contentResolver

    fun copyFrom(openInput: () -> java.io.InputStream?): Boolean = runCatching {
        val input = openInput() ?: return@runCatching false
        input.use { source ->
            target.outputStream().buffered().use { destination -> source.copyTo(destination) }
        }
        target.length() > 0L
    }.getOrElse {
        target.delete()
        false
    }

    var copied = copyFrom { resolver.openInputStream(sourceUri) }
    if (!copied) copied = copyFrom {
        resolver.openAssetFileDescriptor(sourceUri, "r")?.createInputStream()
    }
    if (!copied) copied = copyFrom {
        resolver.openFileDescriptor(sourceUri, "r")?.let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        }
    }
    if (!copied) {
        val rawPath = runCatching { DocumentsContract.getDocumentId(sourceUri) }
            .getOrNull()
            ?.takeIf { it.startsWith("raw:") }
            ?.removePrefix("raw:")
        rawPath?.let { path ->
            val sourceFile = File(path)
            if (sourceFile.isFile) {
                copied = copyFrom { sourceFile.inputStream() }
            }
        }
    }
    if (!copied) {
        target.delete()
        error("Unable to read selected attachment")
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.attachments", target)
}

private fun resolveAttachmentDisplayName(context: android.content.Context, uri: Uri): String {
    val providerName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return providerName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "Attachment"
}

private data class PdfPagePreview(
    val bitmap: Bitmap,
    val pageCount: Int,
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val textContents: List<PdfTextContent> = emptyList()
)

private data class PdfTextContent(
    val text: String,
    val bounds: List<android.graphics.RectF>
)
private data class PositionedWord(
    val text: String,
    val bound: android.graphics.RectF,
    val lineIndex: Int
)

private data class PageWordIndex(val words: List<PositionedWord>) {
    fun nearestIndex(pagePoint: Offset): Int? {
        if (words.isEmpty()) return null
        return words.indices.minByOrNull { idx ->
            val b = words[idx].bound
            val cx = (b.left + b.right) / 2f
            val cy = (b.top + b.bottom) / 2f
            val dx = pagePoint.x - cx
            val dy = pagePoint.y - cy
            dx * dx + dy * dy
        }
    }
}

private data class TextSelectionRange(val anchorIndex: Int, val focusIndex: Int) {
    val startIndex: Int get() = minOf(anchorIndex, focusIndex)
    val endIndex: Int get() = maxOf(anchorIndex, focusIndex)
}

private fun buildPageWordIndex(textContents: List<PdfTextContent>): PageWordIndex {
    val rawWords = textContents.flatMap { content -> content.bounds.map { bound -> content.text to bound } }
    if (rawWords.isEmpty()) return PageWordIndex(emptyList())

    val sortedByTop = rawWords.sortedBy { it.second.top }
    val avgHeight = sortedByTop.map { it.second.height() }.average().toFloat().coerceAtLeast(1f)
    val lineThreshold = avgHeight * 0.6f

    data class LineCluster(
        val items: MutableList<Pair<String, android.graphics.RectF>>,
        var top: Float,
        var bottom: Float
    )

    val clusters = mutableListOf<LineCluster>()
    sortedByTop.forEach { item ->
        val bound = item.second
        val centerY = (bound.top + bound.bottom) / 2f
        val cluster = clusters.firstOrNull { centerY in (it.top - lineThreshold)..(it.bottom + lineThreshold) }
        if (cluster != null) {
            cluster.items += item
            cluster.top = minOf(cluster.top, bound.top)
            cluster.bottom = maxOf(cluster.bottom, bound.bottom)
        } else {
            clusters += LineCluster(mutableListOf(item), bound.top, bound.bottom)
        }
    }

    val orderedWords = clusters.sortedBy { it.top }.flatMapIndexed { lineIndex, cluster ->
        cluster.items.sortedBy { it.second.left }.map { (text, bound) ->
            PositionedWord(text, bound, lineIndex)
        }
    }
    return PageWordIndex(orderedWords)
}

private fun untransformTouchPoint(
    point: Offset,
    containerSize: IntSize,
    zoom: Float,
    panOffset: Offset,
    rotationDegrees: Float
): Offset {
    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
    val afterTranslation = point - panOffset
    val relative = afterTranslation - center
    val radians = Math.toRadians(-rotationDegrees.toDouble())
    val cos = kotlin.math.cos(radians).toFloat()
    val sin = kotlin.math.sin(radians).toFloat()
    val rotated = Offset(
        relative.x * cos - relative.y * sin,
        relative.x * sin + relative.y * cos
    )
    val scaled = rotated / zoom.coerceAtLeast(0.01f)
    return scaled + center
}

private fun screenToPageSpace(preview: PdfPagePreview, screenPoint: Offset, containerSize: IntSize): Offset {
    val imageScale = minOf(
        containerSize.width.toFloat() / preview.bitmap.width.coerceAtLeast(1),
        containerSize.height.toFloat() / preview.bitmap.height.coerceAtLeast(1)
    )
    val imageLeft = (containerSize.width - preview.bitmap.width * imageScale) / 2f
    val imageTop = (containerSize.height - preview.bitmap.height * imageScale) / 2f
    val bitmapX = (screenPoint.x - imageLeft) / imageScale.coerceAtLeast(0.0001f)
    val bitmapY = (screenPoint.y - imageTop) / imageScale.coerceAtLeast(0.0001f)
    val pageX = bitmapX * preview.pageWidth / preview.bitmap.width.coerceAtLeast(1)
    val pageY = bitmapY * preview.pageHeight / preview.bitmap.height.coerceAtLeast(1)
    return Offset(pageX, pageY)
}

private fun pageRectToScreen(preview: PdfPagePreview, bound: android.graphics.RectF, containerSize: IntSize): android.graphics.RectF {
    val imageScale = minOf(
        containerSize.width.toFloat() / preview.bitmap.width.coerceAtLeast(1),
        containerSize.height.toFloat() / preview.bitmap.height.coerceAtLeast(1)
    )
    val imageLeft = (containerSize.width - preview.bitmap.width * imageScale) / 2f
    val imageTop = (containerSize.height - preview.bitmap.height * imageScale) / 2f
    val left = imageLeft + bound.left * preview.bitmap.width / preview.pageWidth.coerceAtLeast(1) * imageScale
    val top = imageTop + bound.top * preview.bitmap.height / preview.pageHeight.coerceAtLeast(1) * imageScale
    val right = imageLeft + bound.right * preview.bitmap.width / preview.pageWidth.coerceAtLeast(1) * imageScale
    val bottom = imageTop + bound.bottom * preview.bitmap.height / preview.pageHeight.coerceAtLeast(1) * imageScale
    return android.graphics.RectF(left, top, right, bottom)
}

private fun TextSelectionRange.toMarkerSelection(
    index: PageWordIndex,
    preview: PdfPagePreview,
    containerSize: IntSize,
    color: Color,
    opacity: Float
): PdfMarkerSelection {
    val safeEnd = (endIndex + 1).coerceAtMost(index.words.size)
    val words = if (startIndex in index.words.indices) index.words.subList(startIndex, safeEnd) else emptyList()
    val bounds = words.map { it.bound }
    val text = words.joinToString(" ") { it.text }
    return PdfMarkerSelection(color = color, start = Offset.Zero, end = Offset.Zero, opacity = opacity, textBounds = bounds, selectedText = text)
}

private fun boundsOverlap(a: List<android.graphics.RectF>, b: List<android.graphics.RectF>): Boolean {
    return a.any { rectA -> b.any { rectB -> android.graphics.RectF.intersects(rectA, rectB) } }
}
private data class PdfViewState(
    val isLocked: Boolean = false,
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Float = 0f
)

private data class PdfMarkerSelection(
    val color: Color,
    val start: Offset,
    val end: Offset,
    val opacity: Float,
    val textBounds: List<android.graphics.RectF> = emptyList(),
    val selectedText: String = ""
)

private data class DeviceFile(
    val file: File,
    val name: String,
    val extension: String
)

private data class PdfLibraryEntry(
    val path: String,
    val sourceName: String,
    val displayName: String = sourceName
)

private data class PdfLibrarySection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val entries: List<PdfLibraryEntry> = emptyList(),
    val backgroundArgb: Long? = null,
    val textArgb: Long? = null,
    val iconUri: String? = null
)

private data class PdfLibraryStyle(
    val backgroundArgb: Long? = null,
    val textArgb: Long? = null,
    val sectionBackgroundArgb: Long? = null,
    val sectionTextArgb: Long? = null
)

private val SupportedDeviceFileExtensions = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx")

private object DeviceFileCache {
    @Volatile var files: List<DeviceFile> = emptyList()
    @Volatile var updatedAtMillis: Long = 0L
}

/** In-process signal used to refresh the Files screen immediately after a download. */
private object PdfLibraryRefresh {
    var version by mutableIntStateOf(0)
}

private fun sharePdfFiles(context: android.content.Context, files: List<DeviceFile>) {
    val sharedDir = File(context.cacheDir, "shared-files").apply { mkdirs() }
    val uris = ArrayList<Uri>()
    files.filter { it.file.isFile }.forEachIndexed { index, deviceFile ->
        val target = File(sharedDir, "${System.currentTimeMillis()}_${index}_${deviceFile.name}")
        deviceFile.file.copyTo(target, overwrite = true)
        uris += FileProvider.getUriForFile(context, "${context.packageName}.attachments", target)
    }
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uris.first())
    } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/pdf"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}

private fun cachedDeviceFiles(): List<DeviceFile>? = DeviceFileCache.files.takeIf {
    it.isNotEmpty() && System.currentTimeMillis() - DeviceFileCache.updatedAtMillis < 10 * 60 * 1000L
}
private object PdfThumbnailCache {
    private const val MAX_ENTRIES = 240
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }
}

private fun pdfThumbnailCacheKey(file: DeviceFile): String = "${file.file.path}:${file.file.lastModified()}"

private fun loadPdfThumbnailBitmap(file: DeviceFile, maxSide: Int = 160): Bitmap? = runCatching {
    if (!file.file.isFile) return@runCatching null
    val descriptor = android.os.ParcelFileDescriptor.open(file.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use { fileDescriptor ->
        PdfRenderer(fileDescriptor).use { renderer ->
            if (renderer.pageCount <= 0) return@runCatching null
            renderer.openPage(0).use { page ->
                val scale = minOf(1f, maxSide.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1))
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
}.getOrNull()

@Composable
private fun PdfThumbnail(file: DeviceFile, modifier: Modifier = Modifier) {
    val cacheKey = remember(file.file.path, file.file.lastModified()) { pdfThumbnailCacheKey(file) }
    var bitmap by remember(cacheKey) { mutableStateOf(PdfThumbnailCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) { loadPdfThumbnailBitmap(file) }
            if (loaded != null) {
                PdfThumbnailCache.put(cacheKey, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.InsertDriveFile, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatDeviceFileTime(file: File): String = java.text.SimpleDateFormat(
    "dd MMM yyyy, hh:mm a",
    java.util.Locale.getDefault()
).format(java.util.Date(file.lastModified()))

private fun loadPdfLibrarySections(context: android.content.Context): List<PdfLibrarySection> = runCatching {
    val raw = context.getSharedPreferences("pdf_library", android.content.Context.MODE_PRIVATE)
        .getString("sections", "[]") ?: "[]"
    val sections = org.json.JSONArray(raw)
    buildList {
        repeat(sections.length()) { sectionIndex ->
            val jsonSection = sections.getJSONObject(sectionIndex)
            val entries = jsonSection.optJSONArray("entries") ?: org.json.JSONArray()
            add(
                PdfLibrarySection(
                    id = jsonSection.optString("id"),
                    title = jsonSection.optString("title", "PDF Section"),
                    entries = buildList {
                        repeat(entries.length()) { entryIndex ->
                            val jsonEntry = entries.getJSONObject(entryIndex)
                            add(
                                PdfLibraryEntry(
                                    path = jsonEntry.optString("path"),
                                    sourceName = jsonEntry.optString("sourceName"),
                                    displayName = jsonEntry.optString("displayName", jsonEntry.optString("sourceName"))
                                )
                            )
                        }
                    },
                    backgroundArgb = jsonSection.takeIf { it.has("backgroundArgb") && !it.isNull("backgroundArgb") }
                        ?.getLong("backgroundArgb"),
                    textArgb = jsonSection.takeIf { it.has("textArgb") && !it.isNull("textArgb") }
                        ?.getLong("textArgb"),
                    iconUri = jsonSection.optString("iconUri").takeIf { it.isNotBlank() }
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun savePdfLibrarySections(context: android.content.Context, sections: List<PdfLibrarySection>) {
    val jsonSections = org.json.JSONArray()
    sections.forEach { section ->
        val entries = org.json.JSONArray()
        section.entries.forEach { entry ->
            entries.put(
                org.json.JSONObject()
                    .put("path", entry.path)
                    .put("sourceName", entry.sourceName)
                    .put("displayName", entry.displayName)
            )
        }
        jsonSections.put(
            org.json.JSONObject()
                .put("id", section.id)
                .put("title", section.title)
                .put("entries", entries)
                .put("backgroundArgb", section.backgroundArgb)
                .put("textArgb", section.textArgb)
                .put("iconUri", section.iconUri)
        )
    }
    context.getSharedPreferences("pdf_library", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("sections", jsonSections.toString())
        .apply()
}

private fun loadPdfLibraryStyle(context: android.content.Context): PdfLibraryStyle {
    val preferences = context.getSharedPreferences("pdf_library", android.content.Context.MODE_PRIVATE)
    fun color(key: String): Long? = preferences.getLong(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
    return PdfLibraryStyle(
        backgroundArgb = color("background_argb"),
        textArgb = color("text_argb"),
        sectionBackgroundArgb = color("section_background_argb"),
        sectionTextArgb = color("section_text_argb")
    )
}

private fun savePdfLibraryStyle(context: android.content.Context, style: PdfLibraryStyle) {
    val editor = context.getSharedPreferences("pdf_library", android.content.Context.MODE_PRIVATE).edit()
    fun saveColor(key: String, color: Long?) {
        if (color == null) editor.remove(key) else editor.putLong(key, color)
    }
    saveColor("background_argb", style.backgroundArgb)
    saveColor("text_argb", style.textArgb)
    saveColor("section_background_argb", style.sectionBackgroundArgb)
    saveColor("section_text_argb", style.sectionTextArgb)
    editor.apply()
}

private val SkippedDeviceDirectoryNames = setOf(
    "android", "dcim", "pictures", "movies", "music", "ringtones",
    "podcasts", "notifications", "alarms", "camera", "screenshots",
    "whatsapp images", "whatsapp video", "whatsapp voice notes",
    "whatsapp animated gifs", "whatsapp audio", "sent", "statuses",
    "video", "images", "photos", "telegram images", "telegram video",
    "telegram audio", "cache", "caches"
)

private fun shouldSkipDeviceDirectory(name: String): Boolean {
    if (name.startsWith(".")) return true
    return name.lowercase() in SkippedDeviceDirectoryNames
}
private fun loadPersistedDeviceFiles(context: android.content.Context): List<DeviceFile> = runCatching {
    val raw = context.getSharedPreferences("device_file_cache", android.content.Context.MODE_PRIVATE)
        .getString("files", null) ?: return@runCatching emptyList()
    val array = org.json.JSONArray(raw)
    buildList {
        repeat(array.length()) { i ->
            val path = array.optJSONObject(i)?.optString("path") ?: return@repeat
            val file = File(path)
            if (file.isFile) add(DeviceFile(file, file.name, file.extension.lowercase()))
        }
    }
}.getOrDefault(emptyList())

private fun savePersistedDeviceFiles(context: android.content.Context, files: List<DeviceFile>) {
    val array = org.json.JSONArray()
    files.forEach { array.put(org.json.JSONObject().put("path", it.file.path)) }
    context.getSharedPreferences("device_file_cache", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("files", array.toString())
        .apply()
}
private fun findDeviceFiles(): List<DeviceFile> {
    cachedDeviceFiles()?.let { return it }
    val root = android.os.Environment.getExternalStorageDirectory()
    if (!root.isDirectory) return emptyList()
    val queue = java.util.ArrayDeque<File>()
    val result = mutableListOf<DeviceFile>()
    queue.add(root)
    while (queue.isNotEmpty() && result.size < 2_000) {
        val current = queue.removeFirst()
        val children = runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
        children.forEach { child ->
            when {
                child.isDirectory && !shouldSkipDeviceDirectory(child.name) -> queue.add(child)
                child.isFile -> {
                    val extension = child.extension.lowercase()
                    if (extension in SupportedDeviceFileExtensions) {
                        result += DeviceFile(child, child.name, extension)
                    }
                }
            }
        }
    }
    return result.sortedByDescending { it.file.lastModified() }.also {
        DeviceFileCache.files = it
        DeviceFileCache.updatedAtMillis = System.currentTimeMillis()
    }
}

private fun openDeviceFileExternally(context: android.content.Context, file: DeviceFile) {
    val sharedDirectory = File(context.cacheDir, "shared-files").apply { mkdirs() }
    val safeName = file.name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
    val sharedFile = File(sharedDirectory, "${file.file.lastModified()}_$safeName")
    runCatching {
        if (!sharedFile.isFile || sharedFile.length() != file.file.length()) {
            file.file.copyTo(sharedFile, overwrite = true)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", sharedFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, resolveAttachmentMimeType(file.name, null))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, file.name))
    }
}

private fun pdfViewStateKey(uri: String) = "pdf_${uri.hashCode().toUInt().toString(16)}"

private fun loadPdfViewState(context: android.content.Context, uri: String): PdfViewState {
    val preferences = context.getSharedPreferences("pdf_view_state", android.content.Context.MODE_PRIVATE)
    val key = pdfViewStateKey(uri)
    return PdfViewState(
        isLocked = preferences.getBoolean("${key}_locked", false),
        pageIndex = preferences.getInt("${key}_page", 0),
        zoom = preferences.getFloat("${key}_zoom", 1f),
        panX = preferences.getFloat("${key}_pan_x", 0f),
        panY = preferences.getFloat("${key}_pan_y", 0f),
        rotation = preferences.getFloat("${key}_rotation", 0f)
    )
}

private fun savePdfViewState(context: android.content.Context, uri: String, state: PdfViewState) {
    val key = pdfViewStateKey(uri)
    context.getSharedPreferences("pdf_view_state", android.content.Context.MODE_PRIVATE)
        .edit()
        .putBoolean("${key}_locked", state.isLocked)
        .putInt("${key}_page", state.pageIndex)
        .putFloat("${key}_zoom", state.zoom)
        .putFloat("${key}_pan_x", state.panX)
        .putFloat("${key}_pan_y", state.panY)
        .putFloat("${key}_rotation", state.rotation)
        .apply()
}

private fun clearPdfViewState(context: android.content.Context, uri: String) {
    val key = pdfViewStateKey(uri)
    context.getSharedPreferences("pdf_view_state", android.content.Context.MODE_PRIVATE)
        .edit()
        .remove("${key}_locked")
        .remove("${key}_page")
        .remove("${key}_zoom")
        .remove("${key}_pan_x")
        .remove("${key}_pan_y")
        .remove("${key}_rotation")
        .apply()
}
private fun pdfMarkerStorageKey(uri: String) = "pdf_markers_${uri.hashCode().toUInt().toString(16)}"

private fun savePdfMarkers(context: android.content.Context, uri: String, selections: Map<Int, List<PdfMarkerSelection>>) {
    val root = org.json.JSONObject()
    selections.forEach { (pageIndex, list) ->
        if (list.isEmpty()) return@forEach
        val pageArray = org.json.JSONArray()
        list.forEach { selection ->
            val argb = (((selection.color.alpha * 255f).roundToInt().coerceIn(0, 255)) shl 24) or
                    (((selection.color.red * 255f).roundToInt().coerceIn(0, 255)) shl 16) or
                    (((selection.color.green * 255f).roundToInt().coerceIn(0, 255)) shl 8) or
                    ((selection.color.blue * 255f).roundToInt().coerceIn(0, 255))
            val boundsArray = org.json.JSONArray()
            selection.textBounds.forEach { bound ->
                val boundArray = org.json.JSONArray()
                boundArray.put(bound.left.toDouble())
                boundArray.put(bound.top.toDouble())
                boundArray.put(bound.right.toDouble())
                boundArray.put(bound.bottom.toDouble())
                boundsArray.put(boundArray)
            }
            pageArray.put(
                org.json.JSONObject()
                    .put("colorArgb", argb.toLong() and 0xFFFFFFFFL)
                    .put("opacity", selection.opacity.toDouble())
                    .put("text", selection.selectedText)
                    .put("bounds", boundsArray)
            )
        }
        root.put(pageIndex.toString(), pageArray)
    }
    context.getSharedPreferences("pdf_markers", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(pdfMarkerStorageKey(uri), root.toString())
        .apply()
}

private fun loadPdfMarkers(context: android.content.Context, uri: String): Map<Int, List<PdfMarkerSelection>> = runCatching {
    val raw = context.getSharedPreferences("pdf_markers", android.content.Context.MODE_PRIVATE)
        .getString(pdfMarkerStorageKey(uri), null) ?: return@runCatching emptyMap()
    val root = org.json.JSONObject(raw)
    val result = mutableMapOf<Int, List<PdfMarkerSelection>>()
    root.keys().forEach { key ->
        val pageIndex = key.toIntOrNull() ?: return@forEach
        val pageArray = root.getJSONArray(key)
        val list = buildList {
            repeat(pageArray.length()) { i ->
                val obj = pageArray.getJSONObject(i)
                val colorArgb = obj.optLong("colorArgb", 0xFFFFEB3BL)
                val opacity = obj.optDouble("opacity", 0.36).toFloat()
                val text = obj.optString("text", "")
                val boundsArray = obj.optJSONArray("bounds") ?: org.json.JSONArray()
                val bounds = buildList {
                    repeat(boundsArray.length()) { bi ->
                        val boundArray = boundsArray.getJSONArray(bi)
                        add(
                            android.graphics.RectF(
                                boundArray.getDouble(0).toFloat(),
                                boundArray.getDouble(1).toFloat(),
                                boundArray.getDouble(2).toFloat(),
                                boundArray.getDouble(3).toFloat()
                            )
                        )
                    }
                }
                add(
                    PdfMarkerSelection(
                        color = Color(colorArgb),
                        start = Offset.Zero,
                        end = Offset.Zero,
                        opacity = opacity,
                        textBounds = bounds,
                        selectedText = text
                    )
                )
            }
        }
        result[pageIndex] = list
    }
    result
}.getOrDefault(emptyMap())

private fun materializePdfForRendering(context: android.content.Context, uri: Uri): File {
    if (uri.scheme == "file") {
        val localFile = uri.path?.let(::File) ?: error("Unable to locate PDF")
        check(localFile.isFile) { "PDF file is unavailable" }
        return localFile
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()) {
        val directFile = runCatching {
            val documentId = DocumentsContract.getDocumentId(uri)
            val path = when {
                documentId.startsWith("raw:") -> documentId.removePrefix("raw:")
                documentId.startsWith("primary:") -> File(
                    android.os.Environment.getExternalStorageDirectory(),
                    documentId.removePrefix("primary:")
                ).path
                else -> null
            }
            path?.let(::File)?.takeIf { it.isFile }
        }.getOrNull()
        if (directFile != null) return directFile
    }
    val previewDirectory = File(context.cacheDir, "pdf-preview").apply { mkdirs() }
    check(previewDirectory.isDirectory) { "Unable to create PDF preview storage" }
    val cacheFile = File(previewDirectory, "${uri.toString().hashCode().toUInt().toString(16)}.pdf")
    if (cacheFile.isFile && cacheFile.length() > 0L) return cacheFile
    val pendingFile = File(previewDirectory, "${cacheFile.name}.pending")
    pendingFile.delete()

    val resolver = context.contentResolver
    val input = resolver.openInputStream(uri)
        ?: resolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
        ?: resolver.openFileDescriptor(uri, "r")?.let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        }
        ?: error("Unable to read PDF")

    input.use { source ->
        pendingFile.outputStream().buffered().use { destination -> source.copyTo(destination) }
    }
    check(pendingFile.length() > 0L) { "PDF is empty" }
    pendingFile.copyTo(cacheFile, overwrite = true)
    pendingFile.delete()
    return cacheFile
}

private fun pdfDownloadFileName(media: MediaEntity): String {
    val base = media.displayName.ifBlank { "document" }
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}

private fun isPdfAlreadyDownloaded(context: android.content.Context, media: MediaEntity): Boolean {
    val safeName = pdfDownloadFileName(media)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        return runCatching {
            context.contentResolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(safeName),
                null
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    return File(downloadsDir, safeName).isFile
}

private suspend fun downloadPdfToDevice(context: android.content.Context, media: MediaEntity): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = materializePdfForRendering(context, Uri.parse(media.uri))
            val safeName = pdfDownloadFileName(media)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val itemUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                val output = resolver.openOutputStream(itemUri) ?: return@runCatching false
                output.use { destination -> sourceFile.inputStream().use { it.copyTo(destination) } }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                sourceFile.copyTo(File(downloadsDir, safeName), overwrite = true)
            }
            true
        }.getOrDefault(false)
    }

private fun loadPdfPage(context: android.content.Context, uri: Uri, requestedPage: Int): PdfPagePreview {
    val localPdf = materializePdfForRendering(context, uri)
    val descriptor = android.os.ParcelFileDescriptor.open(
        localPdf,
        android.os.ParcelFileDescriptor.MODE_READ_ONLY
    )
    descriptor.use { fileDescriptor ->
        PdfRenderer(fileDescriptor).use { renderer ->
            check(renderer.pageCount > 0) { "PDF has no pages" }
            val pageIndex = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(pageIndex).use { page ->
                val maximumDimension = 2_600
                val scale = (
                        maximumDimension.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                        ).coerceIn(1f, 5f)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val textContents = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val rawContents = page.javaClass.getMethod("getTextContents").invoke(page) as? List<Any?> ?: emptyList()
                    rawContents.mapNotNull { content ->
                        content ?: return@mapNotNull null
                        val text = content.javaClass.getMethod("getText").invoke(content) as? String ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val bounds = content.javaClass.getMethod("getBounds").invoke(content) as? List<android.graphics.RectF> ?: emptyList()
                        PdfTextContent(text, bounds)
                    }
                }.getOrDefault(emptyList())
                return PdfPagePreview(bitmap, renderer.pageCount, pageIndex, page.width, page.height, textContents)
            }
        }
    }
}

private data class PdfPageFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(offset: Offset): Boolean = offset.x in left..right && offset.y in top..bottom
    fun clamp(offset: Offset): Offset = Offset(offset.x.coerceIn(left, right), offset.y.coerceIn(top, bottom))
}

private fun pdfPageFrame(preview: PdfPagePreview, containerSize: IntSize): PdfPageFrame {
    val imageScale = minOf(
        containerSize.width.toFloat() / preview.bitmap.width.coerceAtLeast(1),
        containerSize.height.toFloat() / preview.bitmap.height.coerceAtLeast(1)
    )
    val width = preview.bitmap.width * imageScale
    val height = preview.bitmap.height * imageScale
    val left = (containerSize.width - width) / 2f
    val top = (containerSize.height - height) / 2f
    return PdfPageFrame(left, top, left + width, top + height)
}

/** True only when the *rendered page*, after scale and rotation, extends past its viewport. */
private fun isPdfContentPannable(
    preview: PdfPagePreview,
    containerSize: IntSize,
    zoom: Float,
    rotation: Float
): Boolean {
    if (containerSize == IntSize.Zero) return false
    val isQuarterTurn = abs(rotation % 180f) > 45f
    val targetWidth = if (isQuarterTurn) containerSize.height else containerSize.width
    val targetHeight = if (isQuarterTurn) containerSize.width else containerSize.height
    val imageScale = minOf(
        targetWidth.toFloat() / preview.bitmap.width.coerceAtLeast(1),
        targetHeight.toFloat() / preview.bitmap.height.coerceAtLeast(1)
    )
    val renderedWidth = preview.bitmap.width * imageScale * zoom
    val renderedHeight = preview.bitmap.height * imageScale * zoom
    return renderedWidth > targetWidth + 0.5f || renderedHeight > targetHeight + 0.5f
}

private fun recognisePdfText(bitmap: Bitmap, pageWidth: Int, pageHeight: Int): List<PdfTextContent> = runCatching {
    val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
    )
    try {
        val input = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(input))
        result.textBlocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.mapNotNull { element ->
                    val bounds = element.boundingBox ?: return@mapNotNull null
                    PdfTextContent(
                        text = element.text,
                        bounds = listOf(
                            android.graphics.RectF(
                                bounds.left.toFloat() * pageWidth / bitmap.width.coerceAtLeast(1),
                                bounds.top.toFloat() * pageHeight / bitmap.height.coerceAtLeast(1),
                                bounds.right.toFloat() * pageWidth / bitmap.width.coerceAtLeast(1),
                                bounds.bottom.toFloat() * pageHeight / bitmap.height.coerceAtLeast(1)
                            )
                        )
                    )
                }
            }
        }
    } finally {
        recognizer.close()
    }
}.getOrDefault(emptyList())

@Composable
private fun PdfViewerDialog(media: MediaEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val storedViewState = remember(media.uri) { loadPdfViewState(context, media.uri) }
    var requestedPage by remember(media.uri) { mutableIntStateOf(storedViewState.pageIndex) }
    var pagePreview by remember(media.uri) { mutableStateOf<PdfPagePreview?>(null) }
    var isLoading by remember(media.uri) { mutableStateOf(true) }
    var zoom by remember(media.uri) { mutableFloatStateOf(storedViewState.zoom) }
    var rotation by remember(media.uri) {
        mutableFloatStateOf(if (abs(storedViewState.rotation % 180f) > 45f) 90f else 0f)
    }
    var panOffset by remember(media.uri) { mutableStateOf(Offset(storedViewState.panX, storedViewState.panY)) }
    var zoomLocked by remember(media.uri) { mutableStateOf(storedViewState.isLocked) }
    var controlsVisible by remember(media.uri) { mutableStateOf(true) }
    var markerEnabled by remember(media.uri) { mutableStateOf(false) }
    var markerColor by remember(media.uri) { mutableStateOf(Color(0xFFFFEB3B)) }
    var markerOpacity by remember(media.uri) { mutableFloatStateOf(0.38f) }
    var markerToolsVisible by remember(media.uri) { mutableStateOf(false) }
    var markerColorPaletteVisible by remember(media.uri) { mutableStateOf(false) }
    var markerSelections by remember(media.uri) { mutableStateOf(loadPdfMarkers(context, media.uri)) }
    var markerRedoSelections by remember(media.uri) { mutableStateOf<Map<Int, List<PdfMarkerSelection>>>(emptyMap()) }
    var activeMarkerSelection by remember { mutableStateOf<PdfMarkerSelection?>(null) }
    var activeTextSelection by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var pageWordIndex by remember(media.uri) { mutableStateOf<PageWordIndex?>(null) }
    var selectedTextSelection by remember(media.uri) { mutableStateOf<PdfMarkerSelection?>(null) }
    var selectedTextForActions by remember(media.uri) { mutableStateOf<String?>(null) }
    var copyNotice by remember(media.uri) { mutableStateOf<String?>(null) }
    var pageContainerSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    var swipeDistance by remember(media.uri) { mutableFloatStateOf(0f) }
    var pageDirection by remember(media.uri) { mutableIntStateOf(1) }
    var pageSwipeVersion by remember(media.uri) { mutableIntStateOf(0) }
    var skipPageAnimation by remember(media.uri) { mutableStateOf(false) }
    val initialMarkerFabOffsetPx = with(LocalDensity.current) { Offset(0f, -64.dp.toPx()) }
    var markerFabOffset by remember(media.uri) { mutableStateOf(initialMarkerFabOffsetPx) }
    var markerFabPressed by remember(media.uri) { mutableStateOf(false) }
    var readerSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    var readerPositionInWindow by remember(media.uri) { mutableStateOf(Offset.Zero) }
    val pageCache = remember(media.uri) { mutableStateMapOf<Int, PdfPagePreview>() }
    val pdfLoadScope = rememberCoroutineScope()
    var isPdfDownloaded by remember(media.uri) { mutableStateOf(false) }
    var isPdfDownloading by remember(media.uri) { mutableStateOf(false) }
    LaunchedEffect(media.uri) {
        isPdfDownloaded = withContext(Dispatchers.IO) { isPdfAlreadyDownloaded(context, media) }
    }
    val activePageIndex = pagePreview?.pageIndex ?: requestedPage
    // Keep the existing unlocked gesture direction.  In lock mode, however,
    // navigation follows the actual rendered page orientation: portrait is
    // vertical and a quarter-turn swaps that direction.
    val isQuarterTurn = abs(rotation % 180f) > 45f
    val pageIsPortrait = pagePreview?.let { it.pageHeight >= it.pageWidth } ?: true
    val pageNavigationIsVertical = if (zoomLocked) {
        // পাতা visually লম্বা (tall) দেখাচ্ছে হলে swipe = left-right (false),
        // পাতা visually চওড়া (wide) দেখাচ্ছে হলে swipe = up-down (true)।
        pageIsPortrait == isQuarterTurn
    } else {
        isQuarterTurn
    }
    val currentZoom by rememberUpdatedState(zoom)
    val markerFabSizePx = with(LocalDensity.current) { 44.dp.toPx() }
    val markerToolsPanelWidthPx = with(LocalDensity.current) { 210.dp.toPx() }
    val markerPalettePanelHeightPx = with(LocalDensity.current) { 232.dp.toPx() }
    val markerFabLeftPx = readerSize.width - markerFabSizePx + markerFabOffset.x
    val markerFabTopPx = readerSize.height - markerFabSizePx + markerFabOffset.y
    val markerToolsPanelFitsLeft = markerFabLeftPx >= markerToolsPanelWidthPx
    val markerPalettePanelFitsAbove = markerFabTopPx >= markerPalettePanelHeightPx
    var colorPalettePanelSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    var colorSwatchPositionInWindow by remember(media.uri) { mutableStateOf(Offset.Zero) }
    var colorSwatchSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    var markerToolsPanelSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    var undoMarkerCandidate by remember(media.uri) { mutableStateOf<Pair<Int, PdfMarkerSelection>?>(null) }
    var undoMarkerPopupPosition by remember(media.uri) { mutableStateOf<Offset?>(null) }
    var undoPopupSize by remember(media.uri) { mutableStateOf(IntSize.Zero) }
    val pageSwipeThreshold = (
        if (pageNavigationIsVertical) pageContainerSize.height else pageContainerSize.width
    ).toFloat().times(0.22f).coerceAtLeast(140f)

    fun changePage(nextPage: Int) {
        val totalPages = pagePreview?.pageCount ?: return
        val clampedPage = nextPage.coerceIn(0, totalPages - 1)
        if (clampedPage != requestedPage) {
            pageDirection = if (clampedPage > requestedPage) 1 else -1
            skipPageAnimation = false
            requestedPage = clampedPage
            swipeDistance = 0f
            pageSwipeVersion += 1
            undoMarkerCandidate = null
        }
    }

    fun togglePageOrientation() {
        rotation = if (abs(rotation % 180f) > 45f) 0f else 90f
        panOffset = Offset.Zero
        swipeDistance = 0f
    }

    LaunchedEffect(pageSwipeVersion) {
        delay(100)
        val distance = swipeDistance
        if (abs(distance) < 1f || (!zoomLocked && pagePreview?.let {
                isPdfContentPannable(it, pageContainerSize, zoom, rotation)
            } == true)) return@LaunchedEffect
        val currentPreview = pagePreview ?: return@LaunchedEffect
        val navigationSize = (
            if (pageNavigationIsVertical) pageContainerSize.height else pageContainerSize.width
        ).toFloat().coerceAtLeast(1f)
        val targetPage = when {
            distance <= -pageSwipeThreshold && currentPreview.pageIndex < currentPreview.pageCount - 1 -> currentPreview.pageIndex + 1
            distance >= pageSwipeThreshold && currentPreview.pageIndex > 0 -> currentPreview.pageIndex - 1
            else -> null
        }
        val snapTarget = targetPage?.let { target -> if (distance < 0f) -navigationSize else navigationSize } ?: 0f
        val snapAnimation = Animatable(distance)
        snapAnimation.animateTo(snapTarget, tween(190)) {
            swipeDistance = value
        }
        if (targetPage != null) {
            pageDirection = if (targetPage > currentPreview.pageIndex) 1 else -1
            pageCache[targetPage]?.let { pagePreview = it }
            skipPageAnimation = true
            requestedPage = targetPage
        }
        swipeDistance = 0f
    }

    LaunchedEffect(requestedPage) {
        if (skipPageAnimation) {
            delay(260)
            skipPageAnimation = false
        }
    }

    LaunchedEffect(copyNotice) {
        if (copyNotice != null) {
            delay(900)
            copyNotice = null
        }
    }

    LaunchedEffect(media.uri, requestedPage) {
        val cachedPreview = pageCache[requestedPage]
        if (cachedPreview != null) {
            pagePreview = cachedPreview
            isLoading = false
        } else {
            isLoading = true
            val loadedPreview = withContext(Dispatchers.IO) {
                runCatching { loadPdfPage(context, Uri.parse(media.uri), requestedPage) }.getOrNull()
            }
            if (loadedPreview != null) {
                pageCache[loadedPreview.pageIndex] = loadedPreview
                pagePreview = loadedPreview
            }
            isLoading = false
        }
        pagePreview?.let { preview ->
            listOf(preview.pageIndex - 1, preview.pageIndex + 1)
                .filter { it in 0 until preview.pageCount && it !in pageCache }
                .forEach { nearbyPage ->
                    pdfLoadScope.launch {
                        val nearbyPreview = withContext(Dispatchers.IO) {
                            runCatching { loadPdfPage(context, Uri.parse(media.uri), nearbyPage) }.getOrNull()
                        }
                        if (nearbyPreview != null) pageCache[nearbyPreview.pageIndex] = nearbyPreview
                    }
                }
        }
    }

    LaunchedEffect(media.uri, pagePreview?.pageIndex, pagePreview?.textContents?.isEmpty()) {
        val preview = pagePreview ?: return@LaunchedEffect
        if (preview.textContents.isEmpty()) {
            val recognisedText = withContext(Dispatchers.Default) {
                recognisePdfText(preview.bitmap, preview.pageWidth, preview.pageHeight)
            }
            if (recognisedText.isNotEmpty() && pagePreview?.pageIndex == preview.pageIndex) {
                val updatedPreview = preview.copy(textContents = recognisedText)
                pageCache[updatedPreview.pageIndex] = updatedPreview
                pagePreview = updatedPreview
            }
        }
    }
    LaunchedEffect(pagePreview?.pageIndex, pagePreview?.textContents) {
        pageWordIndex = pagePreview?.textContents?.takeIf { it.isNotEmpty() }?.let(::buildPageWordIndex)
    }

    LaunchedEffect(media.uri, zoomLocked, requestedPage, zoom, panOffset, rotation) {
        if (zoomLocked) {
            delay(250)
            savePdfViewState(
                context,
                media.uri,
                PdfViewState(true, requestedPage, zoom, panOffset.x, panOffset.y, rotation)
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(controlsVisible, dialogWindow) {
            val window = dialogWindow
            val controller = window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
            }
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible) {
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                window?.statusBarColor = AndroidColor.BLACK
                window?.navigationBarColor = AndroidColor.BLACK
                controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        Surface(color = Color(0xFF101822), modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        readerSize = it.size
                        readerPositionInWindow = it.positionInWindow()
                    }
            ) {
                val visiblePreview = pagePreview?.takeIf { it.pageIndex == requestedPage }
                val adjacentPage = when {
                    swipeDistance < 0f -> visiblePreview?.pageIndex?.plus(1)
                    swipeDistance > 0f -> visiblePreview?.pageIndex?.minus(1)
                    else -> null
                }
                val adjacentPreview = adjacentPage?.let(pageCache::get)
                if (adjacentPreview != null) {
                    val swipeProgress = (
                            abs(swipeDistance) /
                                    (if (pageNavigationIsVertical) readerSize.height else readerSize.width).toFloat().coerceAtLeast(1f)
                            ).coerceIn(0f, 1f)
                    Image(
                        bitmap = adjacentPreview.bitmap.asImageBitmap(),
                        contentDescription = "Next PDF page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = panOffset.x
                                translationY = panOffset.y
                                alpha = (0.15f + swipeProgress * 0.8f).coerceIn(0f, 0.95f)
                                rotationZ = rotation
                            }
                    )
                }
                AnimatedContent(
                    targetState = requestedPage,
                    transitionSpec = {
                        if (skipPageAnimation) {
                            androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        } else if (pageNavigationIsVertical) {
                            (slideInVertically(tween(260)) { fullHeight -> pageDirection * fullHeight } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(220)) { fullHeight -> -pageDirection * fullHeight } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(260)) { fullWidth -> pageDirection * fullWidth } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(220)) { fullWidth -> -pageDirection * fullWidth } + fadeOut(tween(150)))
                        }
                    },
                    label = "pdfPageTransition",
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) { targetPage ->
                    val preview = pagePreview?.takeIf { it.pageIndex == targetPage } ?: pageCache[targetPage]
                    preview?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { pageContainerSize = it.size }
                                .pointerInput(media.uri, it.pageIndex, markerEnabled, zoom, panOffset) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoom > 1.01f || panOffset != Offset.Zero) {
                                                zoom = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                togglePageOrientation()
                                            }
                                        }
                                    )
                                }
                                .pointerInput(
                                    media.uri, it.pageIndex, markerEnabled, zoomLocked, rotation,
                                    pageNavigationIsVertical, pageContainerSize
                                ) {
                                    val longPressTimeoutMs = 350L
                                    val slop = viewConfiguration.touchSlop

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downTime = System.currentTimeMillis()
                                        var multiTouch = false
                                        var becameDrag = false
                                        var longPressFired = false
                                        var released = false

                                        while (!multiTouch && !becameDrag && !longPressFired && !released) {
                                            val remaining = longPressTimeoutMs - (System.currentTimeMillis() - downTime)
                                            val event = if (remaining > 0) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null

                                            if (event == null) {
                                                val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
                                                if (stillDown) longPressFired = true else released = true
                                                break
                                            }
                                            val pressedCount = event.changes.count { it.pressed }
                                            if (pressedCount > 1) { multiTouch = true; break }
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            if (!change.pressed) { released = true; break }
                                            if ((change.position - down.position).getDistance() > slop) { becameDrag = true; break }
                                        }

                                        when {
                                            released -> {
                                                if (undoMarkerCandidate != null) {
                                                    undoMarkerCandidate = null
                                                } else if (selectedTextForActions != null || selectedTextSelection != null) {
                                                    selectedTextForActions = null
                                                    selectedTextSelection = null
                                                } else {
                                                    controlsVisible = !controlsVisible
                                                }
                                            }
                                            longPressFired && !markerEnabled -> {
                                                // ---- TEXT SELECTION (reading-order word-range + কপি) ----
                                                val preview = it
                                                val frame = pdfPageFrame(preview, pageContainerSize)
                                                val index = pageWordIndex
                                                val touchPoint = untransformTouchPoint(down.position, pageContainerSize, zoom, panOffset, rotation)
                                                if (index != null && index.words.isNotEmpty() && frame.contains(touchPoint)) {
                                                    val anchorIdx = index.nearestIndex(screenToPageSpace(preview, touchPoint, pageContainerSize))
                                                    if (anchorIdx != null) {
                                                        var range = TextSelectionRange(anchorIdx, anchorIdx)
                                                        activeTextSelection = down.position to down.position
                                                        drag(down.id) { change ->
                                                            change.consume()
                                                            val clampedTouch = frame.clamp(untransformTouchPoint(change.position, pageContainerSize, zoom, panOffset, rotation))
                                                            activeTextSelection = down.position to change.position
                                                            val focusIdx = index.nearestIndex(screenToPageSpace(preview, clampedTouch, pageContainerSize))
                                                            if (focusIdx != null) {
                                                                range = TextSelectionRange(anchorIdx, focusIdx)
                                                                selectedTextSelection = range.toMarkerSelection(index, preview, pageContainerSize, Color(0xFF3B82F6), 0.36f)
                                                            }
                                                        }
                                                        val finalSelection = range.toMarkerSelection(index, preview, pageContainerSize, Color(0xFF3B82F6), 0.36f)
                                                        if (finalSelection.textBounds.isNotEmpty() && finalSelection.selectedText.isNotBlank()) {
                                                            selectedTextSelection = finalSelection
                                                            selectedTextForActions = finalSelection.selectedText
                                                        } else {
                                                            selectedTextSelection = null
                                                            selectedTextForActions = null
                                                        }
                                                    }
                                                    activeTextSelection = null
                                                }
                                            }

                                            longPressFired && markerEnabled -> {
                                                // ---- এক আঙুল, long-press পরে drag: marker (word-based reading-order selection) ----
                                                val preview = it
                                                val frame = pdfPageFrame(preview, pageContainerSize)
                                                val index = pageWordIndex
                                                val touchPoint = untransformTouchPoint(down.position, pageContainerSize, zoom, panOffset, rotation)
                                                if (index != null && index.words.isNotEmpty() && frame.contains(touchPoint)) {
                                                    val anchorIdx = index.nearestIndex(screenToPageSpace(preview, touchPoint, pageContainerSize))
                                                    if (anchorIdx != null) {
                                                        var range = TextSelectionRange(anchorIdx, anchorIdx)
                                                        activeMarkerSelection = range.toMarkerSelection(index, preview, pageContainerSize, markerColor, markerOpacity)
                                                        drag(down.id) { change ->
                                                            change.consume()
                                                            val clampedTouch = frame.clamp(untransformTouchPoint(change.position, pageContainerSize, zoom, panOffset, rotation))
                                                            val focusIdx = index.nearestIndex(screenToPageSpace(preview, clampedTouch, pageContainerSize))
                                                            if (focusIdx != null) {
                                                                range = TextSelectionRange(anchorIdx, focusIdx)
                                                                activeMarkerSelection = range.toMarkerSelection(index, preview, pageContainerSize, markerColor, markerOpacity)
                                                            }
                                                        }
                                                        val committed = range.toMarkerSelection(index, preview, pageContainerSize, markerColor, markerOpacity)
                                                        if (committed.textBounds.isNotEmpty() && committed.selectedText.isNotBlank()) {
                                                            val existingOnPage = markerSelections[preview.pageIndex].orEmpty()
                                                            val overlapping = existingOnPage.firstOrNull { existing ->
                                                                boundsOverlap(existing.textBounds, committed.textBounds)
                                                            }
                                                            if (overlapping != null) {
                                                                // এখনই সরাসরি undo না করে, শুধু Undo বাটনসহ একটা ছোট popup দেখাও
                                                                undoMarkerCandidate = preview.pageIndex to overlapping
                                                                undoMarkerPopupPosition = down.position
                                                            } else {
                                                                markerSelections = markerSelections + (
                                                                        preview.pageIndex to (existingOnPage + committed)
                                                                        )
                                                                markerRedoSelections = markerRedoSelections + (preview.pageIndex to emptyList())
                                                                undoMarkerCandidate = null
                                                            }
                                                        }
                                                        activeMarkerSelection = null
                                                    }
                                                }
                                            }

                                            multiTouch -> {
                                                // ---- দুই আঙুল: zoom + pan (marker on থাকলেও কাজ করবে) ----
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()
                                                    if (!zoomLocked) {
                                                        zoom = (zoom * zoomChange).coerceIn(0.7f, 5f)
                                                        panOffset += panChange
                                                    }
                                                    event.changes.forEach { c -> if (c.positionChanged()) c.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }

                                            else -> {
                                                // ---- এক আঙুল: pan (zoom করা থাকলে) অথবা swipe (আগের মতোই, transition অপরিবর্তিত) ----
                                                var lastPos = down.position
                                                drag(down.id) { change ->
                                                    val panChange = change.position - lastPos
                                                    lastPos = change.position
                                                    change.consume()

                                                    val primaryPan = if (pageNavigationIsVertical) panChange.y else panChange.x
                                                    val secondaryPan = if (pageNavigationIsVertical) panChange.x else panChange.y
                                                    val isPrimarySwipe = abs(primaryPan) > abs(secondaryPan)

                                                    if (zoomLocked) {
                                                        // Locked: navigation axis (pageNavigationIsVertical, পাতা
                                                        // visually লম্বা না চওড়া তার উপর নির্ভর করে) সবসময় swipe করবে,
                                                        // তার লম্ব axis সবসময় pan করবে।
                                                        if (isPrimarySwipe) {
                                                            val swipeLimit = (
                                                                    if (pageNavigationIsVertical) pageContainerSize.height else pageContainerSize.width
                                                                    ).toFloat().coerceAtLeast(1f) * 0.96f
                                                            swipeDistance = (swipeDistance + primaryPan).coerceIn(-swipeLimit, swipeLimit)
                                                            pageSwipeVersion += 1
                                                        } else {
                                                            panOffset += if (pageNavigationIsVertical) {
                                                                Offset(panChange.x, 0f)
                                                            } else {
                                                                Offset(0f, panChange.y)
                                                            }
                                                        }
                                                        return@drag
                                                    }

                                                    // Only the exact fit scale is a page-swipe state. Any pinch
                                                    // transformed scale (in or out), or overflowed content, is pan.
                                                    val canPanCanvas = abs(currentZoom - 1f) > 0.001f || isPdfContentPannable(
                                                        it, pageContainerSize, currentZoom, rotation
                                                    )
                                                    if (canPanCanvas) {
                                                        // A pannable rendered page owns every one-finger drag,
                                                        // irrespective of whether the scale is above or below 1.
                                                        panOffset += panChange
                                                    } else if (isPrimarySwipe) {
                                                        val swipeLimit = (
                                                                if (pageNavigationIsVertical) pageContainerSize.height else pageContainerSize.width
                                                                ).toFloat().coerceAtLeast(1f) * 0.96f
                                                        swipeDistance = (swipeDistance + primaryPan).coerceIn(-swipeLimit, swipeLimit)
                                                        pageSwipeVersion += 1
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panOffset.x + if (!pageNavigationIsVertical) swipeDistance else 0f
                                    translationY = panOffset.y + if (pageNavigationIsVertical) swipeDistance else 0f
                                    rotationZ = rotation
                                }
                        ) {
                            Image(
                                bitmap = it.bitmap.asImageBitmap(),
                                contentDescription = media.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                (markerSelections[it.pageIndex].orEmpty() +
                                        listOfNotNull(
                                            selectedTextSelection,
                                            activeMarkerSelection
                                        ))
                                    .forEach { selection ->
                                        if (selection.textBounds.isNotEmpty()) {
                                            val imageScale = minOf(
                                                size.width / it.bitmap.width.coerceAtLeast(1),
                                                size.height / it.bitmap.height.coerceAtLeast(1)
                                            )
                                            val imageLeft = (size.width - it.bitmap.width * imageScale) / 2f
                                            val imageTop = (size.height - it.bitmap.height * imageScale) / 2f
                                            selection.textBounds.forEach { bound ->
                                                val left = imageLeft + bound.left * it.bitmap.width / it.pageWidth.coerceAtLeast(1) * imageScale
                                                val top = imageTop + bound.top * it.bitmap.height / it.pageHeight.coerceAtLeast(1) * imageScale
                                                val right = imageLeft + bound.right * it.bitmap.width / it.pageWidth.coerceAtLeast(1) * imageScale
                                                val bottom = imageTop + bound.bottom * it.bitmap.height / it.pageHeight.coerceAtLeast(1) * imageScale
                                                drawRoundRect(
                                                    color = selection.color.copy(alpha = selection.opacity),
                                                    topLeft = Offset(left, top),
                                                    size = Size((right - left).coerceAtLeast(2f), (bottom - top).coerceAtLeast(2f)),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                                                )
                                            }
                                        } else {
                                            val left = min(selection.start.x, selection.end.x)
                                            val centerY = (selection.start.y + selection.end.y) / 2f
                                            val markerHeight = 30f.coerceAtMost(size.height * 0.08f)
                                            drawRoundRect(
                                                color = selection.color.copy(alpha = selection.opacity),
                                                topLeft = Offset(left, centerY - markerHeight / 2f),
                                                size = Size(
                                                    abs(selection.end.x - selection.start.x).coerceAtLeast(2f),
                                                    markerHeight
                                                ),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(markerHeight / 2f)
                                            )
                                        }
                                    }
                            }
                        }
                    }
                }
                if (isLoading) CircularProgressIndicator(color = AccentCyan, modifier = Modifier.align(Alignment.Center))

                selectedTextForActions?.let { selectedText ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = rotation },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEE171A2B),
                        shadowElevation = 10.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("PDF text", selectedText))
                                copyNotice = "Copied"
                            }) { Text("Copy", color = AccentCyan) }
                            TextButton(onClick = {
                                pagePreview?.let { preview ->
                                    selectedTextForActions = preview.textContents.joinToString(" ") { it.text }
                                    selectedTextSelection = PdfMarkerSelection(
                                        color = Color(0xFF3B82F6),
                                        start = Offset.Zero,
                                        end = Offset.Zero,
                                        opacity = 0.36f,
                                        textBounds = preview.textContents.flatMap { it.bounds },
                                        selectedText = selectedTextForActions.orEmpty()
                                    )
                                }
                            }) { Text("Select all", color = AccentCyan) }
                        }
                    }
                }
                copyNotice?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 56.dp)
                            .graphicsLayer { rotationZ = rotation },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xEE171A2B)
                    ) {
                        Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                    }
                }

                undoMarkerCandidate?.let { (candidatePageIndex, overlappingSelection) ->
                    val popupPosition = undoMarkerPopupPosition ?: Offset.Zero
                    val popupDensity = LocalDensity.current
                    val popupPaddingPx = with(popupDensity) { 12.dp.toPx() }
                    val undoSizePx = undoPopupSize.takeIf { it.width > 0 && it.height > 0 }
                        ?: IntSize(with(popupDensity) { 64.dp.roundToPx() }, with(popupDensity) { 32.dp.roundToPx() })
                    val footprintWidth = if (pageNavigationIsVertical) undoSizePx.height else undoSizePx.width
                    val footprintHeight = if (pageNavigationIsVertical) undoSizePx.width else undoSizePx.height
                    val desiredCenterX = popupPosition.x + popupPaddingPx - 32f + undoSizePx.width / 2f
                    val desiredCenterY = popupPosition.y + popupPaddingPx - 50f + undoSizePx.height / 2f
                    val halfFootprintWidth = footprintWidth / 2f
                    val halfFootprintHeight = footprintHeight / 2f
                    val clampedCenterX = desiredCenterX.coerceIn(
                        halfFootprintWidth,
                        (readerSize.width - halfFootprintWidth).coerceAtLeast(halfFootprintWidth)
                    )
                    val clampedCenterY = desiredCenterY.coerceIn(
                        halfFootprintHeight,
                        (readerSize.height - halfFootprintHeight).coerceAtLeast(halfFootprintHeight)
                    )
                    val undoTopLeftX = clampedCenterX - undoSizePx.width / 2f
                    val undoTopLeftY = clampedCenterY - undoSizePx.height / 2f
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(undoTopLeftX.roundToInt(), undoTopLeftY.roundToInt()) }
                            .onGloballyPositioned { undoPopupSize = it.size }
                            .zIndex(45f)
                            .graphicsLayer { rotationZ = rotation },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xEE171A2B),
                        shadowElevation = 10.dp
                    ) {
                        TextButton(
                            onClick = {
                                val existing = markerSelections[candidatePageIndex].orEmpty()
                                markerSelections = markerSelections + (candidatePageIndex to existing.filterNot { it == overlappingSelection })
                                undoMarkerCandidate = null
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Undo", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .pointerInput("pdf-top-bar-guard") { detectTapGestures(onTap = {}) }
                ) {
                    AnimatedVisibility(visible = controlsVisible) {
                        Surface(color = Color(0xEE171A2B), shadowElevation = 10.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onDismiss) { Text("Back", color = SoftNeutral) }
                                Text(
                                    text = media.displayName,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                                )
                                if (!isPdfDownloaded) TextButton(
                                    enabled = !isPdfDownloading,
                                    onClick = {
                                        if (!isPdfDownloading) {
                                            isPdfDownloading = true
                                            pdfLoadScope.launch {
                                                val success = downloadPdfToDevice(context, media)
                                                isPdfDownloading = false
                                                if (success) {
                                                    isPdfDownloaded = true
                                                    val refreshed = withContext(Dispatchers.IO) { findDeviceFiles() }
                                                    DeviceFileCache.files = refreshed
                                                    DeviceFileCache.updatedAtMillis = System.currentTimeMillis()
                                                    PdfLibraryRefresh.version++
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Download",
                                        color = SoftNeutral,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = isPdfDownloading,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                    ) {
                        Surface(color = Color(0xDD171A2B)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Downloading...", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = controlsVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Surface(color = Color(0xEE171A2B), shadowElevation = 10.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfReaderControl("<", enabled = (pagePreview?.pageIndex ?: 0) > 0) {
                                changePage((pagePreview?.pageIndex ?: 0) - 1)
                            }
                            Text(
                                "${(pagePreview?.pageIndex ?: 0) + 1} / ${pagePreview?.pageCount ?: 0}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.width(36.dp)
                            )
                            PdfReaderControl(">", enabled = pagePreview?.let { it.pageIndex < it.pageCount - 1 } == true) {
                                changePage((pagePreview?.pageIndex ?: 0) + 1)
                            }
                            PdfReaderControl(if (zoomLocked) "Unlock" else "Lock") {
                                zoomLocked = !zoomLocked
                                if (!zoomLocked) clearPdfViewState(context, media.uri)
                            }
                            PdfReaderControl("Rotate", enabled = !zoomLocked) { togglePageOrientation() }
                            PdfReaderControl(if (markerEnabled) "Marker on" else "Marker") {
                                markerEnabled = !markerEnabled
                                markerToolsVisible = markerEnabled
                                undoMarkerCandidate = null
                            }
                        }
                    }
                }

                if (markerEnabled && controlsVisible) {
                    val toolsDensity = LocalDensity.current
                    val toolsGapPx = with(toolsDensity) { 8.dp.toPx() }
                    val toolsSizePx = markerToolsPanelSize.takeIf { it.width > 0 && it.height > 0 }
                        ?: if (pageNavigationIsVertical) {
                            IntSize(with(toolsDensity) { 202.dp.roundToPx() }, with(toolsDensity) { 44.dp.roundToPx() })
                        } else {
                            IntSize(with(toolsDensity) { 52.dp.roundToPx() }, with(toolsDensity) { 184.dp.roundToPx() })
                        }
                    val targetHorizontalToolsOffset = run {
                        val fabAbsLeft = readerSize.width - markerFabSizePx + markerFabOffset.x
                        val fabAbsRight = readerSize.width.toFloat() + markerFabOffset.x
                        val fabAbsTop = readerSize.height - markerFabSizePx + markerFabOffset.y
                        val fitsLeft = fabAbsLeft - toolsGapPx - toolsSizePx.width >= 0f
                        val maxLeft = (readerSize.width - toolsSizePx.width).toFloat().coerceAtLeast(0f)
                        val toolsAbsLeft = (if (fitsLeft) {
                            fabAbsLeft - toolsGapPx - toolsSizePx.width
                        } else {
                            fabAbsRight + toolsGapPx
                        }).coerceIn(0f, maxLeft)
                        val maxTop = (readerSize.height - toolsSizePx.height).toFloat().coerceAtLeast(0f)
                        val toolsAbsTop = (fabAbsTop - 4f).coerceIn(0f, maxTop)
                        Offset(
                            toolsAbsLeft - (readerSize.width - toolsSizePx.width),
                            toolsAbsTop - (readerSize.height - toolsSizePx.height)
                        )
                    }
                    val targetVerticalToolsOffset = run {
                        val fabAbsLeft = readerSize.width - markerFabSizePx + markerFabOffset.x
                        val fabAbsTop = readerSize.height - markerFabSizePx + markerFabOffset.y
                        val fabAbsBottom = readerSize.height.toFloat() + markerFabOffset.y
                        val fitsAbove = fabAbsTop - toolsGapPx - toolsSizePx.height >= 0f
                        val maxTop = (readerSize.height - toolsSizePx.height).toFloat().coerceAtLeast(0f)
                        val toolsAbsTop = (if (fitsAbove) {
                            fabAbsTop - toolsGapPx - toolsSizePx.height
                        } else {
                            fabAbsBottom + toolsGapPx
                        }).coerceIn(0f, maxTop)
                        val maxLeft = (readerSize.width - toolsSizePx.width).toFloat().coerceAtLeast(0f)
                        val toolsAbsLeft = (fabAbsLeft - 4f).coerceIn(0f, maxLeft)
                        Offset(
                            toolsAbsLeft - (readerSize.width - toolsSizePx.width),
                            toolsAbsTop - (readerSize.height - toolsSizePx.height)
                        )
                    }
                    val targetToolsOffset = if (pageNavigationIsVertical) targetHorizontalToolsOffset else targetVerticalToolsOffset
                    val animatedToolsOffset by animateOffsetAsState(
                        targetValue = targetToolsOffset,
                        animationSpec = tween(220),
                        label = "markerToolsOffset"
                    )
                    AnimatedVisibility(
                        visible = markerToolsVisible,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset { IntOffset(animatedToolsOffset.x.roundToInt(), animatedToolsOffset.y.roundToInt()) },
                        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
                        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xEE171A2B),
                            shadowElevation = 10.dp,
                            modifier = Modifier.onGloballyPositioned { markerToolsPanelSize = it.size }
                        ) {
                            val isMarkingInProgress = activeMarkerSelection != null
                            val toolContent: @Composable () -> Unit = {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .onGloballyPositioned {
                                            colorSwatchPositionInWindow = it.positionInWindow()
                                            colorSwatchSize = it.size
                                        }
                                        .clip(CircleShape)
                                        .background(markerColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                        .pointerInput("marker-color-${media.uri}") {
                                            detectTapGestures(onTap = { markerColorPaletteVisible = !markerColorPaletteVisible })
                                        }
                                )
                                if (!isMarkingInProgress) {
                                    TextButton(
                                        enabled = markerSelections[activePageIndex].orEmpty().isNotEmpty(),
                                        onClick = {
                                            val selections = markerSelections[activePageIndex].orEmpty()
                                            val lastSelection = selections.last()
                                            markerSelections = markerSelections + (activePageIndex to selections.dropLast(1))
                                            markerRedoSelections = markerRedoSelections + (activePageIndex to (markerRedoSelections[activePageIndex].orEmpty() + lastSelection))
                                        }
                                    ) { Text("Undo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                                    TextButton(
                                        enabled = markerRedoSelections[activePageIndex].orEmpty().isNotEmpty(),
                                        onClick = {
                                            val selections = markerRedoSelections[activePageIndex].orEmpty()
                                            val restoredSelection = selections.last()
                                            markerSelections = markerSelections + (activePageIndex to (markerSelections[activePageIndex].orEmpty() + restoredSelection))
                                            markerRedoSelections = markerRedoSelections + (activePageIndex to selections.dropLast(1))
                                        }
                                    ) { Text("Redo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                                    TextButton(
                                        enabled = markerSelections.values.any { it.isNotEmpty() },
                                        onClick = {
                                            savePdfMarkers(context, media.uri, markerSelections)
                                            copyNotice = "Saved"
                                        }
                                    ) { Text("Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                                }
                            }
                            if (pageNavigationIsVertical) {
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = 320.dp)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) { toolContent() }
                            } else {
                                Column(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) { toolContent() }
                            }
                        }
                    }

                    if (markerToolsVisible) {
                        val localDensity = LocalDensity.current
                        val paletteSizePx = colorPalettePanelSize.takeIf { it.width > 0 && it.height > 0 }
                            ?: if (pageNavigationIsVertical) {
                                IntSize(with(localDensity) { 92.dp.roundToPx() }, with(localDensity) { 168.dp.roundToPx() })
                            } else {
                                IntSize(with(localDensity) { 206.dp.roundToPx() }, with(localDensity) { 134.dp.roundToPx() })
                            }
                        val gapPx = with(localDensity) { 10.dp.toPx() }
                        val targetPaletteOffset = if (pageNavigationIsVertical) {
                            val swatchAbsLeft = colorSwatchPositionInWindow.x - readerPositionInWindow.x
                            val swatchAbsTop = colorSwatchPositionInWindow.y - readerPositionInWindow.y
                            val fitsAbove = swatchAbsTop - gapPx - paletteSizePx.height >= 0f
                            val maxTop = (readerSize.height - paletteSizePx.height).toFloat().coerceAtLeast(0f)
                            val paletteAbsTop = (if (fitsAbove) {
                                swatchAbsTop - gapPx - paletteSizePx.height
                            } else {
                                swatchAbsTop + colorSwatchSize.height + gapPx
                            }).coerceIn(0f, maxTop)
                            val maxLeft = (readerSize.width - paletteSizePx.width).toFloat().coerceAtLeast(0f)
                            val paletteAbsLeft = (swatchAbsLeft + colorSwatchSize.width / 2f - paletteSizePx.width / 2f)
                                .coerceIn(0f, maxLeft)
                            Offset(
                                paletteAbsLeft - (readerSize.width - paletteSizePx.width),
                                paletteAbsTop - (readerSize.height - paletteSizePx.height)
                            )
                        } else {
                            val swatchAbsLeft = colorSwatchPositionInWindow.x - readerPositionInWindow.x
                            val swatchAbsTop = colorSwatchPositionInWindow.y - readerPositionInWindow.y
                            val maxLeft = (readerSize.width - paletteSizePx.width).toFloat().coerceAtLeast(0f)
                            val fitsRight = swatchAbsLeft + colorSwatchSize.width + gapPx + paletteSizePx.width <= readerSize.width
                            val paletteAbsLeft = (if (fitsRight) {
                                swatchAbsLeft + colorSwatchSize.width + gapPx
                            } else {
                                swatchAbsLeft - gapPx - paletteSizePx.width
                            }).coerceIn(0f, maxLeft)
                            val maxTop = (readerSize.height - paletteSizePx.height).toFloat().coerceAtLeast(0f)
                            val paletteAbsTop = (swatchAbsTop + colorSwatchSize.height / 2f - paletteSizePx.height / 2f)
                                .coerceIn(0f, maxTop)
                            Offset(
                                paletteAbsLeft - (readerSize.width - paletteSizePx.width),
                                paletteAbsTop - (readerSize.height - paletteSizePx.height)
                            )
                        }
                        val animatedPaletteOffset by animateOffsetAsState(
                            targetValue = targetPaletteOffset,
                            animationSpec = tween(220),
                            label = "colorPaletteOffset"
                        )

                        AnimatedVisibility(
                            visible = markerColorPaletteVisible,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset { IntOffset(animatedPaletteOffset.x.roundToInt(), animatedPaletteOffset.y.roundToInt()) },
                            enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
                            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = Color(0xEE171A2B),
                                shadowElevation = 10.dp,
                                modifier = Modifier.onGloballyPositioned { colorPalettePanelSize = it.size }
                            ) {
                                if (!pageNavigationIsVertical) {
                                    // Vertical PDF: horizontal swatches, horizontal slider নিচে
                                    Column(modifier = Modifier.width(186.dp).padding(10.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(Color(0xFFFFEB3B), Color(0xFF64FFDA), Color(0xFFFF80AB), Color(0xFFBB86FC), Color(0xFFFF9800)).forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(if (color == markerColor) 2.dp else 1.dp, Color.White, CircleShape)
                                                        .pointerInput(color) {
                                                            detectTapGestures(onTap = {
                                                                markerColor = color
                                                                markerColorPaletteVisible = false
                                                                markerToolsVisible = false
                                                            })
                                                        }
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        StyledValueSlider(
                                            value = markerOpacity,
                                            valueRange = 0.14f..0.72f,
                                            trackBrush = Brush.horizontalGradient(listOf(markerColor.copy(alpha = 0.2f), markerColor)),
                                            thumbColor = markerColor,
                                            onValueChange = { markerOpacity = it }
                                        )
                                    }
                                } else {
                                    // Horizontal PDF: vertical swatches, vertical slider পাশে (side-by-side)
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(Color(0xFFFFEB3B), Color(0xFF64FFDA), Color(0xFFFF80AB), Color(0xFFBB86FC), Color(0xFFFF9800)).forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(if (color == markerColor) 2.dp else 1.dp, Color.White, CircleShape)
                                                        .pointerInput(color) {
                                                            detectTapGestures(onTap = {
                                                                markerColor = color
                                                                markerColorPaletteVisible = false
                                                                markerToolsVisible = false
                                                            })
                                                        }
                                                )
                                            }
                                        }
                                        VerticalMarkerOpacitySlider(
                                            value = markerOpacity,
                                            onValueChange = { markerOpacity = it },
                                            color = markerColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val markerFabScale by animateFloatAsState(if (markerFabPressed) 0.87f else 1f, label = "markerFabScale")
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset { IntOffset(markerFabOffset.x.roundToInt(), markerFabOffset.y.roundToInt()) }
                            .size(44.dp)
                            .graphicsLayer { scaleX = markerFabScale; scaleY = markerFabScale }
                            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = markerColor, spotColor = markerColor)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(markerColor.copy(alpha = 0.82f), markerColor)))
                            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .pointerInput("marker-drag-${media.uri}") {
                                detectDragGestures(
                                    onDragStart = { markerFabPressed = true },
                                    onDragEnd = { markerFabPressed = false },
                                    onDragCancel = { markerFabPressed = false }
                                ) { change, amount ->
                                    change.consume()
                                    markerFabOffset = Offset(
                                        (markerFabOffset.x + amount.x).coerceIn((-readerSize.width + markerFabSizePx).coerceAtMost(0f), 0f),
                                        (markerFabOffset.y + amount.y).coerceIn((-readerSize.height + markerFabSizePx).coerceAtMost(0f), 0f)
                                    )
                                }
                            }
                            .pointerInput("marker-tap-${media.uri}") {
                                detectTapGestures(
                                    onPress = {
                                        markerFabPressed = true
                                        tryAwaitRelease()
                                        markerFabPressed = false
                                    },
                                    onTap = {
                                        markerToolsVisible = !markerToolsVisible
                                        markerColorPaletteVisible = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✎", color = Color(0xFF101822), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderControl(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val controlWidth = when {
        label == "<" || label == ">" -> 44.dp
        label == "Rotate" -> 54.dp
        label.startsWith("Marker") -> 68.dp
        else -> 50.dp
    }
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.width(controlWidth).height(48.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) SoftNeutral else Color.White.copy(alpha = 0.32f),
            fontSize = if (label == "<" || label == ">") 28.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun VerticalMarkerOpacitySlider(value: Float, onValueChange: (Float) -> Unit, color: Color) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    fun updateFromY(y: Float) {
        val fraction = (1f - y / heightPx).coerceIn(0f, 1f)
        onValueChange(0.14f + fraction * 0.58f)
    }
    val fraction = ((value - 0.14f) / 0.58f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(94.dp)
            .onGloballyPositioned { heightPx = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { updateFromY(it.y) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromY(change.position.y)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .fillMaxHeight(fraction)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -((heightPx * fraction).roundToInt())) }
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White, CircleShape)
        )
    }
}

private fun resolveAttachmentMimeType(displayName: String, reportedMimeType: String?): String {
    if (!reportedMimeType.isNullOrBlank() && reportedMimeType != "application/octet-stream") {
        return reportedMimeType
    }
    return when (displayName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }
}

@Composable
private fun MediaViewerDialog(
    media: MediaEntity,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onRotate: (Float) -> Unit
) {
    var scale by remember(media.id) { mutableStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }
    var rotation by remember(media.id) { mutableStateOf(media.rotationDegrees) }
    var controlsVisible by remember(media.id) { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(media.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
                .pointerInput("viewer-controls-${media.id}") {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        ) {
            MediaThumbnail(
                uri = media.uri,
                rotationDegrees = rotation,
                maxSide = 2048,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Back", color = Color.White, modifier = Modifier.pointerInput("back-${media.id}") { detectTapGestures(onTap = { onDismiss() }) })
                    Text("Rotate", color = Color.White, modifier = Modifier.pointerInput("rotate-${media.id}") {
                        detectTapGestures(onTap = {
                            rotation = (rotation + 90f) % 360f
                            onRotate(rotation)
                        })
                    })
                    Text("Replace", color = SoftNeutral, modifier = Modifier.pointerInput("replace-${media.id}") { detectTapGestures(onTap = { onReplace() }) })
                    Text("Remove", color = Color(0xFFFF6E6E), modifier = Modifier.pointerInput("remove-${media.id}") { detectTapGestures(onTap = { onRemove() }) })
                }
            }
        }
    }
}

@Composable
private fun InlineTextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.88f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF0F1020), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "fabScale")

    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.55f), AccentPurple.copy(alpha = 0.55f))))
            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
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
        Text("+", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun StyledChevronButton(pointingDown: Boolean, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = if (pointingDown) 0f else 180f }
        )
    }
}

@Composable
fun SectionTopBar(
    sections: List<SectionEntity>,
    currentSection: SectionEntity?,
    listExpanded: Boolean,
    onToggleList: () -> Unit,
    editExpanded: Boolean,
    onToggleEdit: () -> Unit,
    onSelectSection: (Long) -> Unit,
    onReorder: (List<SectionEntity>) -> Unit,
    onRename: () -> Unit,
    onAddSection: () -> Unit,
    onRemoveSection: () -> Unit,
    sectionNameColor: Color,
    onSectionNameLongPress: () -> Unit,
    themeColors: MindMapColors
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).zIndex(30f),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(themeColors.barBg.copy(alpha = 0.9f))
                    .border(1.dp, Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledChevronButton(pointingDown = !listExpanded, tint = themeColors.textPrimary, onClick = onToggleList)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentSection?.title ?: "",
                    color = sectionNameColor, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(currentSection?.id) {
                        detectTapGestures(
                            onTap = { currentSection?.let { onSelectSection(it.id) } },
                            onLongPress = { onSectionNameLongPress() }
                        )
                    }
                )
                Spacer(Modifier.width(6.dp))
                StyledChevronButton(pointingDown = !editExpanded, tint = themeColors.textPrimary, onClick = onToggleEdit)
            }

            AnimatedVisibility(
                visible = listExpanded,
                enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = themeColors.barBg,
                    contentColor = themeColors.textPrimary,
                    shadowElevation = 10.dp
                ) {
                    SectionReorderList(
                        sections = sections,
                        currentSectionId = currentSection?.id,
                        onSelect = onSelectSection,
                        onReorder = onReorder,
                        themeColors = themeColors
                    )
                }
                }
            }

            AnimatedVisibility(
                visible = editExpanded,
                enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = themeColors.barBg,
                    contentColor = themeColors.textPrimary,
                    shadowElevation = 10.dp
                    ) {
                        Column(modifier = Modifier.width(180.dp)) {
                            DropdownMenuItem(text = { Text("Edit section name", color = themeColors.textPrimary) }, onClick = { onRename(); onToggleEdit() })
                            DropdownMenuItem(text = { Text("Add section", color = themeColors.textPrimary) }, onClick = { onAddSection(); onToggleEdit() })
                            DropdownMenuItem(text = { Text("Remove section", color = themeColors.textPrimary) }, onClick = { onRemoveSection(); onToggleEdit() })
                        }
                }
                }
            }
        }
    }
}

@Composable
fun SectionReorderList(
    sections: List<SectionEntity>,
    currentSectionId: Long?,
    onSelect: (Long) -> Unit,
    onReorder: (List<SectionEntity>) -> Unit,
    themeColors: MindMapColors
) {
    var localList by remember { mutableStateOf(sections) }
    val itemHeight = 44.dp
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemHeight.toPx() }
    var draggingSectionId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var orderChangedDuringDrag by remember { mutableStateOf(false) }
    var dragStartOrder by remember { mutableStateOf<List<SectionEntity>>(emptyList()) }
    val releaseOffset = remember { Animatable(0f) }
    var settlingDrag by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val dragScope = rememberCoroutineScope()
    var pressedSectionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(sections, draggingSectionId, settlingDrag) {
        if (draggingSectionId == null && !settlingDrag) {
            localList = sections
        }
    }

    Column(modifier = Modifier.width(180.dp).heightIn(max = 320.dp)) {
        localList.forEachIndexed { index, section ->
            key(section.id) {
            val isDragging = draggingSectionId == section.id
            val offsetY = when {
                isDragging && settlingDrag -> releaseOffset.value
                isDragging -> dragOffset
                else -> 0f
            }
            val rowScale by animateFloatAsState(
                targetValue = if (pressedSectionId == section.id || isDragging) 0.97f else 1f,
                animationSpec = tween(120),
                label = "sectionPress"
            )
            val rowBackground by animateColorAsState(
                targetValue = if (section.id == currentSectionId) AccentCyan.copy(alpha = 0.14f) else Color.Transparent,
                animationSpec = tween(180),
                label = "sectionSelection"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .zIndex(if (isDragging) 2f else 0f)
                    .graphicsLayer { translationY = offsetY; scaleX = rowScale; scaleY = rowScale }
                    .background(rowBackground)
                    .pointerInput(section.id) {
                        detectDragGestures(
                            onDragStart = {
                                settleJob?.cancel()
                                settlingDrag = false
                                val originIndex = localList.indexOfFirst { it.id == section.id }
                                if (originIndex < 0) return@detectDragGestures
                                draggingSectionId = section.id
                                dragStartOrder = localList
                                dragOffset = 0f
                                orderChangedDuringDrag = false
                                pressedSectionId = section.id
                            },
                            onDragEnd = {
                                if (draggingSectionId != section.id) return@detectDragGestures
                                if (orderChangedDuringDrag) onReorder(localList)
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(120))
                                    if (draggingSectionId == section.id) {
                                        draggingSectionId = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedSectionId = null
                                    }
                                }
                            },
                            onDragCancel = {
                                if (draggingSectionId != section.id) return@detectDragGestures
                                localList = dragStartOrder
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(140))
                                    if (draggingSectionId == section.id) {
                                        draggingSectionId = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedSectionId = null
                                    }
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            if (draggingSectionId != section.id) return@detectDragGestures
                            dragOffset += amount.y
                            val currentIndex = localList.indexOfFirst { it.id == section.id }
                            val direction = when {
                                dragOffset >= itemHeightPx / 2 && currentIndex < localList.lastIndex -> 1
                                dragOffset <= -itemHeightPx / 2 && currentIndex > 0 -> -1
                                else -> 0
                            }
                            if (direction != 0) {
                                val reordered = localList.toMutableList()
                                val movedSection = reordered.removeAt(currentIndex)
                                reordered.add(currentIndex + direction, movedSection)
                                localList = reordered
                                dragOffset -= direction * itemHeightPx
                                orderChangedDuringDrag = true
                            }
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("≡", color = themeColors.textPrimary.copy(alpha = 0.45f), fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = section.title, color = themeColors.textPrimary, fontSize = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(section.id) {
                            detectTapGestures(
                                onPress = {
                                    pressedSectionId = section.id
                                    tryAwaitRelease()
                                    pressedSectionId = null
                                },
                                onTap = { onSelect(section.id) }
                            )
                        }
                )
            }
            }
        }
    }
}

@Composable
fun StyledInputDialog(title: String, initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(GlassDark1.copy(alpha = 0.96f), GlassDark2.copy(alpha = 0.96f))))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Title", fontSize = 15.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentCyan, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = AccentCyan, unfocusedLabelColor = Color.LightGray,
                        cursorColor = AccentCyan
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(88.dp).height(48.dp)
                    ) {
                        Text("Cancel", color = Color.LightGray, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { if (text.isNotBlank()) onConfirm(text) },
                        modifier = Modifier.width(88.dp).height(48.dp)
                    ) {
                        Text("Save", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextStylePanel(
    node: NodeEntity,
    themeColors: MindMapColors,
    onDismiss: () -> Unit,
    onPreview: (String, Float, Int, Long?) -> Unit
) {
    var text by remember(node.id) { mutableStateOf(node.label) }
    var textSize by remember(node.id) { mutableStateOf(node.textSizeSp) }
    var textWeight by remember(node.id) { mutableStateOf(node.textWeight) }
    var selectedTextColor by remember(node.id) { mutableStateOf(node.textColorArgb) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    val defaultTextColorArgb = if (themeColors.textPrimary == Color.White) 0xFFFFFFFF else 0xFF1A1A1A

    fun preview() = onPreview(text, textSize, textWeight, selectedTextColor)

    Column(modifier = Modifier.width(240.dp).padding(14.dp)) {
        Text("Text", color = themeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value
                preview()
            },
            label = { Text("Text", color = themeColors.textPrimary.copy(alpha = 0.7f)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = themeColors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = themeColors.textPrimary.copy(alpha = 0.3f),
                focusedTextColor = themeColors.textPrimary,
                unfocusedTextColor = themeColors.textPrimary,
                cursorColor = AccentCyan
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Text color", color = themeColors.textPrimary.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                selectedTextColor = null
                preview()
            }) {
                Text("Default", color = AccentCyan, fontSize = 12.sp)
            }
            ColorCircle(Color(selectedTextColor ?: defaultTextColorArgb), onClick = { showTextColorPicker = true })
        }
        Spacer(Modifier.height(6.dp))
        Text("Text size  ${"%.0f".format(textSize)}sp", color = themeColors.textPrimary, fontSize = 13.sp)
        StyledValueSlider(
            value = textSize,
            valueRange = 10f..34f,
            trackBrush = Brush.horizontalGradient(listOf(AccentCyan.copy(alpha = 0.35f), AccentCyan)),
            thumbColor = AccentCyan,
            onValueChange = { value ->
                textSize = value
                preview()
            }
        )
        Text("Text weight  $textWeight", color = themeColors.textPrimary, fontSize = 13.sp)
        StyledValueSlider(
            value = textWeight.toFloat(),
            valueRange = 100f..1200f,
            trackBrush = Brush.horizontalGradient(listOf(AccentPurple.copy(alpha = 0.35f), AccentPurple)),
            thumbColor = AccentPurple,
            onValueChange = { value ->
                textWeight = value.roundToInt()
                preview()
            }
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                textSize = 16f
                textWeight = 400
                preview()
            }, modifier = Modifier.width(92.dp)) {
                Text("Default size", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.width(72.dp)) {
                Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showTextColorPicker) {
        ColorPickerDialog(
            title = "Text color",
            initialColorArgb = selectedTextColor ?: defaultTextColorArgb,
            onDismiss = { showTextColorPicker = false },
            onSelect = { color ->
                selectedTextColor = color
                preview()
                showTextColorPicker = false
            },
            allowReset = false,
            onReset = {}
        )
    }
}

@Composable
private fun SectionTitleStylePanel(
    initialColorArgb: Long?,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onUpdate: (Long) -> Unit,
    onReset: () -> Unit
) {
    val isWhiteTheme = themeMode == ThemeMode.WHITE
    val textColor = if (isWhiteTheme) Color(0xFF1A1A1A) else Color.White
    val mutedColor = if (isWhiteTheme) Color(0xFF5E5E68) else Color.LightGray
    val defaultColor = if (isWhiteTheme) 0xFF1A1A1A else 0xFFFFFFFF
    var selectedColor by remember { mutableStateOf(initialColorArgb ?: defaultColor) }
    var showColorPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(230.dp).padding(14.dp)) {
        Text("Section name", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Text color", color = mutedColor, modifier = Modifier.weight(1f))
            ColorCircle(Color(selectedColor), onClick = { showColorPicker = true })
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                selectedColor = defaultColor
                onReset()
            }) { Text("Default", color = mutedColor) }
            TextButton(onClick = onDismiss) { Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold) }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "Section name color",
            initialColorArgb = selectedColor,
            onDismiss = { showColorPicker = false },
            onSelect = { color ->
                selectedColor = color
                onUpdate(color)
                showColorPicker = false
            },
            allowReset = false,
            onReset = {}
        )
    }
}

@Composable
private fun TreeLineStylePanel(
    node: NodeEntity,
    defaultColorArgb: Long,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onUpdate: (Long, Float) -> Unit,
    onReset: () -> Unit
) {
    var selectedColor by remember(node.id) { mutableStateOf(node.connectorColorArgb ?: defaultColorArgb) }
    var thickness by remember(node.id) { mutableStateOf(node.connectorStrokeWidth) }
    var showColorPicker by remember { mutableStateOf(false) }
    val isWhiteTheme = themeMode == ThemeMode.WHITE
    val textColor = if (isWhiteTheme) Color(0xFF1A1A1A) else Color.White
    val mutedColor = if (isWhiteTheme) Color(0xFF5E5E68) else Color.LightGray

    Box(modifier = Modifier.width(220.dp).padding(14.dp)) {
        Column {
            Text("Line style", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Line color", color = mutedColor, modifier = Modifier.weight(1f))
                ColorCircle(Color(selectedColor), onClick = { showColorPicker = true })
            }
            Spacer(Modifier.height(14.dp))
            Text("Thickness  ${"%.1f".format(thickness)}", color = mutedColor, fontSize = 14.sp)
            StyledValueSlider(
                value = thickness,
                valueRange = 1f..16f,
                trackBrush = Brush.horizontalGradient(listOf(Color(selectedColor).copy(alpha = 0.35f), Color(selectedColor))),
                thumbColor = Color(selectedColor),
                onValueChange = { value ->
                    thickness = value
                    onUpdate(selectedColor, thickness)
                }
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    selectedColor = defaultColorArgb
                    thickness = 3f
                    onReset()
                }) { Text("Default", color = mutedColor) }
                TextButton(onClick = onDismiss) { Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "Line color",
            initialColorArgb = selectedColor,
            onDismiss = { showColorPicker = false },
            onSelect = { color ->
                selectedColor = color
                onUpdate(selectedColor, thickness)
                showColorPicker = false
            },
            allowReset = true,
            onReset = {
                selectedColor = defaultColorArgb
                onReset()
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun LineStyleDialog(
    line: LineEntity,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onUpdate: (LineEntity) -> Unit
) {
    var selectedColor by remember(line.id) { mutableStateOf(line.colorArgb) }
    var thickness by remember(line.id) { mutableStateOf(line.strokeWidth) }
    var showColorPicker by remember { mutableStateOf(false) }
    val isWhiteTheme = themeMode == ThemeMode.WHITE
    val textColor = if (isWhiteTheme) Color(0xFF1A1A1A) else Color.White
    val mutedColor = if (isWhiteTheme) Color(0xFF5E5E68) else Color.LightGray
    val dialogBrush = if (isWhiteTheme) Brush.linearGradient(listOf(Color.White, Color(0xFFF0F1F6))) else Brush.linearGradient(listOf(GlassDark1, GlassDark2))

    Box(modifier = Modifier.width(220.dp).padding(14.dp)) {
        Column {
                Text("Line style", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Line color", color = mutedColor, modifier = Modifier.weight(1f))
                    ColorCircle(Color(selectedColor), onClick = { showColorPicker = true })
                }
                Spacer(Modifier.height(18.dp))
                Text("Thickness  ${"%.1f".format(thickness)}", color = mutedColor, fontSize = 14.sp)
                StyledValueSlider(
                    value = thickness,
                    valueRange = 1f..16f,
                    trackBrush = Brush.horizontalGradient(listOf(Color(selectedColor).copy(alpha = 0.35f), Color(selectedColor))),
                    thumbColor = Color(selectedColor),
                    onValueChange = { value ->
                        thickness = value
                        onUpdate(line.copy(colorArgb = selectedColor, strokeWidth = value))
                    }
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        selectedColor = 0xFF64FFDA
                        thickness = 4f
                        onUpdate(line.copy(colorArgb = selectedColor, strokeWidth = thickness))
                    }) { Text("Default", color = mutedColor) }
                    TextButton(onClick = onDismiss) { Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold) }
                }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "Line color", initialColorArgb = selectedColor,
            onDismiss = { showColorPicker = false },
            onSelect = { color ->
                selectedColor = color
                onUpdate(line.copy(colorArgb = color, strokeWidth = thickness))
                showColorPicker = false
            },
            allowReset = true,
            onReset = {
                selectedColor = 0xFF64FFDA
                thickness = 4f
                onUpdate(line.copy(colorArgb = selectedColor, strokeWidth = thickness))
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun BoxStyleDialog(
    node: NodeEntity,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onStyleChange: (Long?, Long?, Float, Float) -> Unit
) {
    var selectedColor by remember(node.id) { mutableStateOf(node.colorArgb) }
    var widthScale by remember(node.id) { mutableStateOf(node.widthScale) }
    var heightScale by remember(node.id) { mutableStateOf(node.heightScale) }
    var showBoxColorPicker by remember { mutableStateOf(false) }
    val isWhiteTheme = themeMode == ThemeMode.WHITE
    val textColor = if (isWhiteTheme) Color(0xFF1A1A1A) else Color.White
    val mutedColor = if (isWhiteTheme) Color(0xFF5E5E68) else Color.LightGray
    val dialogBrush = if (isWhiteTheme) Brush.linearGradient(listOf(Color.White, Color(0xFFF0F1F6))) else Brush.linearGradient(listOf(GlassDark1, GlassDark2))
    val displayColor = Color(selectedColor ?: 0xFF64FFDA)

    fun updateStyle() = onStyleChange(selectedColor, node.textColorArgb, widthScale, heightScale)

    Box(modifier = Modifier.width(220.dp).padding(14.dp)) {
        Column {
                Text("Box style", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Box color", color = mutedColor, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        selectedColor = null
                        updateStyle()
                    }) { Text("Default", color = AccentCyan, fontSize = 12.sp) }
                    ColorCircle(displayColor, onClick = { showBoxColorPicker = true })
                }
                Spacer(Modifier.height(18.dp))
                Text("Width  ${"%.0f".format(widthScale * 100)}%", color = mutedColor, fontSize = 14.sp)
                StyledValueSlider(
                    value = widthScale, valueRange = 0.65f..2.2f,
                    trackBrush = Brush.horizontalGradient(listOf(displayColor.copy(alpha = 0.35f), displayColor)),
                    thumbColor = displayColor,
                    onValueChange = { value -> widthScale = value; updateStyle() }
                )
                Text("Height  ${"%.0f".format(heightScale * 100)}%", color = mutedColor, fontSize = 14.sp)
                StyledValueSlider(
                    value = heightScale, valueRange = 0.65f..2.2f,
                    trackBrush = Brush.horizontalGradient(listOf(displayColor.copy(alpha = 0.35f), displayColor)),
                    thumbColor = displayColor,
                    onValueChange = { value -> heightScale = value; updateStyle() }
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        widthScale = 1f
                        heightScale = 1f
                        updateStyle()
                    }) { Text("Default size", color = mutedColor) }
                    TextButton(onClick = onDismiss) { Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold) }
                }
        }
    }

    if (showBoxColorPicker) {
        ColorPickerDialog(
            title = "Box color", initialColorArgb = selectedColor ?: 0xFF64FFDA,
            onDismiss = { showBoxColorPicker = false },
            onSelect = { color -> selectedColor = color; updateStyle(); showBoxColorPicker = false },
            allowReset = false, onReset = {}
        )
    }

}

@Composable
fun ColorPickerDialog(
    title: String,
    initialColorArgb: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    allowReset: Boolean,
    onReset: () -> Unit
) {
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb.toInt(), it) }
    }
    var hue by remember(initialColorArgb) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(initialColorArgb) { mutableStateOf(initialHsv[1]) }
    var brightness by remember(initialColorArgb) { mutableStateOf(initialHsv[2]) }
    val selectedArgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFFL
    val selectedColor = Color(selectedArgb)

    fun setFromArgb(argb: Long) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb.toInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(GlassDark1.copy(alpha = 0.96f), GlassDark2.copy(alpha = 0.96f))))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(selectedColor)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(14.dp))
                FlowRowSwatches(onSelect = ::setFromArgb)
                Spacer(Modifier.height(14.dp))
                Text("Hue", color = Color.LightGray, fontSize = 13.sp)
                StyledValueSlider(
                    value = hue,
                    valueRange = 0f..360f,
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    ),
                    thumbColor = selectedColor,
                    onValueChange = { hue = it }
                )
                Text("Saturation", color = Color.LightGray, fontSize = 13.sp)
                StyledValueSlider(
                    value = saturation,
                    valueRange = 0f..1f,
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color.White, Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, brightness))))
                    ),
                    thumbColor = selectedColor,
                    onValueChange = { saturation = it }
                )
                Text("Brightness", color = Color.LightGray, fontSize = 13.sp)
                StyledValueSlider(
                    value = brightness,
                    valueRange = 0f..1f,
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color.Black, Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))))
                    ),
                    thumbColor = selectedColor,
                    onValueChange = { brightness = it }
                )
                if (allowReset) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) { Text("Reset to default", color = Color.LightGray) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                    TextButton(onClick = { onSelect(selectedArgb) }) {
                        Text("Apply", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRowSwatches(onSelect: (Long) -> Unit) {
    val rows = ColorSwatches.chunked(4)
    Column {
        rows.forEach { row ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .pointerInput(argb) { detectTapGestures(onTap = { onSelect(argb) }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StyledValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackBrush: Brush,
    thumbColor: Color,
    onValueChange: (Float) -> Unit
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    val rangeSize = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f)

    fun updateFromX(positionX: Float) {
        if (trackWidthPx <= 0f) return
        val newFraction = (positionX / trackWidthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + rangeSize * newFraction)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .pointerInput(trackWidthPx, valueRange.start, valueRange.endInclusive) {
                coroutineScope {
                    launch { detectTapGestures(onTap = { updateFromX(it.x) }) }
                    launch {
                        detectDragGestures(
                            onDragStart = { updateFromX(it.x) }
                        ) { change, _ ->
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(CircleShape)
                .background(trackBrush)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((trackWidthPx * fraction).roundToInt() - 10, 0) }
                .size(20.dp)
                .shadow(8.dp, CircleShape, ambientColor = thumbColor, spotColor = thumbColor)
                .clip(CircleShape)
                .background(thumbColor)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
        )
    }
}

@Composable
private fun SettingsDialog(
    currentGlow: Float,
    currentCollapseStyle: CollapseAnimationStyle,
    currentTheme: ThemeMode,
    currentGlowColor: Long,
    zoomEnabled: Boolean,
    longPressPanEnabled: Boolean,
    smartRootLayoutEnabled: Boolean,
    rootCollisionBehavior: RootCollisionBehavior,
    multipleRootsEnabled: Boolean,
    currentSectionStyle: SectionStyle,
    applySectionStyleToAll: Boolean,
    sections: List<SectionEntity>,
    media: List<MediaEntity>,
    onDismiss: () -> Unit,
    onMediaClick: (MediaEntity) -> Unit,
    onGlowChange: (Float) -> Unit,
    onCollapseStyleChange: (CollapseAnimationStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onGlowColorChange: (Long) -> Unit,
    onZoomEnabledChange: (Boolean) -> Unit,
    onLongPressPanEnabledChange: (Boolean) -> Unit,
    onSmartRootLayoutEnabledChange: (Boolean) -> Unit,
    onRootCollisionBehaviorChange: (RootCollisionBehavior) -> Unit,
    onMultipleRootsEnabledChange: (Boolean) -> Unit,
    onSectionBackgroundChange: (Long) -> Unit,
    onSectionTextColorChange: (Long) -> Unit,
    onSectionTextColorReset: () -> Unit,
    onSectionBoxColorChange: (Long) -> Unit,
    onSectionBoxColorReset: () -> Unit,
    onSectionCompletionColorChange: (Long) -> Unit,
    onSectionCompletionColorReset: () -> Unit,
    onApplySectionStyleToAllChange: (Boolean) -> Unit
) {
    var showGlowColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBoxColorPicker by remember { mutableStateOf(false) }
    var showCompletionColorPicker by remember { mutableStateOf(false) }
    var showMediaLibrary by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    val closeProgress by animateFloatAsState(
        targetValue = if (isClosing) 0f else 1f,
        animationSpec = tween(180),
        label = "settingsClose"
    )
    val coroutineScope = rememberCoroutineScope()
    val isWhiteTheme = currentTheme == ThemeMode.WHITE
    val dialogTextColor = if (isWhiteTheme) Color(0xFF1A1A1A) else Color.White
    val dialogMutedColor = if (isWhiteTheme) Color(0xFF5E5E68) else Color.LightGray
    val dialogBrush = if (isWhiteTheme) {
        Brush.linearGradient(listOf(Color.White, Color(0xFFF0F1F6)))
    } else {
        Brush.linearGradient(listOf(GlassDark1.copy(alpha = 0.96f), GlassDark2.copy(alpha = 0.96f)))
    }
    val dialogBorderColor = if (isWhiteTheme) Color(0x22000000) else Color.White.copy(alpha = 0.15f)

    Dialog(onDismissRequest = { if (!isClosing) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = closeProgress
                    scaleX = 0.96f + closeProgress * 0.04f
                    scaleY = 0.96f + closeProgress * 0.04f
                }
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBrush)
                .border(1.dp, dialogBorderColor, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("Settings", color = dialogTextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pinch zoom", color = dialogTextColor, fontSize = 15.sp)
                        Text("Use two fingers to zoom the page", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = zoomEnabled,
                        onCheckedChange = onZoomEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Long-press to pan", color = dialogTextColor, fontSize = 15.sp)
                        Text("Hold an empty area, then drag the canvas", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = longPressPanEnabled,
                        onCheckedChange = onLongPressPanEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Avoid child overlap", color = dialogTextColor, fontSize = 15.sp)
                        Text("Move or hide a main box below open children", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = smartRootLayoutEnabled,
                        onCheckedChange = onSmartRootLayoutEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.3f))
                    )
                }
                if (smartRootLayoutEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("When boxes overlap", color = dialogMutedColor, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = rootCollisionBehavior == RootCollisionBehavior.MOVE,
                            onClick = { onRootCollisionBehaviorChange(RootCollisionBehavior.MOVE) },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Text("Move down", color = dialogTextColor, fontSize = 14.sp)
                        Spacer(Modifier.width(12.dp))
                        RadioButton(
                            selected = rootCollisionBehavior == RootCollisionBehavior.HIDE,
                            onClick = { onRootCollisionBehaviorChange(RootCollisionBehavior.HIDE) },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Text("Hide", color = dialogTextColor, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep multiple child groups open", color = dialogTextColor, fontSize = 15.sp)
                        Text("Off keeps only one main box expanded", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = multipleRootsEnabled,
                        onCheckedChange = onMultipleRootsEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("Theme", color = dialogMutedColor, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf(ThemeMode.DEFAULT to "Default", ThemeMode.WHITE to "White").forEach { (mode, label) ->
                        val selected = currentTheme == mode
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) AccentCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                .border(1.dp, if (selected) AccentCyan else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .pointerInput(mode) { detectTapGestures(onTap = { onThemeChange(mode) }) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(label, color = dialogTextColor) }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Glow intensity", color = dialogMutedColor, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(currentGlowColor).copy(alpha = 0.15f), Color(currentGlowColor).copy(alpha = 0.35f))
                            )
                        )
                        .border(1.dp, Color(currentGlowColor).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    StyledValueSlider(
                        value = currentGlow,
                        valueRange = 0f..MaxGlowIntensity,
                        trackBrush = Brush.horizontalGradient(
                            listOf(Color(currentGlowColor).copy(alpha = 0.35f), Color(currentGlowColor))
                        ),
                        thumbColor = Color(currentGlowColor),
                        onValueChange = onGlowChange
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Media", color = dialogTextColor, fontSize = 15.sp)
                        Text("Images and files by section", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    TextButton(onClick = { showMediaLibrary = true }) {
                        Text("Open", color = AccentCyan)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background color", color = dialogMutedColor, fontSize = 14.sp)
                        Text("This section's page color", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    ColorCircle(
                        color = Color(currentSectionStyle.backgroundArgb ?: if (isWhiteTheme) 0xFFF4F4F8 else 0xFF0F1020),
                        onClick = { showBackgroundColorPicker = true }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Box color", color = dialogMutedColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSectionBoxColorReset, modifier = Modifier.width(76.dp)) {
                        Text("Default", color = dialogTextColor, fontSize = 12.sp)
                    }
                    ColorCircle(
                        color = Color(currentSectionStyle.boxArgb ?: if (isWhiteTheme) 0xFFFFFFFF else 0xFF2A2A3C),
                        onClick = { showBoxColorPicker = true }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Box text color", color = dialogMutedColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSectionTextColorReset, modifier = Modifier.width(76.dp)) {
                        Text("Default", color = dialogTextColor, fontSize = 12.sp)
                    }
                    ColorCircle(
                        color = Color(currentSectionStyle.textArgb ?: if (isWhiteTheme) 0xFF1A1A1A else 0xFFFFFFFF),
                        onClick = { showTextColorPicker = true }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Complete line color", color = dialogMutedColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSectionCompletionColorReset, modifier = Modifier.width(76.dp)) {
                        Text("Default", color = dialogTextColor, fontSize = 12.sp)
                    }
                    ColorCircle(
                        color = Color(currentSectionStyle.completionArgb ?: 0xFF4CAF50),
                        onClick = { showCompletionColorPicker = true }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Apply to all sections", color = dialogTextColor, fontSize = 14.sp)
                        Text("Background and text color", color = dialogMutedColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = applySectionStyleToAll,
                        onCheckedChange = onApplySectionStyleToAllChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Glow color", color = dialogMutedColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(currentGlowColor))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .pointerInput(Unit) { detectTapGestures(onTap = { showGlowColorPicker = true }) }
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("Collapse animation", color = dialogMutedColor, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    RadioButton(
                        selected = currentCollapseStyle == CollapseAnimationStyle.FADE,
                        onClick = { onCollapseStyleChange(CollapseAnimationStyle.FADE) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                    )
                    Text("Fade out", color = dialogTextColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    RadioButton(
                        selected = currentCollapseStyle == CollapseAnimationStyle.LINE_RETRACT,
                        onClick = { onCollapseStyleChange(CollapseAnimationStyle.LINE_RETRACT) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                    )
                    Text("Retract along line", color = dialogTextColor)
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        enabled = !isClosing,
                        onClick = {
                            if (!isClosing) {
                                isClosing = true
                                coroutineScope.launch {
                                    delay(180)
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.width(104.dp).height(52.dp)
                    ) {
                        Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (showGlowColorPicker) {
        ColorPickerDialog(
            title = "Glow color",
            initialColorArgb = currentGlowColor,
            onDismiss = { showGlowColorPicker = false },
            onSelect = { argb -> onGlowColorChange(argb); showGlowColorPicker = false },
            allowReset = false,
            onReset = {}
        )
    }
    if (showMediaLibrary) {
        MediaLibraryDialog(
            sections = sections,
            media = media,
            onDismiss = { showMediaLibrary = false },
            onMediaClick = {
                showMediaLibrary = false
                onMediaClick(it)
            }
        )
    }
    if (showBackgroundColorPicker) {
        ColorPickerDialog(
            title = "Background color",
            initialColorArgb = currentSectionStyle.backgroundArgb ?: if (isWhiteTheme) 0xFFF4F4F8 else 0xFF0F1020,
            onDismiss = { showBackgroundColorPicker = false },
            onSelect = { argb -> onSectionBackgroundChange(argb); showBackgroundColorPicker = false },
            allowReset = false,
            onReset = {}
        )
    }
    if (showTextColorPicker) {
        ColorPickerDialog(
            title = "Box text color",
            initialColorArgb = currentSectionStyle.textArgb ?: if (isWhiteTheme) 0xFF1A1A1A else 0xFFFFFFFF,
            onDismiss = { showTextColorPicker = false },
            onSelect = { argb -> onSectionTextColorChange(argb); showTextColorPicker = false },
            allowReset = false,
            onReset = {}
        )
    }
    if (showBoxColorPicker) {
        ColorPickerDialog(
            title = "Section box color",
            initialColorArgb = currentSectionStyle.boxArgb ?: if (isWhiteTheme) 0xFFFFFFFF else 0xFF2A2A3C,
            onDismiss = { showBoxColorPicker = false },
            onSelect = { argb -> onSectionBoxColorChange(argb); showBoxColorPicker = false },
            allowReset = false,
            onReset = {}
        )
    }
    if (showCompletionColorPicker) {
        ColorPickerDialog(
            title = "Complete line color",
            initialColorArgb = currentSectionStyle.completionArgb ?: 0xFF4CAF50,
            onDismiss = { showCompletionColorPicker = false },
            onSelect = { argb -> onSectionCompletionColorChange(argb); showCompletionColorPicker = false },
            allowReset = false,
            onReset = {}
        )
    }
}

@Composable
private fun MediaLibraryDialog(
    sections: List<SectionEntity>,
    media: List<MediaEntity>,
    onDismiss: () -> Unit,
    onMediaClick: (MediaEntity) -> Unit
) {
    var selectedType by remember { mutableStateOf(MediaType.IMAGE) }
    var selectedSectionId by remember { mutableStateOf<Long?>(null) }
    val filteredMedia = media.filter { it.type == selectedType }
    val mediaSectionIds = filteredMedia.map { it.sectionId }.toSet()
    val availableSections = sections.filter { it.id in mediaSectionIds }

    LaunchedEffect(selectedType, media) {
        if (selectedSectionId !in mediaSectionIds) {
            selectedSectionId = availableSections.firstOrNull()?.id
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = GlassDark1,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Media", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row {
                    MediaTab("Images", selectedType == MediaType.IMAGE) { selectedType = MediaType.IMAGE }
                    Spacer(Modifier.width(10.dp))
                    MediaTab("Files", selectedType == MediaType.FILE) { selectedType = MediaType.FILE }
                }
                Spacer(Modifier.height(14.dp))
                if (availableSections.isEmpty()) {
                    Text("No ${if (selectedType == MediaType.IMAGE) "images" else "files"}", color = Color.LightGray)
                } else {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        availableSections.forEach { section ->
                            val selected = section.id == selectedSectionId
                            Text(
                                text = section.title,
                                color = if (selected) AccentCyan else Color.White,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) AccentCyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                                    .pointerInput(section.id) { detectTapGestures(onTap = { selectedSectionId = section.id }) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val selectedMedia = filteredMedia.filter { it.sectionId == selectedSectionId }
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (selectedType == MediaType.IMAGE) {
                            selectedMedia.chunked(3).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { item ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(82.dp)
                                                .padding(3.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .pointerInput(item.id) { detectTapGestures(onTap = { onMediaClick(item) }) }
                                        ) {
                                            MediaThumbnail(
                                                uri = item.uri,
                                                rotationDegrees = item.rotationDegrees,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            selectedMedia.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(item.id) { detectTapGestures(onTap = { onMediaClick(item) }) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = AccentCyan)
                                    Spacer(Modifier.width(10.dp))
                                    Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = AccentCyan)
                }
            }
        }
    }
}

@Composable
private fun MediaTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color(0xFF0F1020) else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentCyan else Color.White.copy(alpha = 0.10f))
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun PdfSectionsList(
    sections: List<PdfLibrarySection>,
    libraryStyle: PdfLibraryStyle,
    draggingSectionId: String?,
    onDraggingSectionIdChange: (String?) -> Unit,
    sectionReorderDistance: SnapshotStateMap<String, Float>,
    reorderThreshold: Float,
    onReorder: (List<PdfLibrarySection>) -> Unit,
    onOpenSection: (String) -> Unit,
    onSectionAction: (PdfLibrarySection) -> Unit,
    onIconClick: (PdfLibrarySection) -> Unit,
    interactive: Boolean = true
) {
    if (sections.isEmpty()) {
        Text("Create a PDF section with +", color = Color.LightGray, modifier = Modifier.padding(20.dp))
        return
    }

    var localList by remember { mutableStateOf(sections) }
    val itemHeight = 70.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var orderChangedDuringDrag by remember { mutableStateOf(false) }
    var dragStartOrder by remember { mutableStateOf<List<PdfLibrarySection>>(emptyList()) }
    val releaseOffset = remember { Animatable(0f) }
    var settlingDrag by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val dragScope = rememberCoroutineScope()
    var pressedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sections, draggingId, settlingDrag) {
        if (draggingId == null && !settlingDrag) {
            localList = sections
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(localList, key = { it.id }) { section ->
            val isDragging = draggingId == section.id
            val offsetY = when {
                isDragging && settlingDrag -> releaseOffset.value
                isDragging -> dragOffset
                else -> 0f
            }
            val rowScale by animateFloatAsState(
                targetValue = if (pressedId == section.id || isDragging) 0.98f else 1f,
                animationSpec = tween(120),
                label = "pdfSectionPress"
            )
            var rowModifier = Modifier
                .fillMaxWidth()
                .then(if (isDragging) Modifier else Modifier.animateItem())
                .zIndex(if (isDragging) 2f else 0f)
                .graphicsLayer { translationY = offsetY; scaleX = rowScale; scaleY = rowScale }
            if (interactive) {
                rowModifier = rowModifier
                    .pointerInput(section.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                settleJob?.cancel()
                                settlingDrag = false
                                val originIndex = localList.indexOfFirst { it.id == section.id }
                                if (originIndex < 0) return@detectDragGesturesAfterLongPress
                                draggingId = section.id
                                dragStartOrder = localList
                                dragOffset = 0f
                                orderChangedDuringDrag = false
                                pressedId = section.id
                                onDraggingSectionIdChange(section.id)
                            },
                            onDragEnd = {
                                if (draggingId != section.id) return@detectDragGesturesAfterLongPress
                                if (orderChangedDuringDrag) onReorder(localList)
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(150))
                                    if (draggingId == section.id) {
                                        draggingId = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedId = null
                                        onDraggingSectionIdChange(null)
                                    }
                                }
                            },
                            onDragCancel = {
                                if (draggingId != section.id) return@detectDragGesturesAfterLongPress
                                localList = dragStartOrder
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(160))
                                    if (draggingId == section.id) {
                                        draggingId = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedId = null
                                        onDraggingSectionIdChange(null)
                                    }
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            if (draggingId != section.id) return@detectDragGesturesAfterLongPress
                            dragOffset += amount.y
                            val currentIndex = localList.indexOfFirst { it.id == section.id }
                            val direction = when {
                                dragOffset >= itemHeightPx / 2 && currentIndex < localList.lastIndex -> 1
                                dragOffset <= -itemHeightPx / 2 && currentIndex > 0 -> -1
                                else -> 0
                            }
                            if (direction != 0) {
                                val reordered = localList.toMutableList()
                                val moved = reordered.removeAt(currentIndex)
                                reordered.add(currentIndex + direction, moved)
                                localList = reordered
                                dragOffset -= direction * itemHeightPx
                                orderChangedDuringDrag = true
                            }
                        }
                    }
                    .pointerInput("pdf-section-tap-${section.id}") {
                        detectTapGestures(
                            onPress = {
                                pressedId = section.id
                                tryAwaitRelease()
                                if (draggingId != section.id) pressedId = null
                            },
                            onTap = { onOpenSection(section.id) },
                            onDoubleTap = { onSectionAction(section) }
                        )
                    }
            }
            Row(
                modifier = rowModifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (pressedId == section.id || isDragging) {
                            AccentCyan.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(
                            if (interactive) {
                                Modifier.pointerInput(section.id) {
                                    detectTapGestures(onTap = { onIconClick(section) })
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (section.iconUri != null) {
                        MediaThumbnail(
                            uri = section.iconUri,
                            maxSide = 320,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(Icons.Default.InsertDriveFile, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        section.title,
                        color = Color(section.textArgb ?: libraryStyle.sectionTextArgb ?: libraryStyle.textArgb ?: 0xFFFFFFFF),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${section.entries.size} PDF",
                        color = Color(section.textArgb ?: libraryStyle.sectionTextArgb ?: libraryStyle.textArgb ?: 0xFFFFFFFF).copy(alpha = 0.66f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
@Composable
private fun PdfSectionEntriesList(
    entries: List<PdfLibraryEntry>,
    onOpen: (PdfLibraryEntry) -> Unit,
    onEntryAction: (PdfLibraryEntry) -> Unit,
    onReorder: (List<PdfLibraryEntry>) -> Unit
) {
    if (entries.isEmpty()) {
        Text("No PDFs in this section", color = Color.LightGray, modifier = Modifier.padding(20.dp))
        return
    }

    var localList by remember { mutableStateOf(entries) }
    val itemHeight = 64.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    var draggingPath by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var orderChangedDuringDrag by remember { mutableStateOf(false) }
    var dragStartOrder by remember { mutableStateOf<List<PdfLibraryEntry>>(emptyList()) }
    val releaseOffset = remember { Animatable(0f) }
    var settlingDrag by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val dragScope = rememberCoroutineScope()
    var pressedPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entries, draggingPath, settlingDrag) {
        if (draggingPath == null && !settlingDrag) {
            localList = entries
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(localList, key = { it.path }) { entry ->
            val file = DeviceFile(File(entry.path), entry.displayName, "pdf")
            val isDragging = draggingPath == entry.path
            val offsetY = when {
                isDragging && settlingDrag -> releaseOffset.value
                isDragging -> dragOffset
                else -> 0f
            }
            val scale by animateFloatAsState(
                targetValue = if (pressedPath == entry.path || isDragging) 0.97f else 1f,
                label = "pdfEntryPress"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 2f else 0f)
                    .graphicsLayer { translationY = offsetY; scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (pressedPath == entry.path || isDragging) AccentCyan.copy(alpha = 0.13f) else Color.Transparent)
                    .pointerInput(entry.path) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                settleJob?.cancel()
                                settlingDrag = false
                                draggingPath = entry.path
                                dragStartOrder = localList
                                dragOffset = 0f
                                orderChangedDuringDrag = false
                                pressedPath = entry.path
                            },
                            onDragEnd = {
                                if (draggingPath != entry.path) return@detectDragGesturesAfterLongPress
                                if (orderChangedDuringDrag) onReorder(localList)
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(150))
                                    if (draggingPath == entry.path) {
                                        draggingPath = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedPath = null
                                    }
                                }
                            },
                            onDragCancel = {
                                if (draggingPath != entry.path) return@detectDragGesturesAfterLongPress
                                localList = dragStartOrder
                                settleJob = dragScope.launch {
                                    releaseOffset.snapTo(dragOffset)
                                    settlingDrag = true
                                    releaseOffset.animateTo(0f, tween(160))
                                    if (draggingPath == entry.path) {
                                        draggingPath = null
                                        settlingDrag = false
                                        dragOffset = 0f
                                        orderChangedDuringDrag = false
                                        pressedPath = null
                                    }
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            if (draggingPath != entry.path) return@detectDragGesturesAfterLongPress
                            dragOffset += amount.y
                            val currentIndex = localList.indexOfFirst { it.path == entry.path }
                            val direction = when {
                                dragOffset >= itemHeightPx / 2 && currentIndex < localList.lastIndex -> 1
                                dragOffset <= -itemHeightPx / 2 && currentIndex > 0 -> -1
                                else -> 0
                            }
                            if (direction != 0) {
                                val reordered = localList.toMutableList()
                                val moved = reordered.removeAt(currentIndex)
                                reordered.add(currentIndex + direction, moved)
                                localList = reordered
                                dragOffset -= direction * itemHeightPx
                                orderChangedDuringDrag = true
                            }
                        }
                    }
                    .pointerInput("pdf-entry-tap-${entry.path}") {
                        detectTapGestures(
                            onPress = {
                                pressedPath = entry.path
                                tryAwaitRelease()
                                if (draggingPath != entry.path) pressedPath = null
                            },
                            onTap = { onOpen(entry) },
                            onDoubleTap = { onEntryAction(entry) }
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PdfThumbnail(file = file, modifier = Modifier.size(width = 34.dp, height = 40.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDeviceFileTime(file.file), color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PdfFilesTabBody(
    files: List<DeviceFile>,
    query: String,
    onQueryChange: (String) -> Unit,
    searchBarVisible: Boolean,
    skipVisibilityAnimation: Boolean,
    textColor: Color,
    surfaceColor: Color,
    onOpen: (DeviceFile) -> Unit,
    onLongPressFile: (DeviceFile) -> Unit,
    onDoubleTapFile: (DeviceFile) -> Unit = onLongPressFile,
    selectionEnabled: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onSelectChange: (DeviceFile, Boolean) -> Unit = { _, _ -> },
    interactive: Boolean = true
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = searchBarVisible,
            enter = if (skipVisibilityAnimation) androidx.compose.animation.EnterTransition.None
            else expandVertically(tween(180)) + fadeIn(tween(160)),
            exit = if (skipVisibilityAnimation) androidx.compose.animation.ExitTransition.None
            else shrinkVertically(tween(160)) + fadeOut(tween(130))
        ) {
            SmartPdfSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                textColor = textColor,
                surfaceColor = surfaceColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            PdfDeviceFileList(
                files = files,
                selectedPaths = selectedPaths,
                selectionEnabled = selectionEnabled,
                onSelectChange = onSelectChange,
                onOpen = if (interactive) onOpen else { _ -> },
                onDoubleTap = if (interactive) { file -> if (file.extension == "pdf") onDoubleTapFile(file) } else { _ -> },
                onLongPress = { file -> if (interactive && file.extension == "pdf") onLongPressFile(file) }
            )
        }
    }
}

@Composable
private fun SmartPdfSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    textColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 15.sp, lineHeight = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceColor.copy(alpha = 0.86f)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = AccentCyan, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text("Smart search PDFs", color = textColor.copy(alpha = 0.52f), fontSize = 15.sp)
                    }
                    innerTextField()
                }
                if (query.isNotBlank()) {
                    Text(
                        text = "×",
                        color = textColor.copy(alpha = 0.72f),
                        fontSize = 24.sp,
                        modifier = Modifier.pointerInput("clear-pdf-search") {
                            detectTapGestures(onTap = { onQueryChange("") })
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PdfLibraryHomeDialog(
    onDismiss: () -> Unit,
    onNavigateToMindMap: () -> Unit,
    onNavigateToTimer: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onFileClick: (DeviceFile) -> Unit
) {
    val context = LocalContext.current
    val libraryPreferences = remember(context) {
        context.getSharedPreferences("pdf_library", android.content.Context.MODE_PRIVATE)
    }
    var hasAllFilesAccess by remember { mutableStateOf(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()) }
    val cachedFilesAtOpen = remember { cachedDeviceFiles() ?: loadPersistedDeviceFiles(context).takeIf { it.isNotEmpty() } }
    var files by remember { mutableStateOf(cachedFilesAtOpen.orEmpty()) }
    var isLoading by remember { mutableStateOf(cachedFilesAtOpen == null && hasAllFilesAccess) }
    var activeTab by remember { mutableStateOf(libraryPreferences.getString("last_tab", "files") ?: "files") }
    var pdfSearchQuery by remember { mutableStateOf("") }
    var isPdfSearchVisible by remember { mutableStateOf(true) }
    var openedSectionId by remember { mutableStateOf<String?>(null) }
    var selectingForSectionId by remember { mutableStateOf<String?>(null) }
    var selectedPdfPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var removeSelectionDialog by remember { mutableStateOf(false) }
    var sectionTargetsForSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSectionTargetDialog by remember { mutableStateOf(false) }
    var sections by remember { mutableStateOf(loadPdfLibrarySections(context)) }
    var fileActionFor by remember { mutableStateOf<DeviceFile?>(null) }
    var sectionActionFor by remember { mutableStateOf<PdfLibrarySection?>(null) }
    var entryActionFor by remember { mutableStateOf<Pair<String, PdfLibraryEntry>?>(null) }
    var chooseSectionForFile by remember { mutableStateOf<DeviceFile?>(null) }
    var moveEntryRequest by remember { mutableStateOf<Pair<String, PdfLibraryEntry>?>(null) }
    var createSectionMode by remember { mutableStateOf<String?>(null) }
    var createSectionFile by remember { mutableStateOf<DeviceFile?>(null) }
    var createSectionMove by remember { mutableStateOf<Pair<String, PdfLibraryEntry>?>(null) }
    var renameSectionFor by remember { mutableStateOf<PdfLibrarySection?>(null) }
    var renameEntryFor by remember { mutableStateOf<Pair<String, PdfLibraryEntry>?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showLibrarySettings by remember { mutableStateOf(false) }
    var sectionBackgroundColorFor by remember { mutableStateOf<PdfLibrarySection?>(null) }
    var sectionTextColorFor by remember { mutableStateOf<PdfLibrarySection?>(null) }
    var sectionIconPickerFor by remember { mutableStateOf<PdfLibrarySection?>(null) }
    var libraryStyle by remember { mutableStateOf(loadPdfLibraryStyle(context)) }
    var draggingSectionId by remember { mutableStateOf<String?>(null) }
    val sectionReorderDistance = remember { mutableStateMapOf<String, Float>() }
    val currentSections by rememberUpdatedState(sections)
    val reorderThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    var tabSwipeDistance by remember { mutableFloatStateOf(0f) }
    var tabDirection by remember { mutableIntStateOf(1) }
    var skipTabAnimation by remember { mutableStateOf(false) }
    var tabPageWidth by remember { mutableFloatStateOf(1f) }
    var isTabSwipeActive by remember { mutableStateOf(false) }
    val tabSwipeScope = rememberCoroutineScope()
    val tabSwipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val libraryBackground = Color(libraryStyle.backgroundArgb ?: 0xFF101822)
    val libraryText = Color(libraryStyle.textArgb ?: 0xFFFFFFFF)
    val librarySectionBackground = Color(libraryStyle.sectionBackgroundArgb ?: 0xFF1A2633)
    val librarySectionText = Color(libraryStyle.sectionTextArgb ?: libraryStyle.textArgb ?: 0xFFFFFFFF)
    val hiddenPdfPaths = remember { mutableStateOf(libraryPreferences.getStringSet("hidden_pdf_paths", emptySet()).orEmpty()) }
    val refreshVersion = PdfLibraryRefresh.version
    val homeFiles = remember(files, pdfSearchQuery, hiddenPdfPaths.value) {
        pdfSearchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
            files.filter { file ->
                file.extension == "pdf" && file.name.contains(query, ignoreCase = true)
            }
        }?.filterNot { it.file.path in hiddenPdfPaths.value } ?: files.filterNot { it.file.path in hiddenPdfPaths.value }
    }

    fun updateSections(updatedSections: List<PdfLibrarySection>) {
        sections = updatedSections
        savePdfLibrarySections(context, updatedSections)
    }

    fun updateLibraryStyle(updatedStyle: PdfLibraryStyle) {
        libraryStyle = updatedStyle
        savePdfLibraryStyle(context, updatedStyle)
    }


    fun addPdfToSection(sectionId: String, file: DeviceFile) {
        updateSections(sections.map { section ->
            if (section.id != sectionId || section.entries.any { it.path == file.file.path }) section
            else section.copy(entries = section.entries + PdfLibraryEntry(file.file.path, file.name))
        })
    }

    val sectionIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSection = sectionIconPickerFor
        if (uri != null && targetSection != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = resolveAttachmentDisplayName(context, uri)
            val savedUri = runCatching { copyAttachmentToAppStorage(context, uri, displayName) }.getOrNull()
            val newIconUri = (savedUri ?: uri).toString()
            updateSections(sections.map { if (it.id == targetSection.id) it.copy(iconUri = newIconUri) else it })
        }
        sectionIconPickerFor = null
    }

    LaunchedEffect(sectionIconPickerFor) {
        if (sectionIconPickerFor != null) {
            sectionIconPicker.launch(arrayOf("image/*"))
        }
    }
    fun switchTab(targetTab: String) {
        if (activeTab == targetTab) return
        tabSwipeScope.launch {
            val startDistance = if (targetTab == "sections") tabPageWidth else -tabPageWidth
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                activeTab = targetTab
                tabSwipeDistance = startDistance
            }
            val animation = Animatable(startDistance)
            animation.animateTo(0f, tween(250)) { tabSwipeDistance = value }
        }
    }
    fun navigateBack() {
        when {
            selectionMode -> {
                selectionMode = false
                selectedPdfPaths = emptySet()
            }
            selectingForSectionId != null -> {
                selectingForSectionId = null
                selectedPdfPaths = emptySet()
                activeTab = "sections"
            }
            openedSectionId != null -> {
                openedSectionId = null
                activeTab = "sections"
            }
            activeTab == "sections" -> activeTab = "files"
            else -> onDismiss()
        }
    }

    LaunchedEffect(activeTab) {
        libraryPreferences.edit().putString("last_tab", activeTab).apply()
        if (skipTabAnimation) {
            delay(260)
            skipTabAnimation = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            hasAllFilesAccess = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()
            delay(750)
        }
    }
    LaunchedEffect(hasAllFilesAccess) {
        if (hasAllFilesAccess) {
            val memoryCached = cachedDeviceFiles()
            if (memoryCached != null) {
                files = memoryCached
                isLoading = false
            } else {
                val persisted = loadPersistedDeviceFiles(context)
                if (persisted.isNotEmpty()) {
                    files = persisted
                    isLoading = false
                } else {
                    isLoading = true
                }
                val freshFiles = withContext(Dispatchers.IO) { findDeviceFiles() }
                files = freshFiles
                isLoading = false
                savePersistedDeviceFiles(context, freshFiles)
            }
        }
    }
    LaunchedEffect(refreshVersion) {
        if (hasAllFilesAccess && refreshVersion > 0) {
            val freshFiles = withContext(Dispatchers.IO) { findDeviceFiles() }
            files = freshFiles
            savePersistedDeviceFiles(context, freshFiles)
        }
    }

    fun requestAllFilesAccess() {
        val appSettingsIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(appSettingsIntent) }.getOrElse {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    BackHandler(onBack = ::navigateBack)
    Dialog(
        onDismissRequest = ::navigateBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = libraryBackground, modifier = Modifier.fillMaxSize(), contentColor = libraryText) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { tabPageWidth = it.size.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(openedSectionId, selectingForSectionId, activeTab) {
                            if (openedSectionId == null && selectingForSectionId == null) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        isTabSwipeActive = true
                                        tabSwipeDistance = 0f
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        tabSwipeDistance = (tabSwipeDistance + amount).coerceIn(-tabPageWidth, tabPageWidth)
                                    },
                                    onDragCancel = {
                                        tabSwipeScope.launch {
                                            val animation = Animatable(tabSwipeDistance)
                                            animation.animateTo(0f, tween(170)) { tabSwipeDistance = value }
                                            isTabSwipeActive = false
                                        }
                                    },
                                    onDragEnd = {
                                        val targetTab = when {
                                            tabSwipeDistance <= -tabSwipeThreshold && activeTab == "files" -> "sections"
                                            tabSwipeDistance >= tabSwipeThreshold && activeTab == "sections" -> "files"
                                            else -> null
                                        }
                                        tabSwipeScope.launch {
                                            if (targetTab != null) {
                                                val continuedDistance = if (targetTab == "sections") {
                                                    tabSwipeDistance + tabPageWidth
                                                } else {
                                                    tabSwipeDistance - tabPageWidth
                                                }
                                                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                                    activeTab = targetTab
                                                    tabSwipeDistance = continuedDistance
                                                }
                                                val animation = Animatable(continuedDistance)
                                                animation.animateTo(0f, tween(220)) { tabSwipeDistance = value }
                                                isTabSwipeActive = false
                                            } else {
                                                val animation = Animatable(tabSwipeDistance)
                                                animation.animateTo(0f, tween(200)) { tabSwipeDistance = value }
                                                isTabSwipeActive = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    if (openedSectionId != null) {
                        val openedSection = sections.firstOrNull { it.id == openedSectionId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(librarySectionBackground.copy(alpha = 0.94f))
                                .border(1.dp, libraryText.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = ::navigateBack) { Text("Back", color = SoftNeutral) }
                            Text(openedSection?.title ?: "PDF Section", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { showHomeMenu = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "PDF settings", tint = libraryText)
                                }
                                DropdownMenu(
                                    expanded = showHomeMenu,
                                    onDismissRequest = { showHomeMenu = false },
                                    containerColor = GlassDark1,
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 14.dp
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Files settings", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; showLibrarySettings = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mind map", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; onNavigateToMindMap() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Timer", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; onNavigateToTimer() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Calendar", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; onNavigateToCalendar() }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(librarySectionBackground.copy(alpha = 0.94f))
                                .border(1.dp, libraryText.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { switchTab("files") }) {
                                Text("Files", color = if (activeTab == "files") SoftNeutral else libraryText)
                            }
                            TextButton(onClick = { switchTab("sections") }) {
                                Text("PDF Sections", color = if (activeTab == "sections") SoftNeutral else libraryText)
                            }
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pointerInput("toggle-pdf-search") {
                                        detectTapGestures(onDoubleTap = {
                                            isPdfSearchVisible = !isPdfSearchVisible
                                        })
                                    }
                            )
                            Box {
                                IconButton(onClick = { showHomeMenu = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "PDF settings", tint = libraryText)
                                }
                                DropdownMenu(
                                    expanded = showHomeMenu,
                                    onDismissRequest = { showHomeMenu = false },
                                    containerColor = GlassDark1,
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 14.dp
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Files settings", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; showLibrarySettings = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mind map", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; onNavigateToMindMap() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Calendar", color = librarySectionText) },
                                        onClick = { showHomeMenu = false; onNavigateToCalendar() }
                                    )
                                }
                            }
                        }
                    }

                    if (selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedPdfPaths.size} selected", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            TextButton(enabled = selectedPdfPaths.isNotEmpty(), onClick = { removeSelectionDialog = true }) { Text("Remove", color = Color(0xFFFF7A7A)) }
                            TextButton(enabled = selectedPdfPaths.isNotEmpty(), onClick = { showSectionTargetDialog = true }) { Text("Add Section", color = AccentCyan) }
                            TextButton(enabled = selectedPdfPaths.isNotEmpty(), onClick = { sharePdfFiles(context, files.filter { it.file.path in selectedPdfPaths }) }) { Text("Share", color = AccentCyan) }
                            TextButton(onClick = { selectionMode = false; selectedPdfPaths = emptySet() }) { Text("Cancel", color = AccentCyan) }
                        }
                    }
                    if (!hasAllFilesAccess) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Allow all-files access to browse phone PDFs", color = Color.LightGray)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = ::requestAllFilesAccess) { Text("Allow access") }
                        }
                    } else if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentCyan)
                        }
                    } else if (selectingForSectionId != null) {
                        val selectedSection = sections.firstOrNull { it.id == selectingForSectionId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select PDFs for ${selectedSection?.title ?: "section"}", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                selectingForSectionId?.let { sectionId ->
                                    files.filter { it.file.path in selectedPdfPaths }.forEach { addPdfToSection(sectionId, it) }
                                }
                                selectingForSectionId = null
                                selectedPdfPaths = emptySet()
                                activeTab = "sections"
                            }) { Text("Done", color = AccentCyan) }
                            TextButton(onClick = ::navigateBack) { Text("Cancel") }
                        }
                        PdfDeviceFileList(
                            files = files.filter { it.extension == "pdf" },
                            selectedPaths = selectedPdfPaths,
                            selectionEnabled = true,
                            onSelectChange = { file, selected ->
                                selectedPdfPaths = if (selected) selectedPdfPaths + file.file.path else selectedPdfPaths - file.file.path
                            },
                            onOpen = {},
                            onDoubleTap = {}
                        )
                    } else if (openedSectionId != null) {
                        val currentOpenedSectionId = openedSectionId!!
                        val openedSection = sections.firstOrNull { it.id == currentOpenedSectionId }
                        PdfSectionEntriesList(
                            entries = openedSection?.entries.orEmpty(),
                            onOpen = { entry -> onFileClick(DeviceFile(File(entry.path), entry.displayName, "pdf")) },
                            onEntryAction = { entry -> entryActionFor = currentOpenedSectionId to entry },
                            onReorder = { reordered ->
                                updateSections(sections.map { section ->
                                    if (section.id == currentOpenedSectionId) section.copy(entries = reordered) else section
                                })
                            }
                        )
                    }else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val filesOffset = (if (activeTab == "files") 0f else -tabPageWidth) + tabSwipeDistance
                            val sectionsOffset = (if (activeTab == "sections") 0f else tabPageWidth) + tabSwipeDistance

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = filesOffset }
                            ) {
                                PdfFilesTabBody(
                                    files = homeFiles,
                                    query = pdfSearchQuery,
                                    onQueryChange = { pdfSearchQuery = it },
                                    searchBarVisible = isPdfSearchVisible,
                                    skipVisibilityAnimation = true,
                                    textColor = libraryText,
                                    surfaceColor = librarySectionBackground,
                                    onOpen = onFileClick,
                                    onLongPressFile = { file ->
                                        selectionMode = true
                                        selectedPdfPaths = setOf(file.file.path)
                                    },
                                    onDoubleTapFile = { file -> fileActionFor = file },
                                    selectionEnabled = selectionMode,
                                    selectedPaths = selectedPdfPaths,
                                    onSelectChange = { file, selected ->
                                        val updatedSelection = if (selected) {
                                            selectedPdfPaths + file.file.path
                                        } else {
                                            selectedPdfPaths - file.file.path
                                        }
                                        selectedPdfPaths = updatedSelection
                                        if (updatedSelection.isEmpty()) selectionMode = false
                                    },
                                    interactive = activeTab == "files"
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = sectionsOffset }
                            ) {
                                PdfSectionsList(
                                    sections = sections,
                                    libraryStyle = libraryStyle,
                                    draggingSectionId = draggingSectionId,
                                    onDraggingSectionIdChange = { draggingSectionId = it },
                                    sectionReorderDistance = sectionReorderDistance,
                                    reorderThreshold = reorderThreshold,
                                    onReorder = ::updateSections,
                                    onOpenSection = { openedSectionId = it },
                                    onSectionAction = { sectionActionFor = it },
                                    onIconClick = { sectionIconPickerFor = it }
                                )
                            }
                        }
                    }
                }
                if (openedSectionId == null && selectingForSectionId == null && hasAllFilesAccess) {
                    val fabProgress = (abs(tabSwipeDistance) / tabPageWidth).coerceIn(0f, 1f)
                    val fabVisibleForSections = activeTab == "sections" && (!isTabSwipeActive || tabSwipeDistance <= 0f)
                    val fabVisibleForFilesEntering = activeTab == "files" && isTabSwipeActive && tabSwipeDistance > 0f
                    if (fabVisibleForSections || fabVisibleForFilesEntering) {
                        GlassFab(
                            onClick = { createSectionMode = "select" },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(22.dp)
                                .graphicsLayer {
                                    translationX = if (isTabSwipeActive) tabSwipeDistance else 0f
                                    alpha = if (isTabSwipeActive) (1f - fabProgress) else 1f
                                }
                        )
                    }
                }
            }
        }
    }


    if (removeSelectionDialog) {
        AlertDialog(
            onDismissRequest = { removeSelectionDialog = false },
            title = { Text("Remove PDF?") },
            text = { Text("Choose whether to hide the PDFs from this library or permanently delete their files.") },
            confirmButton = {
                TextButton(onClick = {
                    hiddenPdfPaths.value = hiddenPdfPaths.value + selectedPdfPaths
                    libraryPreferences.edit().putStringSet("hidden_pdf_paths", hiddenPdfPaths.value).apply()
                    updateSections(sections.map { section -> section.copy(entries = section.entries.filterNot { it.path in selectedPdfPaths }) })
                    selectionMode = false; selectedPdfPaths = emptySet(); removeSelectionDialog = false
                }) { Text("Remove from here", color = AccentCyan) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val paths = selectedPdfPaths
                        files.filter { it.file.path in paths }.forEach { it.file.delete() }
                        updateSections(sections.map { section -> section.copy(entries = section.entries.filterNot { it.path in paths }) })
                        files = files.filterNot { it.file.path in paths }
                        selectionMode = false; selectedPdfPaths = emptySet(); removeSelectionDialog = false
                    }) { Text("Permanently Delete", color = Color(0xFFFF7A7A)) }
                    TextButton(onClick = { removeSelectionDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showSectionTargetDialog) {
        PdfLibraryOptionsDialog(title = "Add to sections", onDismiss = { showSectionTargetDialog = false }) {
            sections.forEach { section ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = section.id in sectionTargetsForSelection, onCheckedChange = { checked ->
                        sectionTargetsForSelection = if (checked) sectionTargetsForSelection + section.id else sectionTargetsForSelection - section.id
                    })
                    Text(section.title)
                }
            }
            Button(
                enabled = sectionTargetsForSelection.isNotEmpty(),
                onClick = {
                    val chosenFiles = files.filter { it.file.path in selectedPdfPaths }
                    sectionTargetsForSelection.forEach { sectionId -> chosenFiles.forEach { addPdfToSection(sectionId, it) } }
                    showSectionTargetDialog = false; sectionTargetsForSelection = emptySet(); selectionMode = false; selectedPdfPaths = emptySet()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Move") }
        }
    }

    if (showLibrarySettings) {
        PdfLibrarySettingsDialog(
            style = libraryStyle,
            onStyleChange = ::updateLibraryStyle,
            onDismiss = { showLibrarySettings = false }
        )
    }

    sectionBackgroundColorFor?.let { section ->
        ColorPickerDialog(
            title = "Section color",
            initialColorArgb = section.backgroundArgb ?: libraryStyle.sectionBackgroundArgb ?: 0xFF1A2633,
            onDismiss = { sectionBackgroundColorFor = null },
            onSelect = { color ->
                updateSections(sections.map { if (it.id == section.id) it.copy(backgroundArgb = color) else it })
                sectionBackgroundColorFor = null
            },
            allowReset = true,
            onReset = {
                updateSections(sections.map { if (it.id == section.id) it.copy(backgroundArgb = null) else it })
                sectionBackgroundColorFor = null
            }
        )
    }

    sectionTextColorFor?.let { section ->
        ColorPickerDialog(
            title = "Section text color",
            initialColorArgb = section.textArgb ?: libraryStyle.sectionTextArgb ?: libraryStyle.textArgb ?: 0xFFFFFFFF,
            onDismiss = { sectionTextColorFor = null },
            onSelect = { color ->
                updateSections(sections.map { if (it.id == section.id) it.copy(textArgb = color) else it })
                sectionTextColorFor = null
            },
            allowReset = true,
            onReset = {
                updateSections(sections.map { if (it.id == section.id) it.copy(textArgb = null) else it })
                sectionTextColorFor = null
            }
        )
    }

    fileActionFor?.let { file ->
        PdfLibraryOptionsDialog(title = file.name, onDismiss = { fileActionFor = null }) {
            val existing = sections.filter { section -> section.entries.any { it.path == file.file.path } }
            Text(if (existing.isEmpty()) "Not in any PDF section" else "In: ${existing.joinToString { it.title }}", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PdfLibraryOption("Add to section") { chooseSectionForFile = file; fileActionFor = null }
            PdfLibraryOption("Create section") { createSectionFile = file; createSectionMode = "file"; fileActionFor = null }
            PdfLibraryOption("Share") { sharePdfFiles(context, listOf(file)); fileActionFor = null }
        }
    }

    chooseSectionForFile?.let { file ->
        PdfLibraryOptionsDialog(title = "Add to section", onDismiss = { chooseSectionForFile = null }) {
            sections.forEach { section ->
                PdfLibraryOption(section.title) { addPdfToSection(section.id, file); chooseSectionForFile = null }
            }
            PdfLibraryOption("New section") { createSectionFile = file; createSectionMode = "file"; chooseSectionForFile = null }
        }
    }

    sectionActionFor?.let { section ->
        PdfLibraryOptionsDialog(title = section.title, onDismiss = { sectionActionFor = null }) {
            PdfLibraryOption("Rename section") { renameSectionFor = section; sectionActionFor = null }
            PdfLibraryOption("Change section color") { sectionBackgroundColorFor = section; sectionActionFor = null }
            PdfLibraryOption("Change text color") { sectionTextColorFor = section; sectionActionFor = null }
            PdfLibraryOption("Add PDFs") {
                selectingForSectionId = section.id
                selectedPdfPaths = emptySet()
                activeTab = "files"
                sectionActionFor = null
            }
            PdfLibraryOption("Remove section", color = Color(0xFFFF7A7A)) {
                updateSections(sections.filterNot { it.id == section.id })
                sectionActionFor = null
            }
        }
    }

    entryActionFor?.let { (sectionId, entry) ->
        val section = sections.firstOrNull { it.id == sectionId }
        PdfLibraryOptionsDialog(title = entry.displayName, onDismiss = { entryActionFor = null }) {
            Text("In: ${section?.title ?: "PDF Section"}", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PdfLibraryOption("Rename") { renameEntryFor = sectionId to entry; entryActionFor = null }
            PdfLibraryOption("Move another section") { moveEntryRequest = sectionId to entry; entryActionFor = null }
            PdfLibraryOption("Share") {
                sharePdfFiles(context, listOf(DeviceFile(File(entry.path), entry.displayName, "pdf")))
                entryActionFor = null
            }
            PdfLibraryOption("Remove from section", color = Color(0xFFFF7A7A)) {
                updateSections(sections.map { current ->
                    if (current.id == sectionId) current.copy(entries = current.entries.filterNot { it.path == entry.path }) else current
                })
                entryActionFor = null
            }
        }
    }

    moveEntryRequest?.let { (fromSectionId, entry) ->
        PdfLibraryOptionsDialog(title = "Move to section", onDismiss = { moveEntryRequest = null }) {
            sections.filterNot { it.id == fromSectionId }.forEach { section ->
                PdfLibraryOption(section.title) {
                    updateSections(sections.map { current ->
                        when (current.id) {
                            fromSectionId -> current.copy(entries = current.entries.filterNot { it.path == entry.path })
                            section.id -> current.copy(entries = current.entries + entry)
                            else -> current
                        }
                    })
                    moveEntryRequest = null
                }
            }
            PdfLibraryOption("New section") { createSectionMove = fromSectionId to entry; createSectionMode = "move"; moveEntryRequest = null }
        }
    }

    if (createSectionMode != null) {
        StyledInputDialog("PDF section name", "PDF Section ${sections.size + 1}", { createSectionMode = null }) { title ->
            val newSection = PdfLibrarySection(title = title.ifBlank { "PDF Section ${sections.size + 1}" })
            when (createSectionMode) {
                "select" -> {
                    updateSections(sections + newSection)
                    selectingForSectionId = newSection.id
                    selectedPdfPaths = emptySet()
                    activeTab = "files"
                }
                "file" -> {
                    val file = createSectionFile
                    updateSections(sections + newSection.copy(entries = file?.let { listOf(PdfLibraryEntry(it.file.path, it.name)) }.orEmpty()))
                    createSectionFile = null
                }
                "move" -> {
                    createSectionMove?.let { (fromSectionId, entry) ->
                        updateSections(
                            sections.map { section ->
                                if (section.id == fromSectionId) section.copy(entries = section.entries.filterNot { it.path == entry.path }) else section
                            } + newSection.copy(entries = listOf(entry))
                        )
                    }
                    createSectionMove = null
                }
            }
            createSectionMode = null
        }
    }

    renameSectionFor?.let { section ->
        StyledInputDialog("Rename section", section.title, { renameSectionFor = null }) { title ->
            updateSections(sections.map { if (it.id == section.id) it.copy(title = title.ifBlank { it.title }) else it })
            renameSectionFor = null
        }
    }

    renameEntryFor?.let { (sectionId, entry) ->
        StyledInputDialog("Rename PDF", entry.displayName, { renameEntryFor = null }) { title ->
            updateSections(sections.map { section ->
                if (section.id == sectionId) {
                    section.copy(entries = section.entries.map {
                        if (it.path == entry.path) it.copy(displayName = title.ifBlank { entry.sourceName }) else it
                    })
                } else section
            })
            renameEntryFor = null
        }
    }
}

@Composable
private fun PdfLibraryOptionsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput("pdf-panel-scrim") { detectTapGestures(onTap = { onDismiss() }) }
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp),
                enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 3 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 3 }
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 320.dp)
                        .heightIn(max = 460.dp)
                        .pointerInput("pdf-panel-block") { detectTapGestures(onTap = {}) },
                    shape = RoundedCornerShape(16.dp),
                    color = GlassDark1,
                    contentColor = SoftNeutral,
                    shadowElevation = 12.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = SoftNeutral)
                        Spacer(Modifier.height(8.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfLibraryOption(label: String, color: Color = SoftNeutral, onClick: () -> Unit) {
    var pressed by remember(label) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pdfLibraryOptionPress")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PdfLibrarySettingsDialog(
    style: PdfLibraryStyle,
    onStyleChange: (PdfLibraryStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var picker by remember { mutableStateOf<String?>(null) }
    val dialogText = Color.White
    val dialogMuted = Color.White.copy(alpha = 0.68f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = GlassDark1,
            contentColor = dialogText
        ) {
            Column(modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text("Files settings", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Only PDF Files home changes", color = dialogMuted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Theme", fontWeight = FontWeight.SemiBold)
                        Text("Default or white Files home", color = dialogMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onStyleChange(PdfLibraryStyle()) }) {
                        Text("Default", color = AccentCyan)
                    }
                    TextButton(onClick = {
                        onStyleChange(
                            PdfLibraryStyle(
                                backgroundArgb = 0xFFF4F4F8,
                                textArgb = 0xFF1A1A1A,
                                sectionBackgroundArgb = 0xFFFFFFFF,
                                sectionTextArgb = 0xFF1A1A1A
                            )
                        )
                    }) { Text("White", color = AccentCyan) }
                }

                Spacer(Modifier.height(14.dp))
                PdfLibraryColorRow(
                    label = "Background color",
                    color = Color(style.backgroundArgb ?: 0xFF101822),
                    onDefault = { onStyleChange(style.copy(backgroundArgb = null)) },
                    onPick = { picker = "background" }
                )
                Spacer(Modifier.height(12.dp))
                PdfLibraryColorRow(
                    label = "Text color",
                    color = Color(style.textArgb ?: 0xFFFFFFFF),
                    onDefault = { onStyleChange(style.copy(textArgb = null)) },
                    onPick = { picker = "text" }
                )
                Spacer(Modifier.height(16.dp))
                Text("PDF section defaults", color = dialogMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                PdfLibraryColorRow(
                    label = "Section color",
                    color = Color(style.sectionBackgroundArgb ?: 0xFF1A2633),
                    onDefault = { onStyleChange(style.copy(sectionBackgroundArgb = null)) },
                    onPick = { picker = "section_background" }
                )
                Spacer(Modifier.height(12.dp))
                PdfLibraryColorRow(
                    label = "Section text color",
                    color = Color(style.sectionTextArgb ?: style.textArgb ?: 0xFFFFFFFF),
                    onDefault = { onStyleChange(style.copy(sectionTextArgb = null)) },
                    onPick = { picker = "section_text" }
                )
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) {
                    Text("Done", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    val pickerConfig = when (picker) {
        "background" -> Triple("Background color", style.backgroundArgb ?: 0xFF101822) { color: Long ->
            onStyleChange(style.copy(backgroundArgb = color))
        }
        "text" -> Triple("Text color", style.textArgb ?: 0xFFFFFFFF) { color: Long ->
            onStyleChange(style.copy(textArgb = color))
        }
        "section_background" -> Triple("Section color", style.sectionBackgroundArgb ?: 0xFF1A2633) { color: Long ->
            onStyleChange(style.copy(sectionBackgroundArgb = color))
        }
        "section_text" -> Triple("Section text color", style.sectionTextArgb ?: style.textArgb ?: 0xFFFFFFFF) { color: Long ->
            onStyleChange(style.copy(sectionTextArgb = color))
        }
        else -> null
    }
    pickerConfig?.let { (title, initialColor, applyColor) ->
        ColorPickerDialog(
            title = title,
            initialColorArgb = initialColor,
            onDismiss = { picker = null },
            onSelect = { color -> applyColor(color); picker = null },
            allowReset = false,
            onReset = {}
        )
    }
}

@Composable
private fun PdfLibraryColorRow(
    label: String,
    color: Color,
    onDefault: () -> Unit,
    onPick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        TextButton(onClick = onDefault, modifier = Modifier.width(80.dp)) {
            Text("Default", color = AccentCyan, fontSize = 12.sp)
        }
        ColorCircle(color = color, onClick = onPick)
    }
}

@Composable
private fun PdfDeviceFileList(
    files: List<DeviceFile>,
    selectedPaths: Set<String>,
    selectionEnabled: Boolean,
    onSelectChange: (DeviceFile, Boolean) -> Unit,
    onOpen: (DeviceFile) -> Unit,
    onDoubleTap: (DeviceFile) -> Unit,
    onLongPress: (DeviceFile) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (files.isEmpty()) {
            item {
                Text("No files found", color = Color.LightGray, modifier = Modifier.padding(top = 20.dp))
            }
        } else {
            items(files, key = { it.file.path }) { file ->
                var pressed by remember(file.file.path, selectionEnabled) { mutableStateOf(false) }
                val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "deviceFilePress")
                val itemBackground by animateColorAsState(
                    targetValue = when {
                        file.file.path in selectedPaths -> Color.White.copy(alpha = 0.16f)
                        pressed -> Color.White.copy(alpha = 0.07f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(140),
                    label = "deviceFileSelection"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(14.dp))
                        .background(itemBackground)
                        .pointerInput(file.file.path, selectionEnabled) {
                            detectTapGestures(
                                onPress = {
                                    pressed = true
                                    tryAwaitRelease()
                                    pressed = false
                                },
                                onTap = {
                                    if (selectionEnabled) onSelectChange(file, file.file.path !in selectedPaths) else onOpen(file)
                                },
                                onDoubleTap = { if (!selectionEnabled) onDoubleTap(file) },
                                onLongPress = { onLongPress(file) }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionEnabled) {
                        Checkbox(checked = file.file.path in selectedPaths, onCheckedChange = { onSelectChange(file, it) })
                    } else if (file.extension == "pdf") {
                        PdfThumbnail(file = file, modifier = Modifier.size(width = 34.dp, height = 40.dp))
                    } else {
                        Icon(Icons.Default.InsertDriveFile, null, tint = Color.LightGray)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${file.extension.uppercase()}  •  ${formatDeviceFileTime(file.file)}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceFileBrowserDialog(
    onDismiss: () -> Unit,
    onPdfClick: (DeviceFile) -> Unit
) {
    val context = LocalContext.current
    var hasAllFilesAccess by remember { mutableStateOf(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()) }
    var files by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showDocuments by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            hasAllFilesAccess = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()
            delay(750)
        }
    }
    LaunchedEffect(hasAllFilesAccess) {
        if (hasAllFilesAccess) {
            isLoading = true
            files = withContext(Dispatchers.IO) { findDeviceFiles() }
            isLoading = false
        }
    }

    fun requestAllFilesAccess() {
        val appSettingsIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(appSettingsIntent) }.getOrElse {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = GlassDark1,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Files", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasAllFilesAccess) "Phone storage access is on" else "Allow all-files access to browse phone PDFs",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                if (!hasAllFilesAccess) {
                    Button(onClick = ::requestAllFilesAccess, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow all files access")
                    }
                } else {
                    Row {
                        MediaTab("PDFs", !showDocuments) { showDocuments = false }
                        Spacer(Modifier.width(10.dp))
                        MediaTab("Files", showDocuments) { showDocuments = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentCyan)
                        }
                    } else {
                        val visibleFiles = if (showDocuments) files else files.filter { it.extension == "pdf" }
                        Column(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            if (visibleFiles.isEmpty()) {
                                Text("No files found", color = Color.LightGray)
                            } else {
                                visibleFiles.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(item.file.path) {
                                                detectTapGestures(onTap = {
                                                    if (item.extension == "pdf") onPdfClick(item)
                                                })
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.InsertDriveFile, null, tint = if (item.extension == "pdf") AccentCyan else Color.LightGray)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${if (item.extension == "pdf") "Tap to open" else item.extension.uppercase()}  •  ${formatDeviceFileTime(item.file)}",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done", color = AccentCyan) }
            }
        }
    }
}

@Composable
private fun ColorCircle(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .pointerInput(color) { detectTapGestures(onTap = { onClick() }) }
    )
}

@Composable
fun ProductivityFilesSummary(): Pair<Int, String?> {
    val context = LocalContext.current
    var files by remember { mutableStateOf(cachedDeviceFiles() ?: loadPersistedDeviceFiles(context)) }
    LaunchedEffect(Unit) {
        if (cachedDeviceFiles() == null) {
            val fresh = withContext(Dispatchers.IO) { findDeviceFiles() }
            files = fresh
        }
    }
    val pdfFiles = files.filter { it.extension == "pdf" }
    return pdfFiles.size to pdfFiles.maxByOrNull { it.file.lastModified() }?.name
}
