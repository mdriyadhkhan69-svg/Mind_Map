package com.example.mindmap.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mindmap.data.ExtractedSlide
import com.example.mindmap.data.MediaEntity
import com.example.mindmap.data.extractDocx
import com.example.mindmap.data.extractPptx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
private val AccentCyan = Color(0xFF64FFDA)

@Composable
fun DocxViewerDialog(media: MediaEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var paragraphs by remember(media.uri) { mutableStateOf<List<String>>(emptyList()) }
    var images by remember(media.uri) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var isLoading by remember(media.uri) { mutableStateOf(true) }
    var errorMessage by remember(media.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(media.uri) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching { extractDocx(context, Uri.parse(media.uri)) }
        }
        result.onSuccess {
            paragraphs = it.paragraphs
            images = it.images
        }.onFailure {
            errorMessage = "ফাইলটি পড়া যাচ্ছে না"
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF101822), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171A2B))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = Color.White) }
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                    errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = Color.White)
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        if (images.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("ছবি", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            images.forEach { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                        if (paragraphs.isEmpty() && images.isEmpty()) {
                            Text("কোনো কনটেন্ট পাওয়া যায়নি", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PptxViewerDialog(media: MediaEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var slides by remember(media.uri) { mutableStateOf<List<ExtractedSlide>>(emptyList()) }
    var currentSlide by remember(media.uri) { mutableStateOf(0) }
    var isLoading by remember(media.uri) { mutableStateOf(true) }
    var errorMessage by remember(media.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(media.uri) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching { extractPptx(context, Uri.parse(media.uri)) }
        }
        result.onSuccess { slides = it.slides }
            .onFailure { errorMessage = "ফাইলটি পড়া যাচ্ছে না" }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF101822), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171A2B))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = Color.White) }
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    if (slides.isNotEmpty()) {
                        Text("${currentSlide + 1}/${slides.size}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                }
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                    errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = Color.White)
                    }
                    slides.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো স্লাইড পাওয়া যায়নি", color = Color.LightGray)
                    }
                    else -> {
                        val slide = slides[currentSlide]
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                        ) {
                            Text(
                                "Slide ${currentSlide + 1}",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            slide.texts.forEach { text ->
                                Text(
                                    text = text,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                            slide.images.forEach { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            if (slide.texts.isEmpty() && slide.images.isEmpty()) {
                                Text("এই স্লাইডে কনটেন্ট নেই", color = Color.LightGray)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF171A2B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(enabled = currentSlide > 0, onClick = { currentSlide-- }) {
                                Text("< আগের", color = if (currentSlide > 0) AccentCyan else Color.White.copy(alpha = 0.3f))
                            }
                            TextButton(enabled = currentSlide < slides.lastIndex, onClick = { currentSlide++ }) {
                                Text("পরের >", color = if (currentSlide < slides.lastIndex) AccentCyan else Color.White.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun XlsxViewerDialog(media: MediaEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sheets by remember(media.uri) { mutableStateOf<List<com.example.mindmap.data.ExtractedSheet>>(emptyList()) }
    var currentSheet by remember(media.uri) { mutableStateOf(0) }
    var isLoading by remember(media.uri) { mutableStateOf(true) }
    var errorMessage by remember(media.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(media.uri) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching { com.example.mindmap.data.extractXlsx(context, Uri.parse(media.uri)) }
        }
        result.onSuccess { sheets = it.sheets }
            .onFailure { errorMessage = "ফাইলটি পড়া যাচ্ছে না" }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF101822), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171A2B))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = Color.White) }
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                    errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = Color.White)
                    }
                    sheets.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো ডেটা পাওয়া যায়নি", color = Color.LightGray)
                    }
                    else -> {
                        if (sheets.size > 1) {
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
                                sheets.forEachIndexed { index, sheet ->
                                    val selected = index == currentSheet
                                    Text(
                                        text = sheet.name,
                                        color = if (selected) Color(0xFF0F1020) else Color.White,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) AccentCyan else Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .clickable { currentSheet = index }
                                    )
                                }
                            }
                        }
                        val sheet = sheets[currentSheet]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Column {
                                sheet.rows.forEach { row ->
                                    Row {
                                        row.forEach { cell ->
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = 90.dp)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(cell, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}