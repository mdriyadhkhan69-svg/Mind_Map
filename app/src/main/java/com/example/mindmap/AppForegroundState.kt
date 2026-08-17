package com.example.mindmap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

// App বর্তমানে foreground-এ আছে কিনা - MainActivity.onResume/onStop থেকে আপডেট হয়
object AppForegroundState {
    val isForeground: MutableState<Boolean> = mutableStateOf(true)
}