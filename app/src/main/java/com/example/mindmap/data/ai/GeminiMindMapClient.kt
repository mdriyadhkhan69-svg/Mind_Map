package com.example.mindmap.data.ai

import com.example.mindmap.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

// Single entry point for both "create a new mind map" and (future) "edit this
// existing branch" requests — both should call generateMindMap() with a
// prompt that already embeds the relevant existing-node context, so no
// second AI architecture is needed later.
object GeminiMindMapClient {

    private const val SYSTEM_INSTRUCTION = """
You are a mind map generator. Respond with ONLY valid JSON — no markdown, no code fences, no explanation, no extra text.
JSON schema (exactly this shape):
{
  "nodes": [
    { "id": "string unique id", "parentId": "string id of parent, or null for the single root", "text": "short node label (max 6 words)", "type": "root|branch|leaf" }
  ]
}
Rules:
- Exactly one node has parentId = null (the central topic/root).
- Every other node's parentId MUST match an existing node's id in the same list.
- Keep "text" short (max 6 words) so it fits a small box.
- Build a well-organized hierarchy (2-3 levels) when the topic allows it.
- Output nothing outside the single JSON object.
"""

    suspend fun generateMindMap(userPrompt: String): AiMindMapResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return AiMindMapResult.Error("Gemini API key not configured. Add GEMINI_API_KEY to local.properties.")
        }
        return try {
            withTimeout(25_000L) {
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.6f
                        responseMimeType = "application/json"
                    }
                )
                val response = model.generateContent(
                    content { text("$SYSTEM_INSTRUCTION\n\nUser request: $userPrompt") }
                )
                val rawText = response.text
                if (rawText.isNullOrBlank()) {
                    AiMindMapResult.Error("Gemini returned an empty response.")
                } else {
                    parseResponse(rawText)
                }
            }
        } catch (e: TimeoutCancellationException) {
            AiMindMapResult.Error("Request timed out. Please try again.")
        } catch (e: Exception) {
            AiMindMapResult.Error(e.message ?: "Network or API error occurred.")
        }
    }

    private fun parseResponse(rawText: String): AiMindMapResult {
        return try {
            val jsonStart = rawText.indexOf('{')
            val jsonEnd = rawText.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
                return AiMindMapResult.Error("Gemini response was not valid JSON.")
            }
            val root = JSONObject(rawText.substring(jsonStart, jsonEnd + 1))
            val nodesArray: JSONArray = root.optJSONArray("nodes")
                ?: return AiMindMapResult.Error("Response is missing the 'nodes' array.")
            if (nodesArray.length() == 0) {
                return AiMindMapResult.Error("Gemini returned no nodes.")
            }

            val allNodes = mutableListOf<AiNode>()
            val seenIds = mutableSetOf<String>()
            for (i in 0 until nodesArray.length()) {
                val obj = nodesArray.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val text = obj.optString("text").trim()
                if (id.isEmpty() || text.isEmpty()) continue
                if (!seenIds.add(id)) continue
                val parentId = if (obj.isNull("parentId")) null else obj.optString("parentId").trim()
                    .takeIf { it.isNotEmpty() && it != "null" }
                val type = obj.optString("type").takeIf { it.isNotBlank() }
                allNodes += AiNode(id, parentId, text, type)
            }
            if (allNodes.isEmpty()) {
                return AiMindMapResult.Error("No valid nodes found in Gemini's response.")
            }

            // Any parentId that doesn't resolve to a real node becomes a root
            // instead of being dropped — keeps a malformed response usable.
            val idSet = allNodes.map { it.id }.toSet()
            val sanitized = allNodes.map { n ->
                if (n.parentId != null && n.parentId !in idSet) n.copy(parentId = null) else n
            }
            val roots = sanitized.filter { it.parentId == null }
            if (roots.isEmpty()) {
                return AiMindMapResult.Error("Gemini's response has no root node.")
            }
            AiMindMapResult.Success(roots, sanitized.groupBy { it.parentId })
        } catch (e: Exception) {
            AiMindMapResult.Error("Failed to parse Gemini response: ${e.message}")
        }
    }
}