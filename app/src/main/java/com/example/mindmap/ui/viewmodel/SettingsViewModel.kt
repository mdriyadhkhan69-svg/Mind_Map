package com.example.mindmap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.CollapseAnimationStyle
import com.example.mindmap.data.RootCollisionBehavior
import com.example.mindmap.data.SectionStyle
import com.example.mindmap.data.SettingsRepository
import com.example.mindmap.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val glowIntensity: StateFlow<Float> = repository.glowIntensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.4f)

    val collapseAnimationStyle: StateFlow<CollapseAnimationStyle> = repository.collapseAnimationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollapseAnimationStyle.LINE_RETRACT)

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DEFAULT)

    val glowColorArgb: StateFlow<Long> = repository.glowColorArgb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF64FFDA)

    val zoomEnabled: StateFlow<Boolean> = repository.zoomEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val zoomScale: StateFlow<Float> = repository.zoomScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    val longPressPanEnabled: StateFlow<Boolean> = repository.longPressPanEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val smartRootLayoutEnabled: StateFlow<Boolean> = repository.smartRootLayoutEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rootCollisionBehavior: StateFlow<RootCollisionBehavior> = repository.rootCollisionBehavior
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RootCollisionBehavior.MOVE)

    val multipleRootsEnabled: StateFlow<Boolean> = repository.multipleRootsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val applySectionStyleToAll: StateFlow<Boolean> = repository.applySectionStyleToAll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val globalSectionStyle: StateFlow<SectionStyle> = repository.globalSectionStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SectionStyle())

    fun sectionStyle(sectionId: Long) = repository.sectionStyle(sectionId)
    fun canvasViewport(sectionId: Long) = repository.canvasViewport(sectionId)
    fun zoomEnabled(sectionId: Long) = repository.zoomEnabled(sectionId)
    fun zoomScale(sectionId: Long) = repository.zoomScale(sectionId)
    fun longPressPanEnabled(sectionId: Long) = repository.longPressPanEnabled(sectionId)

    fun setGlow(value: Float) {
        viewModelScope.launch { repository.setGlowIntensity(value) }
    }

    fun setCollapseAnimationStyle(style: CollapseAnimationStyle) {
        viewModelScope.launch { repository.setCollapseAnimationStyle(style) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setGlowColor(colorArgb: Long) {
        viewModelScope.launch { repository.setGlowColor(colorArgb) }
    }

    fun setZoomEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setZoomEnabled(enabled) }
    }

    fun setZoomScale(scale: Float) {
        viewModelScope.launch { repository.setZoomScale(scale) }
    }

    fun setLongPressPanEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLongPressPanEnabled(enabled) }
    }

    fun setLongPressPanEnabled(sectionId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setLongPressPanEnabled(sectionId, enabled) }
    }

    fun setZoomEnabled(sectionId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setZoomEnabled(sectionId, enabled) }
    }

    fun setZoomScale(sectionId: Long, scale: Float) {
        viewModelScope.launch { repository.setZoomScale(sectionId, scale) }
    }

    fun setSmartRootLayoutEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSmartRootLayoutEnabled(enabled) }
    }

    fun setRootCollisionBehavior(behavior: RootCollisionBehavior) {
        viewModelScope.launch { repository.setRootCollisionBehavior(behavior) }
    }

    fun setMultipleRootsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMultipleRootsEnabled(enabled) }
    }

    fun setCanvasViewport(sectionId: Long, x: Float, y: Float) {
        viewModelScope.launch { repository.setCanvasViewport(sectionId, x, y) }
    }

    fun setApplySectionStyleToAll(enabled: Boolean, sourceStyle: SectionStyle) {
        viewModelScope.launch { repository.setApplySectionStyleToAll(enabled, sourceStyle) }
    }

    fun setSectionBackground(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.setSectionBackground(sectionId, colorArgb, applyToAll) }
    }

    fun clearSectionBackground(sectionId: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.clearSectionBackground(sectionId, applyToAll) }
    }

    fun setSectionTextColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.setSectionTextColor(sectionId, colorArgb, applyToAll) }
    }

    fun clearSectionTextColor(sectionId: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.clearSectionTextColor(sectionId, applyToAll) }
    }

    fun setSectionBoxColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.setSectionBoxColor(sectionId, colorArgb, applyToAll) }
    }

    fun clearSectionBoxColor(sectionId: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.clearSectionBoxColor(sectionId, applyToAll) }
    }

    fun setSectionCompletionColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.setSectionCompletionColor(sectionId, colorArgb, applyToAll) }
    }

    fun clearSectionCompletionColor(sectionId: Long, applyToAll: Boolean) {
        viewModelScope.launch { repository.clearSectionCompletionColor(sectionId, applyToAll) }
    }

    fun setSectionTitleColor(sectionId: Long, colorArgb: Long) {
        viewModelScope.launch { repository.setSectionTitleColor(sectionId, colorArgb) }
    }

    fun clearSectionTitleColor(sectionId: Long) {
        viewModelScope.launch { repository.clearSectionTitleColor(sectionId) }
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository) as T
    }
}
