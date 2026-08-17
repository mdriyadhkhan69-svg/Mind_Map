package com.example.mindmap.ui.screens

import androidx.compose.runtime.mutableStateOf

// Countdown/Stopwatch page-e thakle "quick", Study Timer page-e thakle "study",
// onno kono full-screen timer page open na thakle null
internal object TimerForegroundState {
    var activeScreen by mutableStateOf<String?>(null)
}