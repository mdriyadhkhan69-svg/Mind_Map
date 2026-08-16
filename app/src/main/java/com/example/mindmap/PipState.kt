package com.example.mindmap

import android.content.Context
import androidx.compose.runtime.mutableStateOf

// Activity বর্তমানে PiP (ছোট floating) মোডে আছে কিনা - এটা MainActivity সেট করবে,
// আর Compose UI এটা দেখে বুঝবে কখন compact timer view দেখাতে হবে
object PipState {
    val isInPictureInPicture = mutableStateOf(false)
}

// কোনো timer (quick timer অথবা study subject) চলছে কিনা - এটা সবসময় আপডেট থাকে,
// যাতে MainActivity app minimize হওয়ার মুহূর্তেই বুঝতে পারে PiP দেখাবে কিনা
object TimerRunningState {
    val isAnyTimerRunning = mutableStateOf(false)
}

fun loadPipEnabled(context: Context): Boolean =
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getBoolean("pip_enabled", true)

fun savePipEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putBoolean("pip_enabled", value).apply()
}