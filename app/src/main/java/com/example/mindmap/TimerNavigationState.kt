package com.example.mindmap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

// Floating popup double-tap করলে main app খুলে সরাসরি Timer পেজে নিয়ে যাওয়ার জন্য
object TimerNavigationState {
    val requestOpenTimer: MutableState<Boolean> = mutableStateOf(false)
}