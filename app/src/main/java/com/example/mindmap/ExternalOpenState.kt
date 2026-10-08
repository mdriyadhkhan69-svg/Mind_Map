package com.example.mindmap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object ExternalOpenState {
    val pendingPdfUri: MutableState<String?> = mutableStateOf(null)
    val pendingOpenError: MutableState<String?> = mutableStateOf(null)
}