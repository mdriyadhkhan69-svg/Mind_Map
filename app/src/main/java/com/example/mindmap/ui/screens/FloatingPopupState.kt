package com.example.mindmap.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Quick timer (Countdown/Stopwatch) floating popup ekhon show hocche kina —
// MindMapApp er global LaunchedEffect eta calculate kore, FloatingTimerService eta pore
object FloatingPopupVisibility {
    var showQuick by mutableStateOf(false)
    var showStudy by mutableStateOf(false)
}

// User jokhon quick timer-er floating popup-ta manually close (X) kore dey,
// tokhon eta true hoye jay — jotokkhon na abar notun kore timer start hocche
object QuickTimerPopupState {
    var manuallyDismissed by mutableStateOf(false)
}

// Study Timer-er popup kon subject-er jonno "pinned"/active dekhabe, ar
// user kon subject-er popup manually dismiss korche — sheta track kore
object StudyTimerPopupState {
    var activeSubjectId by mutableStateOf<String?>(null)
    var manuallyDismissedSubjectId by mutableStateOf<String?>(null)
}