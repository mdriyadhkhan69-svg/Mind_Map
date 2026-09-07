package com.example.mindmap.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.ai.AiMindMapResult
import com.example.mindmap.data.ai.AiNode
import com.example.mindmap.data.ai.GeminiMindMapClient
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val AiGlow1 = Color(0xFF64FFDA)
private val AiGlow2 = Color(0xFFBB86FC)

// Glossy circular AI trigger, styled to sit next to the existing GlassFab.
@Composable
fun AiSparkleFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "aiFabScale")
    Box(
        modifier = modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AiGlow1.copy(alpha = 0.95f), AiGlow2.copy(alpha = 0.95f))))
            .border(1.2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Mind Map", tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

// Simple non-overlapping tree layout: children stack to the right, each
// sibling gets vertical room proportional to its own subtree, and a parent
// is centered against its children's vertical span.
private fun buildAiLayout(
    roots: List<AiNode>,
    childrenOf: Map<String?, List<AiNode>>,
    startX: Float = 220f,
    startY: Float = 260f
): Map<String, Offset> {
    val positions = mutableMapOf<String, Offset>()
    val xSpacing = 360f
    val unitHeight = 150f

    fun place(node: AiNode, x: Float, yStart: Float, visiting: Set<String>): Float {
        if (node.id in visiting) {
            positions[node.id] = Offset(x, yStart)
            return yStart + unitHeight
        }
        val nextVisiting = visiting + node.id
        val children = childrenOf[node.id].orEmpty()
        if (children.isEmpty()) {
            positions[node.id] = Offset(x, yStart)
            return yStart + unitHeight
        }
        var cursor = yStart
        val firstChildStart = cursor
        children.forEach { child -> cursor = place(child, x + xSpacing, cursor, nextVisiting) }
        positions[node.id] = Offset(x, (firstChildStart + (cursor - unitHeight)) / 2f)
        return cursor
    }

    var yCursor = startY
    roots.forEach { root -> yCursor = place(root, startX, yCursor, emptySet()) + 90f }
    return positions
}

private data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val image: Bitmap? = null,
    val isError: Boolean = false
)

private suspend fun decodeSampledBitmap(
    context: android.content.Context,
    uri: Uri,
    maxSide: Int = 1024
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return@use null
            val largestSide = maxOf(original.width, original.height).coerceAtLeast(1)
            if (largestSide <= maxSide) {
                original
            } else {
                val scale = maxSide.toFloat() / largestSide
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
        }
    }.getOrNull()
}

@Composable
fun AiMindMapDialog(
    viewModel: MindMapViewModel,
    sectionId: Long?,
    onDismiss: () -> Unit
) {
    // All of this lives only while the dialog is composed — closing the
    // dialog (onDismiss / showAiMindMapDialog = false in the caller) drops
    // this composable from composition, so the whole conversation and any
    // pending image are gone the next time it's opened. No manual "clear" needed.
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch { pendingImage = decodeSampledBitmap(context, uri) }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        val trimmed = inputText.trim()
        val imageForRequest = pendingImage
        if (trimmed.isEmpty() && imageForRequest == null) return
        if (isGenerating) return
        val activeSectionId = sectionId
        if (activeSectionId == null) {
            messages.add(ChatMessage(isUser = false, text = "কোনো active section পাওয়া যায়নি। আগে একটা section তৈরি করো।", isError = true))
            return
        }
        val userMessageText = trimmed.ifBlank { "Create a mind map from this image" }
        messages.add(ChatMessage(isUser = true, text = userMessageText, image = imageForRequest))
        inputText = ""
        pendingImage = null
        isGenerating = true

        scope.launch {
            when (val result = GeminiMindMapClient.generateMindMap(userMessageText, imageForRequest)) {
                is AiMindMapResult.Error -> {
                    isGenerating = false
                    messages.add(ChatMessage(isUser = false, text = result.message, isError = true))
                }
                is AiMindMapResult.Success -> {
                    val positions = buildAiLayout(result.rootNodes, result.childrenOf)
                    val createdIds = mutableSetOf<String>()

                    fun insertChildren(parentEntity: NodeEntity, aiParentId: String, visiting: Set<String>) {
                        if (aiParentId in visiting) return
                        result.childrenOf[aiParentId].orEmpty().forEach { child ->
                            if (child.id in createdIds) return@forEach
                            createdIds += child.id
                            val pos = positions[child.id] ?: Offset(parentEntity.x + 360f, parentEntity.y)
                            viewModel.addAiChildNode(parentEntity, child.text, pos.x, pos.y) { createdChild ->
                                insertChildren(createdChild, child.id, visiting + aiParentId)
                            }
                        }
                    }

                    result.rootNodes.forEachIndexed { index, root ->
                        if (root.id in createdIds) return@forEachIndexed
                        createdIds += root.id
                        val pos = positions[root.id] ?: Offset(220f, 300f + index * 400f)
                        viewModel.addAiRootNode(activeSectionId, root.text, pos.x, pos.y) { createdRoot ->
                            insertChildren(createdRoot, root.id, emptySet())
                        }
                    }

                    val totalPlanned = result.childrenOf.values.sumOf { it.size }
                    isGenerating = false
                    messages.add(ChatMessage(isUser = false, text = "মাইন্ড ম্যাপ তৈরি হয়ে গেছে ✅ ($totalPlanned boxes)"))
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f).padding(horizontal = 16.dp, vertical = 24.dp)) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF171826),
                contentColor = Color.White,
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AiGlow1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Mind Map", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(enabled = !isGenerating, onClick = onDismiss) { Text("Close", color = Color.LightGray) }
                    }

                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "ছবি দিতে পারো অথবা বাংলা/ইংরেজিতে লিখে বলো, যেমন —\n\"বাংলাদেশের মুক্তিযুদ্ধ নিয়ে একটা মাইন্ড ম্যাপ বানাও\"",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { message -> ChatBubble(message) }
                            if (isGenerating) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                                        CircularProgressIndicator(color = AiGlow1, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("তৈরি হচ্ছে...", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1F30))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        pendingImage?.let { bmp ->
                            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .pointerInput("remove-pending-image") {
                                            detectTapGestures(onTap = { pendingImage = null })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            IconButton(enabled = !isGenerating, onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach image", tint = AiGlow2)
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                enabled = !isGenerating,
                                placeholder = { Text("বাংলায় বা English-এ লিখো...") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = AiGlow1, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    cursorColor = AiGlow1
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                enabled = !isGenerating && (inputText.isNotBlank() || pendingImage != null),
                                onClick = { submit() }
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (!isGenerating && (inputText.isNotBlank() || pendingImage != null)) AiGlow1 else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = when {
        message.isError -> Color(0xFF3A1414)
        message.isUser -> AiGlow1.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .padding(10.dp)
        ) {
            message.image?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .padding(bottom = 6.dp)
                )
            }
            Text(
                message.text,
                color = if (message.isError) Color(0xFFFF8A80) else Color.White,
                fontSize = 13.5.sp,
                overflow = TextOverflow.Clip
            )
        }
    }
}