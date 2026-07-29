package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.SectionEntity
import com.example.mindmap.data.SectionRepository
import com.example.mindmap.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SectionViewModel(
    private val repository: SectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val allSections: StateFlow<List<SectionEntity>> = repository.getAllSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSectionId = MutableStateFlow<Long?>(null)
    val currentSectionId: StateFlow<Long?> = _currentSectionId

    init {
        viewModelScope.launch {
            combine(repository.getAllSections(), settingsRepository.selectedSectionId) { sections, savedSectionId ->
                sections to savedSectionId
            }.collect { (sections, savedSectionId) ->
                if (sections.isEmpty()) {
                    repository.insert(SectionEntity(title = "Section 1", orderIndex = 0))
                } else {
                    val currentId = _currentSectionId.value
                    val selectedId = when {
                        currentId != null && sections.any { it.id == currentId } -> currentId
                        savedSectionId != null && sections.any { it.id == savedSectionId } -> savedSectionId
                        else -> sections.first().id
                    }
                    if (_currentSectionId.value != selectedId) {
                        _currentSectionId.value = selectedId
                        settingsRepository.setSelectedSectionId(selectedId)
                    }
                }
            }
        }
    }

    fun selectSection(id: Long) {
        _currentSectionId.value = id
        viewModelScope.launch { settingsRepository.setSelectedSectionId(id) }
    }

    fun addSection() {
        viewModelScope.launch {
            val nextIndex = allSections.value.size
            val nextNumber = (allSections.value.maxOfOrNull { it.id } ?: 0) + 1
            val newTitle = "Section $nextNumber"
            val newId = repository.insert(SectionEntity(title = newTitle, orderIndex = nextIndex))
            _currentSectionId.value = newId
            settingsRepository.setSelectedSectionId(newId)
        }
    }

    fun renameSection(section: SectionEntity, newTitle: String) {
        viewModelScope.launch { repository.update(section.copy(title = newTitle)) }
    }

    fun removeSection(section: SectionEntity) {
        viewModelScope.launch { repository.delete(section) }
    }

    fun reorderSections(newOrder: List<SectionEntity>) {
        viewModelScope.launch {
            repository.updateAll(
                newOrder.mapIndexed { index, section -> section.copy(orderIndex = index) }
            )
        }
    }
}

class SectionViewModelFactory(
    private val repository: SectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SectionViewModel(repository, settingsRepository) as T
    }
}
