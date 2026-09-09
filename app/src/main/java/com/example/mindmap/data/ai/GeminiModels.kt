package com.example.mindmap.data.ai

// Structured node coming back from Gemini. `type` is carried through for
// future use (styling per node type) but is not required by the current
// NodeEntity model.
data class AiNode(
    val id: String,
    val parentId: String?,
    val text: String,
    val type: String? = null
)

/** A validated instruction for the application's existing NodeEntity/LineEntity model. */
data class AiMindMapAction(
    val type: String,
    val targetId: Long? = null,
    val parentId: Long? = null,
    val secondaryId: Long? = null,
    val text: String? = null,
    val colorArgb: Long? = null,
    val textColorArgb: Long? = null,
    val widthScale: Float? = null,
    val heightScale: Float? = null,
    val textSizeSp: Float? = null,
    val textWeight: Int? = null,
    val x: Float? = null,
    val y: Float? = null
)

sealed class AiMindMapResult {
    data class Success(
        val rootNodes: List<AiNode>,
        val childrenOf: Map<String?, List<AiNode>>,
        val actions: List<AiMindMapAction> = emptyList(),
        val message: String? = null,
        val needsClarification: Boolean = false
    ) : AiMindMapResult()

    data class Error(val message: String) : AiMindMapResult()
}
