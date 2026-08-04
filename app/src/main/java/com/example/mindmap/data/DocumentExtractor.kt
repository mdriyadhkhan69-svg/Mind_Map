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
