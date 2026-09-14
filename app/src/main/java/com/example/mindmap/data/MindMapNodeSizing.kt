package com.example.mindmap.data

import kotlin.math.ceil
import kotlin.math.max

/** Content-aware dimensions expressed as the existing persisted scale fields. */
object MindMapNodeSizing {
    // A scale below 1 is valid: short labels should not be forced into the
    // old 70dp/86dp minimum box. The renderer still keeps text padding.
    const val MIN_SCALE = 0.25f
    // Deliberately generous: it prevents broken/infinite dimensions while allowing
    // long notes and presentation-sized boxes.
    const val MAX_SCALE = 12f

    data class Scales(val width: Float, val height: Float)

    fun forLabel(label: String, textSizeSp: Float = 16f, isRoot: Boolean = false): Scales {
        val normalized = label.trim().ifBlank { " " }
        val baseWidth = if (isRoot) 86f else 70f
        val baseHeight = if (isRoot) 42f else 32f
        val horizontalPadding = if (isRoot) 24f else 16f
        val verticalPadding = if (isRoot) 16f else 10f
        val preferredWidth = (normalized.length * textSizeSp * 0.50f + horizontalPadding)
            .coerceAtMost(440f)
        val width = (preferredWidth / baseWidth).coerceIn(MIN_SCALE, MAX_SCALE)
        val charsPerLine = max(8, (preferredWidth / (textSizeSp * 0.55f)).toInt())
        val lineCount = ceil(normalized.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
        // textSizeSp is already part of preferredHeight. Applying a second
        // font factor here made larger text inflate the box quadratically.
        val preferredHeight = lineCount * textSizeSp * 1.28f + verticalPadding
        val height = (preferredHeight / baseHeight).coerceIn(MIN_SCALE, MAX_SCALE)
        return Scales(width, height)
    }
}
