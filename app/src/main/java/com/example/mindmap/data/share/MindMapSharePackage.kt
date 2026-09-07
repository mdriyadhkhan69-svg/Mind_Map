package com.example.mindmap.data.share

// Pure transport/serialization model for a portable "Mind Map Section" share
// package. It never replaces NodeEntity/LineEntity/MediaEntity — export
// flattens an existing section's rows into this shape using package-local
// "exportId" strings (not DB row ids, since a receiver's ids may already be
// taken). Import walks this shape back into brand-new NodeEntity/LineEntity/
// MediaEntity rows via the existing repositories.

data class ShareNode(
    val exportId: String,
    val parentExportId: String?,
    val label: String,
    val orderIndex: Int,
    val x: Float,
    val y: Float,
    val isExpanded: Boolean,
    val isDone: Boolean,
    val colorArgb: Long?,
    val widthScale: Float,
    val heightScale: Float,
    val textSizeSp: Float,
    val textWeight: Int,
    val textColorArgb: Long?,
    val connectorColorArgb: Long?,
    val connectorStrokeWidth: Float,
    val isConnectorHidden: Boolean,
    val completionLineColorArgb: Long?
)

data class ShareLine(
    val nodeAExportId: String?,
    val nodeBExportId: String?,
    val looseAX: Float,
    val looseAY: Float,
    val looseBX: Float,
    val looseBY: Float,
    val colorArgb: Long,
    val strokeWidth: Float
)

data class ShareMedia(
    val exportId: String,
    val nodeExportId: String,
    val type: String,
    val displayName: String,
    val mimeType: String,
    val rotationDegrees: Float,
    val attachmentEntryName: String
)

data class ShareManifest(
    val packageVersion: Int,
    val appId: String,
    val exportedAtMillis: Long,
    val sectionTitle: String,
    val nodeCount: Int,
    val lineCount: Int,
    val attachmentCount: Int
)

data class SharePackageData(
    val manifest: ShareManifest,
    val nodes: List<ShareNode>,
    val lines: List<ShareLine>,
    val media: List<ShareMedia>
)