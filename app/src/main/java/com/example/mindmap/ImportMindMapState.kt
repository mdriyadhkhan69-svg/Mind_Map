package com.example.mindmap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

// অন্য app বা share sheet থেকে ".mmshare" Mind Map share package (uri) এলে
// সেটা এখানে সাময়িকভাবে রাখা হয় — MindMapApp সেটা দেখে import preview dialog দেখাবে
object ImportMindMapState {
    val pendingPackageUri: MutableState<String?> = mutableStateOf(null)
}