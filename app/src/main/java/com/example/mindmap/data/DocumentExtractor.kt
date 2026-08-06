package com.example.mindmap.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

data class ExtractedDocument(
    val paragraphs: List<String>,
    val images: List<Bitmap>
)

data class ExtractedSlide(
    val texts: List<String>,
    val images: List<Bitmap>
)

data class ExtractedPresentation(
    val slides: List<ExtractedSlide>
)

// docx/pptx আসলে zip ফাইল - এখান থেকে সব entry (নাম -> raw bytes) পড়ে নেওয়া হচ্ছে
private fun readZipEntries(context: Context, uri: Uri): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    return entries
}

fun extractDocx(context: Context, uri: Uri): ExtractedDocument {
    val entries = readZipEntries(context, uri)
    val documentXml = entries["word/document.xml"]
        ?: return ExtractedDocument(emptyList(), emptyList())

    val paragraphs = mutableListOf<String>()
    val currentParagraph = StringBuilder()

    val parser: XmlPullParser = Xml.newPullParser()
    parser.setInput(documentXml.inputStream(), "UTF-8")
    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "t" -> {
                        val text = if (parser.next() == XmlPullParser.TEXT) parser.text else ""
                        currentParagraph.append(text)
                    }
                    "tab" -> currentParagraph.append("\t")
                    "br" -> currentParagraph.append("\n")
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "p") {
                    paragraphs += currentParagraph.toString()
                    currentParagraph.clear()
                }
            }
        }
        eventType = parser.next()
    }
    if (currentParagraph.isNotBlank()) paragraphs += currentParagraph.toString()

    val images = entries.entries
        .filter { it.key.startsWith("word/media/") }
        .sortedBy { it.key }
        .mapNotNull { (_, bytes) ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }

    return ExtractedDocument(paragraphs.filter { it.isNotBlank() }, images)
}

fun extractPptx(context: Context, uri: Uri): ExtractedPresentation {
    val entries = readZipEntries(context, uri)

    val slideEntries = entries.keys
        .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
        .sortedBy { name ->
            Regex("slide(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

    val slides = slideEntries.map { slideName ->
        val bytes = entries[slideName]!!
        val texts = mutableListOf<String>()
        val currentText = StringBuilder()

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        val text = if (parser.next() == XmlPullParser.TEXT) parser.text else ""
                        currentText.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p" && currentText.isNotEmpty()) {
                        texts += currentText.toString()
                        currentText.clear()
                    }
                }
            }
            eventType = parser.next()
        }
        if (currentText.isNotBlank()) texts += currentText.toString()

        // এই স্লাইডের rels ফাইল থেকে কোন media/image ব্যবহার হয়েছে সেটা বের করা
        val relsName = "ppt/slides/_rels/${slideName.substringAfterLast('/')}.rels"
        val slideImages = mutableListOf<Bitmap>()
        entries[relsName]?.let { relsBytes ->
            val relsText = String(relsBytes, Charsets.UTF_8)
            Regex("Target=\"\\.\\./media/([^\"]+)\"").findAll(relsText).forEach { match ->
                val mediaFile = "ppt/media/${match.groupValues[1]}"
                entries[mediaFile]?.let { imgBytes ->
                    runCatching { BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) }
                        .getOrNull()?.let { slideImages += it }
                }
            }
        }

        ExtractedSlide(texts.filter { it.isNotBlank() }, slideImages)
    }

    return ExtractedPresentation(slides)
}
data class ExtractedSheet(
    val name: String,
    val rows: List<List<String>>
)

data class ExtractedWorkbook(
    val sheets: List<ExtractedSheet>
)

fun extractXlsx(context: Context, uri: Uri): ExtractedWorkbook {
    val entries = readZipEntries(context, uri)

    // shared strings (xlsx text গুলো আলাদা ফাইলে থাকে, cell শুধু index রাখে)
    val sharedStrings = mutableListOf<String>()
    entries["xl/sharedStrings.xml"]?.let { bytes ->
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var eventType = parser.eventType
        var insideSi = false
        val currentText = StringBuilder()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> { insideSi = true; currentText.clear() }
                        "t" -> if (insideSi) {
                            val text = if (parser.next() == XmlPullParser.TEXT) parser.text else ""
                            currentText.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        sharedStrings += currentText.toString()
                        insideSi = false
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // sheet-এর নাম workbook.xml থেকে
    val sheetNames = mutableListOf<String>()
    entries["xl/workbook.xml"]?.let { bytes ->
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                sheetNames += (parser.getAttributeValue(null, "name") ?: "Sheet")
            }
            eventType = parser.next()
        }
    }

    val sheetFileEntries = entries.keys
        .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
        .sortedBy { name -> Regex("sheet(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }

    val sheets = sheetFileEntries.mapIndexed { sheetIndex, sheetFileName ->
        val bytes = entries[sheetFileName]!!
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentCellType: String? = null
        var currentCellText = StringBuilder()

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellText = StringBuilder()
                        }
                        "v", "t" -> {
                            val text = if (parser.next() == XmlPullParser.TEXT) parser.text else ""
                            currentCellText.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "c") {
                        val rawValue = currentCellText.toString()
                        val displayValue = if (currentCellType == "s") {
                            rawValue.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: rawValue
                        } else {
                            rawValue
                        }
                        currentRow += displayValue
                    } else if (parser.name == "row") {
                        rows += currentRow
                    }
                }
            }
            eventType = parser.next()
        }

        ExtractedSheet(
            name = sheetNames.getOrNull(sheetIndex) ?: "Sheet ${sheetIndex + 1}",
            rows = rows
        )
    }

    return ExtractedWorkbook(sheets)
}