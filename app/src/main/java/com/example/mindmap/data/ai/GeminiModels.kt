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

sealed class AiMindMapResult {
    data class Success(
        val rootNodes: List<AiNode>,
        val childrenOf: Map<String?, List<AiNode>>
    ) : AiMindMapResult()

    data class Error(val message: String) : AiMindMapResult()
}