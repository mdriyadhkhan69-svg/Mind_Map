package com.example.mindmap.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.mindmap.data.LineEntity
import com.example.mindmap.data.LineRepository
import com.example.mindmap.data.MediaEntity
import com.example.mindmap.data.MediaRepository
import com.example.mindmap.data.MediaType
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.NodeRepository
import com.example.mindmap.data.SectionEntity
import com.example.mindmap.data.SectionRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Reuses the EXISTING Mind Map data model and the EXISTING Room repositories
// for both export and import. This object only adds a portable, versioned
// ZIP transport format ("*.mmshare") on top of that existing system.
object MindMapShareManager {

    const val SHARE_MIME_TYPE = "application/vnd.mindmap.mmsection"
    const val SHARE_FILE_EXTENSION = "mmshare"
    private const val PACKAGE_VERSION = 1
    private const val ENTRY_PACKAGE_JSON = "package.json"
    private const val ATTACHMENTS_DIR = "attachments/"

    data class ExportResult(val file: File, val nodeCount: Int, val attachmentCount: Int)

    /** Sender side: flattens one section's nodes/lines/media into a portable ZIP file in app cache. */
    fun exportSection(
        context: Context,
        sectionTitle: String,
        nodes: List<NodeEntity>,
        lines: List<LineEntity>,
        media: List<MediaEntity>
    ): ExportResult {
        val exportIdByNodeId = HashMap<Long, String>()
        nodes.forEachIndexed { index, node -> exportIdByNodeId[node.id] = "n$index" }

        val shareNodes = nodes.map { node ->
            ShareNode(
                exportId = exportIdByNodeId.getValue(node.id),
                parentExportId = node.parentId?.let { exportIdByNodeId[it] },
                label = node.label,
                orderIndex = node.orderIndex,
                x = node.x,
                y = node.y,
                isExpanded = node.isExpanded,
                isDone = node.isDone,
                colorArgb = node.colorArgb,
                widthScale = node.widthScale,
                heightScale = node.heightScale,
                textSizeSp = node.textSizeSp,
                textWeight = node.textWeight,
                textColorArgb = node.textColorArgb,
                connectorColorArgb = node.connectorColorArgb,
                connectorStrokeWidth = node.connectorStrokeWidth,
                isConnectorHidden = node.isConnectorHidden,
                completionLineColorArgb = node.completionLineColorArgb
            )
        }

        val shareLines = lines.map { line ->
            ShareLine(
                nodeAExportId = line.nodeAId?.let { exportIdByNodeId[it] },
                nodeBExportId = line.nodeBId?.let { exportIdByNodeId[it] },
                looseAX = line.looseAX,
                looseAY = line.looseAY,
                looseBX = line.looseBX,
                looseBY = line.looseBY,
                colorArgb = line.colorArgb,
                strokeWidth = line.strokeWidth
            )
        }

        val shareMediaEntries = media.mapIndexedNotNull { index, m ->
            val nodeExportId = exportIdByNodeId[m.nodeId] ?: return@mapIndexedNotNull null
            val extension = m.displayName.substringAfterLast('.', "")
                .takeIf { it.isNotBlank() }
                ?.let { ".${it.take(10)}" }
                .orEmpty()
            val entryName = "${ATTACHMENTS_DIR}m$index$extension"
            m to ShareMedia(
                exportId = "m$index",
                nodeExportId = nodeExportId,
                type = m.type.name,
                displayName = m.displayName,
                mimeType = m.mimeType,
                rotationDegrees = m.rotationDegrees,
                attachmentEntryName = entryName
            )
        }

        val manifest = ShareManifest(
            packageVersion = PACKAGE_VERSION,
            appId = context.packageName,
            exportedAtMillis = System.currentTimeMillis(),
            sectionTitle = sectionTitle,
            nodeCount = shareNodes.size,
            lineCount = shareLines.size,
            attachmentCount = shareMediaEntries.size
        )

        val packageData = SharePackageData(manifest, shareNodes, shareLines, shareMediaEntries.map { it.second })
        val packageJson = packageDataToJson(packageData).toString()

        val outputDir = File(context.cacheDir, "shared-files").apply { mkdirs() }
        val safeTitle = sectionTitle.ifBlank { "section" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
        val outputFile = File(outputDir, "mindmap_${safeTitle}_${System.currentTimeMillis()}.$SHARE_FILE_EXTENSION")

        FileOutputStream(outputFile).use { fileOut ->
            ZipOutputStream(fileOut).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_PACKAGE_JSON))
                zip.write(packageJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                shareMediaEntries.forEach { (mediaEntity, shareMedia) ->
                    runCatching {
                        val resolver = context.contentResolver
                        val sourceUri = Uri.parse(mediaEntity.uri)
                        val input = resolver.openInputStream(sourceUri)
                            ?: resolver.openAssetFileDescriptor(sourceUri, "r")?.createInputStream()
                            ?: resolver.openFileDescriptor(sourceUri, "r")?.let {
                                android.os.ParcelFileDescriptor.AutoCloseInputStream(it)
                            }
                        input?.use { source ->
                            zip.putNextEntry(ZipEntry(shareMedia.attachmentEntryName))
                            source.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
                }
            }
        }

        return ExportResult(outputFile, shareNodes.size, shareMediaEntries.size)
    }

    fun shareUriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)

    /** Receiver side: copies the incoming (possibly content://) package into a local cache file for random-access reading. */
    fun materializePackageFile(context: Context, uri: Uri): File {
        val cacheDir = File(context.cacheDir, "shared-files").apply { mkdirs() }
        val target = File(cacheDir, "import_${System.currentTimeMillis()}.$SHARE_FILE_EXTENSION")
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri)
            ?: resolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
            ?: resolver.openFileDescriptor(uri, "r")?.let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it)
            }
            ?: error("Unable to read shared package")
        input.use { source -> target.outputStream().use { dest -> source.copyTo(dest) } }
        return target
    }

    fun readPackage(file: File): SharePackageData {
        ZipFile(file).use { zipFile ->
            val entry = zipFile.getEntry(ENTRY_PACKAGE_JSON)
                ?: error("Not a valid Mind Map share package")
            val json = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return jsonToPackageData(JSONObject(json))
        }
    }

    private fun extractAttachmentBytes(file: File, entryName: String): ByteArray? {
        return ZipFile(file).use { zipFile ->
            val entry = zipFile.getEntry(entryName) ?: return@use null
            zipFile.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun copyAttachmentFromPackage(context: Context, packageFile: File, media: ShareMedia): Uri? {
        val bytes = extractAttachmentBytes(packageFile, media.attachmentEntryName) ?: return null
        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val extension = media.displayName.substringAfterLast('.', "").replace(Regex("[^A-Za-z0-9]"), "")
        val target = File(
            attachmentsDir,
            "attachment_${System.nanoTime()}${if (extension.isBlank()) "" else ".${extension.take(12)}"}"
        )
        target.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", target)
    }

    /**
     * Reconstructs the shared section into the receiver's EXISTING database, via the EXISTING
     * repositories — always as a brand-new section with brand-new row ids, so nothing in the
     * receiver's current data is ever overwritten or ID-collided with.
     */
    suspend fun importIntoDatabase(
        packageData: SharePackageData,
        packageFile: File,
        context: Context,
        sectionRepository: SectionRepository,
        nodeRepository: NodeRepository,
        lineRepository: LineRepository,
        mediaRepository: MediaRepository,
        existingSectionCount: Int
    ): Long {
        val baseTitle = packageData.manifest.sectionTitle.ifBlank { "Imported Section" }
        val newSectionId = sectionRepository.insert(
            SectionEntity(title = "$baseTitle (Imported)", orderIndex = existingSectionCount)
        )

        val newIdByExportId = HashMap<String, Long>()
        val remainingNodes = packageData.nodes.toMutableList()
        var progressed = true
        while (remainingNodes.isNotEmpty() && progressed) {
            progressed = false
            val iterator = remainingNodes.iterator()
            while (iterator.hasNext()) {
                val shareNode = iterator.next()
                val parentReady = shareNode.parentExportId == null || newIdByExportId.containsKey(shareNode.parentExportId)
                if (parentReady) {
                    val newParentId = shareNode.parentExportId?.let { newIdByExportId[it] }
                    val insertedId = nodeRepository.insert(shareNode.toNodeEntity(newSectionId, newParentId))
                    newIdByExportId[shareNode.exportId] = insertedId
                    iterator.remove()
                    progressed = true
                }
            }
        }
        // Any leftover nodes had a broken/cyclic parent reference in the package —
        // attach them as roots instead of silently dropping them.
        remainingNodes.forEach { shareNode ->
            val insertedId = nodeRepository.insert(shareNode.toNodeEntity(newSectionId, parentId = null))
            newIdByExportId[shareNode.exportId] = insertedId
        }

        packageData.lines.forEach { shareLine ->
            lineRepository.insert(
                LineEntity(
                    sectionId = newSectionId,
                    nodeAId = shareLine.nodeAExportId?.let { newIdByExportId[it] },
                    nodeBId = shareLine.nodeBExportId?.let { newIdByExportId[it] },
                    looseAX = shareLine.looseAX,
                    looseAY = shareLine.looseAY,
                    looseBX = shareLine.looseBX,
                    looseBY = shareLine.looseBY,
                    colorArgb = shareLine.colorArgb,
                    strokeWidth = shareLine.strokeWidth
                )
            )
        }

        packageData.media.forEach { shareMedia ->
            val newNodeId = newIdByExportId[shareMedia.nodeExportId] ?: return@forEach
            val copiedUri = copyAttachmentFromPackage(context, packageFile, shareMedia) ?: return@forEach
            mediaRepository.insert(
                MediaEntity(
                    sectionId = newSectionId,
                    nodeId = newNodeId,
                    type = if (shareMedia.type == "IMAGE") MediaType.IMAGE else MediaType.FILE,
                    uri = copiedUri.toString(),
                    displayName = shareMedia.displayName,
                    mimeType = shareMedia.mimeType,
                    rotationDegrees = shareMedia.rotationDegrees
                )
            )
        }

        return newSectionId
    }

    private fun ShareNode.toNodeEntity(sectionId: Long, parentId: Long?) = NodeEntity(
        sectionId = sectionId,
        parentId = parentId,
        label = label,
        orderIndex = orderIndex,
        x = x,
        y = y,
        isExpanded = isExpanded,
        isDone = isDone,
        colorArgb = colorArgb,
        widthScale = widthScale,
        heightScale = heightScale,
        textSizeSp = textSizeSp,
        textWeight = textWeight,
        textColorArgb = textColorArgb,
        connectorColorArgb = connectorColorArgb,
        connectorStrokeWidth = connectorStrokeWidth,
        isConnectorHidden = isConnectorHidden,
        completionLineColorArgb = completionLineColorArgb
    )

    // ---- JSON (de)serialization ----

    private fun packageDataToJson(data: SharePackageData): JSONObject {
        val manifestJson = JSONObject()
            .put("packageVersion", data.manifest.packageVersion)
            .put("appId", data.manifest.appId)
            .put("exportedAtMillis", data.manifest.exportedAtMillis)
            .put("sectionTitle", data.manifest.sectionTitle)
            .put("nodeCount", data.manifest.nodeCount)
            .put("lineCount", data.manifest.lineCount)
            .put("attachmentCount", data.manifest.attachmentCount)

        val nodesArray = JSONArray()
        data.nodes.forEach { n ->
            nodesArray.put(
                JSONObject()
                    .put("exportId", n.exportId)
                    .put("parentExportId", n.parentExportId ?: JSONObject.NULL)
                    .put("label", n.label)
                    .put("orderIndex", n.orderIndex)
                    .put("x", n.x.toDouble())
                    .put("y", n.y.toDouble())
                    .put("isExpanded", n.isExpanded)
                    .put("isDone", n.isDone)
                    .put("colorArgb", n.colorArgb ?: JSONObject.NULL)
                    .put("widthScale", n.widthScale.toDouble())
                    .put("heightScale", n.heightScale.toDouble())
                    .put("textSizeSp", n.textSizeSp.toDouble())
                    .put("textWeight", n.textWeight)
                    .put("textColorArgb", n.textColorArgb ?: JSONObject.NULL)
                    .put("connectorColorArgb", n.connectorColorArgb ?: JSONObject.NULL)
                    .put("connectorStrokeWidth", n.connectorStrokeWidth.toDouble())
                    .put("isConnectorHidden", n.isConnectorHidden)
                    .put("completionLineColorArgb", n.completionLineColorArgb ?: JSONObject.NULL)
            )
        }

        val linesArray = JSONArray()
        data.lines.forEach { l ->
            linesArray.put(
                JSONObject()
                    .put("nodeAExportId", l.nodeAExportId ?: JSONObject.NULL)
                    .put("nodeBExportId", l.nodeBExportId ?: JSONObject.NULL)
                    .put("looseAX", l.looseAX.toDouble())
                    .put("looseAY", l.looseAY.toDouble())
                    .put("looseBX", l.looseBX.toDouble())
                    .put("looseBY", l.looseBY.toDouble())
                    .put("colorArgb", l.colorArgb)
                    .put("strokeWidth", l.strokeWidth.toDouble())
            )
        }

        val mediaArray = JSONArray()
        data.media.forEach { m ->
            mediaArray.put(
                JSONObject()
                    .put("exportId", m.exportId)
                    .put("nodeExportId", m.nodeExportId)
                    .put("type", m.type)
                    .put("displayName", m.displayName)
                    .put("mimeType", m.mimeType)
                    .put("rotationDegrees", m.rotationDegrees.toDouble())
                    .put("attachmentEntryName", m.attachmentEntryName)
            )
        }

        return JSONObject()
            .put("manifest", manifestJson)
            .put("nodes", nodesArray)
            .put("lines", linesArray)
            .put("media", mediaArray)
    }

    private fun jsonToPackageData(root: JSONObject): SharePackageData {
        val manifestJson = root.getJSONObject("manifest")
        val manifest = ShareManifest(
            packageVersion = manifestJson.optInt("packageVersion", 1),
            appId = manifestJson.optString("appId"),
            exportedAtMillis = manifestJson.optLong("exportedAtMillis"),
            sectionTitle = manifestJson.optString("sectionTitle", "Shared section"),
            nodeCount = manifestJson.optInt("nodeCount", 0),
            lineCount = manifestJson.optInt("lineCount", 0),
            attachmentCount = manifestJson.optInt("attachmentCount", 0)
        )

        val nodes = mutableListOf<ShareNode>()
        val nodesArray = root.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until nodesArray.length()) {
            val o = nodesArray.getJSONObject(i)
            nodes += ShareNode(
                exportId = o.getString("exportId"),
                parentExportId = if (o.isNull("parentExportId")) null else o.optString("parentExportId"),
                label = o.optString("label"),
                orderIndex = o.optInt("orderIndex", 0),
                x = o.optDouble("x", 0.0).toFloat(),
                y = o.optDouble("y", 0.0).toFloat(),
                isExpanded = o.optBoolean("isExpanded", false),
                isDone = o.optBoolean("isDone", false),
                colorArgb = if (o.isNull("colorArgb")) null else o.optLong("colorArgb"),
                widthScale = o.optDouble("widthScale", 1.0).toFloat(),
                heightScale = o.optDouble("heightScale", 1.0).toFloat(),
                textSizeSp = o.optDouble("textSizeSp", 16.0).toFloat(),
                textWeight = o.optInt("textWeight", 400),
                textColorArgb = if (o.isNull("textColorArgb")) null else o.optLong("textColorArgb"),
                connectorColorArgb = if (o.isNull("connectorColorArgb")) null else o.optLong("connectorColorArgb"),
                connectorStrokeWidth = o.optDouble("connectorStrokeWidth", 3.0).toFloat(),
                isConnectorHidden = o.optBoolean("isConnectorHidden", false),
                completionLineColorArgb = if (o.isNull("completionLineColorArgb")) null else o.optLong("completionLineColorArgb")
            )
        }

        val lines = mutableListOf<ShareLine>()
        val linesArray = root.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until linesArray.length()) {
            val o = linesArray.getJSONObject(i)
            lines += ShareLine(
                nodeAExportId = if (o.isNull("nodeAExportId")) null else o.optString("nodeAExportId"),
                nodeBExportId = if (o.isNull("nodeBExportId")) null else o.optString("nodeBExportId"),
                looseAX = o.optDouble("looseAX", 0.0).toFloat(),
                looseAY = o.optDouble("looseAY", 0.0).toFloat(),
                looseBX = o.optDouble("looseBX", 0.0).toFloat(),
                looseBY = o.optDouble("looseBY", 0.0).toFloat(),
                colorArgb = o.optLong("colorArgb", 0xFF64FFDA),
                strokeWidth = o.optDouble("strokeWidth", 4.0).toFloat()
            )
        }

        val media = mutableListOf<ShareMedia>()
        val mediaArray = root.optJSONArray("media") ?: JSONArray()
        for (i in 0 until mediaArray.length()) {
            val o = mediaArray.getJSONObject(i)
            media += ShareMedia(
                exportId = o.getString("exportId"),
                nodeExportId = o.optString("nodeExportId"),
                type = o.optString("type", "FILE"),
                displayName = o.optString("displayName", "Attachment"),
                mimeType = o.optString("mimeType", "application/octet-stream"),
                rotationDegrees = o.optDouble("rotationDegrees", 0.0).toFloat(),
                attachmentEntryName = o.optString("attachmentEntryName")
            )
        }

        return SharePackageData(manifest, nodes, lines, media)
    }
}