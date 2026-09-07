package com.example.mindmap.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.ai.AiMindMapResult
import com.example.mindmap.data.ai.AiNode
import com.example.mindmap.data.ai.GeminiMindMapClient
import com.example.mindmap.ui.viewmodel.MindMapViewModel
import kotlinx.coroutines.launch

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

@Composable
fun AiMindMapDialog(
    viewModel: MindMapViewModel,
    sectionId: Long?,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || isGenerating) return
        val activeSectionId = sectionId
        if (activeSectionId == null) {
            errorMessage = "No active section found. Create a section first."
            return
        }
        isGenerating = true
        errorMessage = null
        scope.launch {
            when (val result = GeminiMindMapClient.generateMindMap(trimmed)) {
                is AiMindMapResult.Error -> {
                    isGenerating = false
                    errorMessage = result.message
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
                    isGenerating = false
                    onDismiss()
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF171826),
                contentColor = Color.White,
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AiGlow1, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Mind Map", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Describe what you want, e.g. \"Create a mind map about the Bangladesh Liberation War\"",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.5.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        enabled = !isGenerating,
                        placeholder = { Text("Type your request...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = AiGlow1, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = AiGlow1
                        )
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFFFF6E6E), fontSize = 12.5.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = AiGlow1, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Generating...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = !isGenerating, onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                        Spacer(Modifier.width(6.dp))
                        TextButton(enabled = !isGenerating && prompt.isNotBlank(), onClick = { submit() }) {
                            Text("Generate", color = AiGlow1, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}