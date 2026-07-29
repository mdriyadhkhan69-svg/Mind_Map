package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.LineEntity
import com.example.mindmap.data.LineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LineViewModel(private val repository: LineRepository) : ViewModel() {
    val allLines: StateFlow<List<LineEntity>> = repository.getAllLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLine(sectionId: Long, nodeAId: Long, nodeBId: Long) {
        viewModelScope.launch {
            repository.insert(LineEntity(sectionId = sectionId, nodeAId = nodeAId, nodeBId = nodeBId))
        }
    }

    fun addDetachedLine(
        sectionId: Long,
        nodeAId: Long,
        looseBX: Float,
        looseBY: Float,
        colorArgb: Long,
        strokeWidth: Float
    ) {
        viewModelScope.launch {
            repository.insert(
                LineEntity(
                    sectionId = sectionId,
                    nodeAId = nodeAId,
                    nodeBId = null,
                    looseBX = looseBX,
                    looseBY = looseBY,
                    colorArgb = colorArgb,
                    strokeWidth = strokeWidth.coerceIn(1f, 16f)
                )
            )
        }
    }

    fun updateLine(line: LineEntity) {
        viewModelScope.launch { repository.update(line) }
    }

    fun removeLine(line: LineEntity) {
        viewModelScope.launch { repository.delete(line) }
    }
}

class LineViewModelFactory(private val repository: LineRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LineViewModel(repository) as T
    }
}
