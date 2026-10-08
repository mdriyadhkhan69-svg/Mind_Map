package com.example.mindmap

import android.content.Context
import java.io.File
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Durable handoff for a Mind Map package opened by another app.  The request is
 * retained until the import preview is explicitly dismissed or completed, so a
 * new intent cannot be lost while Compose is navigating or recreating.
 */
object ImportMindMapState {
    private const val PREFERENCES = "incoming_mind_map_import"
    private const val PENDING_URI = "pending_uri"
    private const val PENDING_SOURCE_URI = "pending_source_uri"
    private const val LAST_HANDLED_SOURCE_URI = "last_handled_source_uri"

    val pendingPackageUri: MutableState<String?> = mutableStateOf(null)
    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        pendingPackageUri.value = preferences.getString(PENDING_URI, null)
        loaded = true
    }

    @Synchronized
    fun enqueue(context: Context, sourceUri: String, packageUri: String) {
        ensureLoaded(context)
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val pendingSource = preferences.getString(PENDING_SOURCE_URI, null)
        val lastHandledSource = preferences.getString(LAST_HANDLED_SOURCE_URI, null)
        if (pendingSource == sourceUri || lastHandledSource == sourceUri) return
        preferences.edit()
            .putString(PENDING_URI, packageUri)
            .putString(PENDING_SOURCE_URI, sourceUri)
            .apply()
        pendingPackageUri.value = packageUri
    }

    @Synchronized
    fun complete(context: Context, packageUri: String) {
        ensureLoaded(context)
        if (pendingPackageUri.value != packageUri) return
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val sourceUri = preferences.getString(PENDING_SOURCE_URI, null)
        preferences.edit()
            .remove(PENDING_URI)
            .remove(PENDING_SOURCE_URI)
            .apply {
                if (sourceUri != null) putString(LAST_HANDLED_SOURCE_URI, sourceUri)
            }
            .apply()
        android.net.Uri.parse(packageUri).path?.let { File(it) }
            ?.takeIf { it.parentFile == File(context.filesDir, "incoming-share-packages") }
            ?.delete()
        pendingPackageUri.value = null
    }
}