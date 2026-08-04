package com.example.mindmap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

// অন্য app (WhatsApp, File Manager ইত্যাদি) থেকে "Open with" দিয়ে
// আমাদের app-কে PDF ফাইল পাঠালে সেই ফাইলের URI এখানে সাময়িকভাবে রাখা হবে,
// আর MindMapScreen সেটা দেখে সরাসরি PdfViewerDialog খুলে দেবে
object ExternalOpenState {
    val pendingPdfUri: MutableState<String?> = mutableStateOf(null)
}
