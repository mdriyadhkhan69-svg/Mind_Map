package com.example.mindmap.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mindmap.data.LineEntity
import com.example.mindmap.data.LineRepository
import com.example.mindmap.data.MediaEntity
import com.example.mindmap.data.MediaRepository
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.NodeRepository
import com.example.mindmap.data.SectionEntity
import com.example.mindmap.data.SectionRepository
import com.example.mindmap.data.share.MindMapShareManager
import com.example.mindmap.data.share.SharePackageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ShareDialogBg = Color(0xFF171826)
private val ShareAccent = Color(0xFF64FFDA)

@Composable
fun ShareSectionDialog(
    section: SectionEntity,
    allNodes: List<NodeEntity>,
    allLines: List<LineEntity>,
    allMedia: List<MediaEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPreparing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val nodesInSection = remember(allNodes, section.id) { allNodes.filter { it.sectionId == section.id } }
    val linesInSection = remember(allLines, section.id) { allLines.filter { it.sectionId == section.id } }
    val mediaInSection = remember(allMedia, section.id) { allMedia.filter { it.sectionId == section.id } }

    Dialog(onDismissRequest = { if (!isPreparing) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ShareDialogBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Share Mind Map Section", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(section.title, color = ShareAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("${nodesInSection.size} boxes", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Text(
                    "${mediaInSection.size} attachment${if (mediaInSection.size == 1) "" else "s"}",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                )
                errorMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(enabled = !isPreparing, onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = !isPreparing && nodesInSection.isNotEmpty(),
                        onClick = {
                            isPreparing = true
                            errorMessage = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        MindMapShareManager.exportSection(
                                            context, section.title, nodesInSection, linesInSection, mediaInSection
                                        )
                                    }
                                }
                                isPreparing = false
                                result.onSuccess { exportResult ->
                                    val uri = MindMapShareManager.shareUriForFile(context, exportResult.file)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = MindMapShareManager.SHARE_MIME_TYPE
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Mind Map section"))
                                    }
                                    onDismiss()
                                }.onFailure {
                                    errorMessage = "শেয়ার প্যাকেজ তৈরি করা যায়নি"
                                }
                            }
                        }
                    ) {
                        Text(if (isPreparing) "Preparing..." else "Share", color = ShareAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ImportMindMapSectionDialog(
    packageUriString: String,
    sectionRepository: SectionRepository,
    nodeRepository: NodeRepository,
    lineRepository: LineRepository,
    mediaRepository: MediaRepository,
    existingSectionCount: Int,
    onSelectImportedSection: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember(packageUriString) { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var packageFile by remember(packageUriString) { mutableStateOf<File?>(null) }
    var packageData by remember(packageUriString) { mutableStateOf<SharePackageData?>(null) }

    LaunchedEffect(packageUriString) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = MindMapShareManager.materializePackageFile(context, Uri.parse(packageUriString))
                val data = MindMapShareManager.readPackage(file)
                file to data
            }
        }
        result.onSuccess { (file, data) ->
            packageFile = file
            packageData = data
        }.onFailure {
            errorMessage = "এই ফাইলটি বৈধ Mind Map Share Package নয়"
        }
        isLoading = false
    }

    Dialog(onDismissRequest = { if (!isImporting) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ShareDialogBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Import Mind Map Section", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                when {
                    isLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = ShareAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("পড়া হচ্ছে...", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                        }
                    }
                    errorMessage != null -> {
                        Text(errorMessage!!, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    }
                    packageData != null -> {
                        val data = packageData!!
                        Text(data.manifest.sectionTitle, color = ShareAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("${data.manifest.nodeCount} boxes", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                        Text(
                            "${data.manifest.attachmentCount} attachment${if (data.manifest.attachmentCount == 1) "" else "s"}",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "এটা তোমার Mind Map-এ একটা নতুন আলাদা Section হিসেবে যোগ হবে — কোনো বিদ্যমান ডেটা মুছে যাবে না।",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 11.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(enabled = !isImporting, onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = !isLoading && !isImporting && packageData != null && errorMessage == null,
                        onClick = {
                            val data = packageData ?: return@TextButton
                            val file = packageFile ?: return@TextButton
                            isImporting = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        MindMapShareManager.importIntoDatabase(
                                            data, file, context,
                                            sectionRepository, nodeRepository, lineRepository, mediaRepository,
                                            existingSectionCount
                                        )
                                    }
                                }
                                isImporting = false
                                result.onSuccess { newSectionId ->
                                    onSelectImportedSection(newSectionId)
                                    onDismiss()
                                }.onFailure {
                                    errorMessage = "Import ব্যর্থ হয়েছে"
                                }
                            }
                        }
                    ) {
                        Text(if (isImporting) "Importing..." else "Import", color = ShareAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}