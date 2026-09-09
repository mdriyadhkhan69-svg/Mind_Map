package com.example.mindmap.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.ai.AiMindMapResult
import com.example.mindmap.data.ai.AiNode
import com.example.mindmap.data.ai.GeminiMindMapClient
import com.example.mindmap.ui.viewmodel.LineViewModel
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private val AiGlow1 = Color(0xFF69E6C9)
private val AiGlow2 = Color(0xFF9B8CFF)

/** Tap opens the assistant; a drag moves it without triggering a tap. */
@Composable
fun AiSparkleFab(
    onClick: () -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "aiFabScale")
    Box(
        modifier = modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AiGlow1, AiGlow2)))
            .border(1.dp, Color.White.copy(alpha = .6f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { if (!dragging) onClick() }) }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { dragging = true }, onDragEnd = { onDragEnd(); dragging = false }, onDragCancel = { dragging = false }) { change, amount -> change.consume(); onDrag(amount) } },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.AutoAwesome, "AI Mind Map", tint = Color.White, modifier = Modifier.size(26.dp)) }
}

private data class ChatMessage(
    val id: String = UUID.randomUUID().toString(), val isUser: Boolean, val text: String,
    val images: List<Bitmap> = emptyList(), val isError: Boolean = false
)

private suspend fun decodeSampledBitmap(context: Context, uri: Uri, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return@use null
            val largest = maxOf(original.width, original.height).coerceAtLeast(1)
            if (largest <= maxSide) original else {
                val scale = maxSide.toFloat() / largest
                Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1), (original.height * scale).roundToInt().coerceAtLeast(1), true)
            }
        }
    }.getOrNull()
}

private fun buildAiLayout(roots: List<AiNode>, childrenOf: Map<String?, List<AiNode>>): Map<String, androidx.compose.ui.geometry.Offset> {
    val positions = mutableMapOf<String, androidx.compose.ui.geometry.Offset>()
    fun place(node: AiNode, x: Float, y: Float): Float {
        val children = childrenOf[node.id].orEmpty()
        if (children.isEmpty()) { positions[node.id] = androidx.compose.ui.geometry.Offset(x, y); return y + 180f }
        var cursor = y; val top = cursor
        children.forEach { cursor = place(it, x + 440f, cursor) }
        positions[node.id] = androidx.compose.ui.geometry.Offset(x, (top + cursor - 180f) / 2f)
        return cursor
    }
    var y = 260f; roots.forEach { y = place(it, 220f, y) + 110f }; return positions
}

@Composable
fun AiMindMapDialog(
    viewModel: MindMapViewModel, lineViewModel: LineViewModel, sectionId: Long?,
    nodesInSection: List<NodeEntity>, onDismiss: () -> Unit
) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Bitmap>() }
    var isGenerating by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val listState = rememberLazyListState()

    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch { uris.take(4 - attachments.size).forEach { uri -> decodeSampledBitmap(context, uri)?.let { attachments += it } } }
    }
    lateinit var beginListening: () -> Unit
    val speechRecognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isRecording = true }
            override fun onBeginningOfSpeech() { isRecording = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { isRecording = false }
            override fun onError(error: Int) { isRecording = false; voiceError = "Voice recognition could not understand that. Try again." }
            override fun onResults(results: Bundle?) {
                isRecording = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard ->
                    inputText = listOf(inputText.trim(), heard.trim()).filter { it.isNotBlank() }.joinToString(" ")
                } ?: run { voiceError = "No speech was recognized. Try again." }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { inputText = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { speechRecognizer.destroy() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) beginListening() else voiceError = "Microphone permission is needed for voice input." }
    beginListening = {
        voiceError = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceError = "Voice recognition is unavailable on this device."
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Android language detection is used when the installed recognizer supports it.
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayListOf("bn-BD", "en-US"))
            }
            isRecording = true
            speechRecognizer.startListening(intent)
        }
    }

    LaunchedEffect(messages.size, isGenerating) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (isGenerating) 1 else 0) }

    fun submit() {
        val text = inputText.trim(); val images = attachments.toList(); val activeSection = sectionId
        if ((text.isEmpty() && images.isEmpty()) || isGenerating) return
        if (activeSection == null) { messages += ChatMessage(isUser = false, text = "Create or select a section first.", isError = true); return }
        val request = text.ifBlank { "Create a mind map using all attached images" }
        messages += ChatMessage(isUser = true, text = request, images = images)
        inputText = ""; attachments.clear(); isGenerating = true
        scope.launch {
            when (val result = GeminiMindMapClient.generateMindMap(request, images, nodesInSection)) {
                is AiMindMapResult.Error -> messages += ChatMessage(isUser = false, text = result.message, isError = true)
                is AiMindMapResult.Success -> {
                    val current = nodesInSection.associateBy { it.id }
                    var applied = 0
                    result.actions.forEach { action -> when (action.type) {
                        "createChild" -> action.parentId?.let(current::get)?.let { parent -> action.text?.let { label ->
                            viewModel.addAiChildNode(parent, label, parent.x + 440f, parent.y + current.values.count { it.parentId == parent.id } * 170f) {}; applied++ } }
                        "updateNode", "moveNode" -> action.targetId?.let(current::get)?.let { node ->
                            viewModel.applyAiUpdate(node, action.text, action.colorArgb ?: node.colorArgb, action.textColorArgb ?: node.textColorArgb, action.widthScale, action.heightScale, action.textSizeSp, action.textWeight, action.x, action.y); applied++ }
                        "deleteNode" -> action.targetId?.let(current::get)?.let { viewModel.deleteNode(it); applied++ }
                        "connectNodes" -> {
                            val fromId = action.targetId
                            val toId = action.secondaryId
                            if (fromId != null && toId != null && current.containsKey(fromId) && current.containsKey(toId) && fromId != toId) {
                                lineViewModel.addLine(activeSection, fromId, toId)
                                applied++
                            }
                        }
                    } }
                    if (result.rootNodes.isNotEmpty()) {
                        val positions = buildAiLayout(result.rootNodes, result.childrenOf); val created = mutableSetOf<String>()
                        fun children(parent: NodeEntity, id: String) { result.childrenOf[id].orEmpty().forEach { child -> if (created.add(child.id)) {
                            val p = positions[child.id] ?: androidx.compose.ui.geometry.Offset(parent.x + 440f, parent.y)
                            viewModel.addAiChildNode(parent, child.text, p.x, p.y) { children(it, child.id) }; applied++ } } }
                        result.rootNodes.forEachIndexed { index, root -> if (created.add(root.id)) { val p = positions[root.id] ?: androidx.compose.ui.geometry.Offset(220f, 280f + index * 420f); viewModel.addAiRootNode(activeSection, root.text, p.x, p.y) { children(it, root.id) }; applied++ } }
                    }
                    messages += ChatMessage(isUser = false, text = result.message ?: if (result.needsClarification) "Which box do you mean?" else if (applied > 0) "Done — updated $applied item${if (applied == 1) "" else "s"}." else "I could not safely identify a change. Please name the box.")
                }
            }
            isGenerating = false
        }
    }

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF151724), contentColor = Color.White, shadowElevation = 20.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth().fillMaxHeight(.90f).padding(12.dp).animateContentSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AiGlow1); Spacer(Modifier.width(10.dp)); Text("Mind Map Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)); TextButton(enabled = !isGenerating, onClick = onDismiss) { Text("Close", color = Color.LightGray) }
                }
                if (messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.CenterStart) { Text("Ask in বাংলা, English, or mixed language. I can create a map, add branches, resize boxes, change styles, organize the current map, and compare up to four images.", color = Color.White.copy(alpha = .65f), lineHeight = 22.sp) }
                else LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages, key = { it.id }) { ChatBubble(it) }
                    if (isGenerating) item { ThinkingRow() }
                }
                Column(Modifier.fillMaxWidth().background(Color(0xFF1D2030)).padding(12.dp)) {
                    AnimatedVisibility(attachments.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { attachments.forEachIndexed { index, bmp -> AttachmentPreview(bmp) { attachments.removeAt(index) } } } }
                    voiceError?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp)) }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(enabled = !isGenerating && attachments.size < 4, onClick = { multiImagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.clip(CircleShape).background(Color.White.copy(.08f))) { Icon(Icons.Default.Add, "Add images", tint = AiGlow2) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = inputText, onValueChange = { inputText = it }, enabled = !isGenerating, placeholder = { Text("Ask anything about this map…") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }), modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = 140.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AiGlow1, unfocusedBorderColor = Color.White.copy(.22f), cursorColor = AiGlow1))
                        Spacer(Modifier.width(8.dp))
                        IconButton(enabled = !isGenerating, onClick = { if (isRecording) { speechRecognizer.stopListening(); isRecording = false } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.clip(CircleShape).background(if (isRecording) Color(0xFFB3261E) else Color.White.copy(.08f))) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, if (isRecording) "Stop recording" else "Voice input", tint = Color.White) }
                        val enabled = !isGenerating && (inputText.isNotBlank() || attachments.isNotEmpty()); val sendScale by animateFloatAsState(if (enabled) 1f else .84f, label = "send")
                        IconButton(enabled = enabled, onClick = { submit() }, modifier = Modifier.graphicsLayer { scaleX = sendScale; scaleY = sendScale }.clip(CircleShape).background(if (enabled) AiGlow1 else Color.White.copy(.08f))) { Icon(Icons.Default.Send, "Send", tint = if (enabled) Color(0xFF10211E) else Color.White.copy(.35f)) }
                    }
                }
            }
        }
    }
}

@Composable private fun AttachmentPreview(bitmap: Bitmap, onRemove: () -> Unit) = Box(Modifier.size(64.dp)) { Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))); IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color.Black.copy(.7f))) { Icon(Icons.Default.Close, "Remove image", tint = Color.White, modifier = Modifier.size(14.dp)) } }

@Composable private fun ThinkingRow() { val transition = rememberInfiniteTransition(label = "thinking"); val alpha by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "thinkingAlpha"); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { this.alpha = alpha }) { Icon(Icons.Default.AutoAwesome, null, tint = AiGlow1, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Thinking…", color = Color.White.copy(.7f), fontSize = 13.sp) } }

@Composable private fun ChatBubble(message: ChatMessage) { val alignment = if (message.isUser) Alignment.End else Alignment.Start; val color = when { message.isError -> Color(0xFF4A1D22); message.isUser -> AiGlow1.copy(.18f); else -> Color.White.copy(.07f) }; Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) { Column(Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(18.dp)).background(color).padding(12.dp)) { if (message.images.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { message.images.forEach { Image(it.asImageBitmap(), null, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) } }; if (message.images.isNotEmpty()) Spacer(Modifier.height(8.dp)); Text(message.text, color = if (message.isError) Color(0xFFFFB4AB) else Color.White, fontSize = 14.sp, lineHeight = 20.sp, overflow = TextOverflow.Clip) } } }
