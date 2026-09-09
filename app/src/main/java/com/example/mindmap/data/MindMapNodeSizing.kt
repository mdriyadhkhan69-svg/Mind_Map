package com.example.mindmap.data

import kotlin.math.ceil
import kotlin.math.max

/** Content-aware dimensions expressed as the existing persisted scale fields. */
object MindMapNodeSizing {
    const val MIN_SCALE = 0.45f
    // Deliberately generous: it prevents broken/infinite dimensions while allowing
    // long notes and presentation-sized boxes.
    const val MAX_SCALE = 12f

    data class Scales(val width: Float, val height: Float)

    fun forLabel(label: String, textSizeSp: Float = 16f, isRoot: Boolean = false): Scales {
        val normalized = label.trim().ifBlank { " " }
        val baseWidth = if (isRoot) 86f else 70f
        val baseHeight = if (isRoot) 42f else 32f
        val fontFactor = (textSizeSp / 16f).coerceIn(0.8f, 2.2f)
        val preferredWidth = (normalized.length * textSizeSp * 0.52f + 28f)
            .coerceIn(baseWidth, 440f)
        val width = (preferredWidth / baseWidth).coerceIn(1f, MAX_SCALE)
        val charsPerLine = max(8, (preferredWidth / (textSizeSp * 0.55f)).toInt())
        val lineCount = ceil(normalized.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
        val preferredHeight = lineCount * textSizeSp * 1.38f + 18f
        val height = (preferredHeight / baseHeight * fontFactor).coerceIn(1f, MAX_SCALE)
        return Scales(width, height)
    }
}
