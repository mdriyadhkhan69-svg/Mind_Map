package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.CalendarEventEntity
import com.example.mindmap.data.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {
    val allEvents: StateFlow<List<CalendarEventEntity>> = repository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(event: CalendarEventEntity, onSaved: (CalendarEventEntity) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (event.id == 0L) repository.insert(event) else { repository.update(event); event.id }
            onSaved(event.copy(id = id))
        }
    }

    fun delete(event: CalendarEventEntity) {
        viewModelScope.launch { repository.delete(event) }
    }
}

class CalendarViewModelFactory(private val repository: CalendarRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CalendarViewModel(repository) as T
    }
}