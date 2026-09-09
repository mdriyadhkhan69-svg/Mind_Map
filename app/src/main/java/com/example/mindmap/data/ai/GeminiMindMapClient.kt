package com.example.mindmap.data.ai

import android.graphics.Bitmap
import com.example.mindmap.BuildConfig
import com.example.mindmap.data.NodeEntity
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** The one Gemini entry point for both creation and safe in-place editing. */
object GeminiMindMapClient {
    private const val SYSTEM_INSTRUCTION = """
You are an integrated mind-map assistant. Understand Bangla, English, and mixed Banglish. Reply in the user's language.
Return ONLY valid JSON:
{"mode":"create|edit|clarify","message":"short status or question","nodes":[{"id":"new id","parentId":"new parent id or null","text":"label","type":"root|branch|leaf"}],"actions":[{"type":"createChild|updateNode|deleteNode|connectNodes|moveNode","targetId":12,"parentId":12,"secondaryId":13,"text":"...","color":"#FF0000","textColor":"#FFFFFF","widthScale":2.5,"heightScale":1.5,"textSizeSp":18,"textWeight":700,"x":100,"y":200}]}
Rules:
- Use mode=create and nodes only when the user asks to create a new map/topic. Make one root; all parentId references must be IDs in nodes.
- Use mode=edit and actions only for an existing map. targetId, parentId, and secondaryId MUST be numeric IDs from CURRENT MAP.
- createChild uses parentId and text. updateNode changes only supplied fields. deleteNode requires targetId. connectNodes requires targetId and secondaryId. moveNode requires targetId/x/y.
- Never delete or recreate unrelated nodes. Never use an ID you cannot see. If the target is ambiguous, use mode=clarify and no actions.
- For "add points", return one createChild action per child. For resize/readability, use a generous widthScale/heightScale, never tiny text.
- Colors must be #RRGGBB or #AARRGGBB. Keep styling professional, not neon.
- Preserve Bangla labels for Bangla input, English labels for English input, and natural mixed text for mixed input.
"""

    suspend fun generateMindMap(
        userPrompt: String,
        images: List<Bitmap> = emptyList(),
        existingNodes: List<NodeEntity> = emptyList()
    ): AiMindMapResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return AiMindMapResult.Error("Gemini API key not configured. Add GEMINI_API_KEY to local.properties.")
        return try {
            withTimeout(45_000L) {
                val model = GenerativeModel(
                    modelName = "gemini-3.6-flash", apiKey = apiKey,
                    generationConfig = generationConfig { temperature = 0.35f; responseMimeType = "application/json" }
                )
                val mapContext = existingNodes.take(180).joinToString("\n") { node ->
                    "id=${node.id}; parent=${node.parentId ?: "root"}; text=${node.label.take(180)}; " +
                        "pos=${node.x.toInt()},${node.y.toInt()}; size=${node.widthScale},${node.heightScale}; " +
                        "font=${node.textSizeSp}/${node.textWeight}; bg=${node.colorArgb}; textColor=${node.textColorArgb}"
                }.ifBlank { "(empty map)" }
                val response = model.generateContent(content {
                    images.take(4).forEach { image(it) }
                    text("$SYSTEM_INSTRUCTION\n\nCURRENT MAP (compact, authoritative):\n$mapContext\n\nUSER REQUEST: $userPrompt")
                })
                response.text?.takeIf { it.isNotBlank() }?.let(::parseResponse)
                    ?: AiMindMapResult.Error("Gemini returned an empty response.")
            }
        } catch (_: TimeoutCancellationException) {
            AiMindMapResult.Error("Request timed out. Please try again.")
        } catch (e: Exception) {
            AiMindMapResult.Error(e.message ?: "Network or API error occurred.")
        }
    }

    private fun parseResponse(rawText: String): AiMindMapResult {
        return try {
            val start = rawText.indexOf('{'); val end = rawText.lastIndexOf('}')
            if (start < 0 || end < start) return AiMindMapResult.Error("Gemini response was not valid JSON.")
            val root = JSONObject(rawText.substring(start, end + 1))
            val mode = root.optString("mode", "create").lowercase()
            val message = root.optString("message").trim().takeIf { it.isNotBlank() }
            val allNodes = parseNodes(root.optJSONArray("nodes") ?: JSONArray())
            val ids = allNodes.map { it.id }.toSet()
            val sanitized = allNodes.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it }
            val actions = parseActions(root.optJSONArray("actions") ?: JSONArray())
            if (mode == "create" && sanitized.isEmpty()) return AiMindMapResult.Error("Gemini returned no valid nodes.")
            AiMindMapResult.Success(
                rootNodes = sanitized.filter { it.parentId == null }, childrenOf = sanitized.groupBy { it.parentId },
                actions = actions, message = message, needsClarification = mode == "clarify"
            )
        } catch (e: Exception) { AiMindMapResult.Error("Failed to parse Gemini response: ${e.message}") }
    }

    private fun parseNodes(array: JSONArray): List<AiNode> {
        val seen = mutableSetOf<String>()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim(); val text = obj.optString("text").trim()
                if (id.isEmpty() || text.isEmpty() || !seen.add(id)) continue
                val parent = if (obj.isNull("parentId")) null else obj.optString("parentId").trim().takeIf { it.isNotEmpty() && it != "null" }
                add(AiNode(id, parent, text.take(360), obj.optString("type").takeIf { it.isNotBlank() }))
            }
        }
    }

    private fun parseActions(array: JSONArray): List<AiMindMapAction> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val type = obj.optString("type").trim()
            if (type !in setOf("createChild", "updateNode", "deleteNode", "connectNodes", "moveNode")) continue
            fun id(name: String): Long? = obj.optLong(name, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE || it <= 0L }
            add(AiMindMapAction(
                type = type, targetId = id("targetId"), parentId = id("parentId"), secondaryId = id("secondaryId"),
                text = obj.optString("text").trim().takeIf { it.isNotBlank() }?.take(360),
                colorArgb = parseColor(obj.opt("color")), textColorArgb = parseColor(obj.opt("textColor")),
                widthScale = obj.optDouble("widthScale", Double.NaN).takeIf { it.isFinite() }?.toFloat()?.coerceIn(0.45f, 12f),
                heightScale = obj.optDouble("heightScale", Double.NaN).takeIf { it.isFinite() }?.toFloat()?.coerceIn(0.45f, 12f),
                textSizeSp = obj.optDouble("textSizeSp", Double.NaN).takeIf { it.isFinite() }?.toFloat()?.coerceIn(10f, 40f),
                textWeight = obj.optInt("textWeight", -1).takeIf { it in 100..1200 },
                x = obj.optDouble("x", Double.NaN).takeIf { it.isFinite() }?.toFloat()?.coerceIn(-100_000f, 100_000f),
                y = obj.optDouble("y", Double.NaN).takeIf { it.isFinite() }?.toFloat()?.coerceIn(-100_000f, 100_000f)
            ))
        }
    }

    private fun parseColor(value: Any?): Long? = when (value) {
        is Number -> value.toLong().takeIf { it in 0..0xFFFF_FFFFL }
        is String -> value.removePrefix("#").toLongOrNull(16)?.let { if (value.length == 7) it or 0xFF00_0000L else it }.takeIf { it in 0..0xFFFF_FFFFL }
        else -> null
    }
}
