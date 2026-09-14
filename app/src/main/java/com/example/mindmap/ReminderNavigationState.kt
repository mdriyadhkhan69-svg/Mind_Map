package com.example.mindmap

import androidx.compose.runtime.mutableStateOf

/** A one-shot deep-link target consumed by MindMapApp/MindMapScreen. */
object ReminderNavigationState {
    val pendingSectionId = mutableStateOf<Long?>(null)
    val pendingNodeId = mutableStateOf<Long?>(null)
}
