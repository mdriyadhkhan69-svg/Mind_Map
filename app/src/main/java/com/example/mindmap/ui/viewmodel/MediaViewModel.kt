package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.MediaEntity
import com.example.mindmap.data.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewModel(private val repository: MediaRepository) : ViewModel() {
    val allMedia: StateFlow<List<MediaEntity>> = repository.getAllMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun replaceNodeMedia(media: MediaEntity) {
        viewModelScope.launch { repository.replaceNodeMedia(media) }
    }

    fun update(media: MediaEntity) {
        viewModelScope.launch { repository.update(media) }
    }

    fun delete(mediaId: Long) {
        viewModelScope.launch { repository.delete(mediaId) }
    }
}

class MediaViewModelFactory(private val repository: MediaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MediaViewModel(repository) as T
    }
}
