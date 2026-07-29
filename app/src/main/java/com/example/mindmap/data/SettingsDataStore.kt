package com.example.mindmap.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class CollapseAnimationStyle { FADE, LINE_RETRACT }
enum class ThemeMode { DEFAULT, WHITE }
enum class RootCollisionBehavior { MOVE, HIDE }
data class SectionStyle(
    val backgroundArgb: Long? = null,
    val textArgb: Long? = null,
    val boxArgb: Long? = null,
    val titleArgb: Long? = null,
    val completionArgb: Long? = null
)
data class CanvasViewport(val x: Float = 0f, val y: Float = 0f)

class SettingsRepository(private val context: Context) {
    companion object {
        val GLOW_KEY = floatPreferencesKey("glow_intensity")
        val COLLAPSE_STYLE_KEY = stringPreferencesKey("collapse_animation_style")
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val GLOW_COLOR_KEY = longPreferencesKey("glow_color")
        val SELECTED_SECTION_ID_KEY = longPreferencesKey("selected_section_id")
        val ZOOM_ENABLED_KEY = booleanPreferencesKey("zoom_enabled")
        val ZOOM_SCALE_KEY = floatPreferencesKey("zoom_scale")
        val LONG_PRESS_PAN_KEY = booleanPreferencesKey("long_press_pan")
        val SMART_ROOT_LAYOUT_ENABLED_KEY = booleanPreferencesKey("smart_root_layout_enabled")
        val ROOT_COLLISION_BEHAVIOR_KEY = stringPreferencesKey("root_collision_behavior")
        val MULTIPLE_ROOTS_ENABLED_KEY = booleanPreferencesKey("multiple_roots_enabled")
        val APPLY_STYLE_TO_ALL_KEY = booleanPreferencesKey("apply_section_style_to_all")
        val GLOBAL_BACKGROUND_COLOR_KEY = longPreferencesKey("global_background_color")
        val GLOBAL_TEXT_COLOR_KEY = longPreferencesKey("global_text_color")
        val GLOBAL_BOX_COLOR_KEY = longPreferencesKey("global_box_color")
        val GLOBAL_COMPLETION_COLOR_KEY = longPreferencesKey("global_completion_color")
        const val RESET_COLOR = Long.MIN_VALUE
    }

    val glowIntensity: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[GLOW_KEY] ?: 0.4f
    }

    val collapseAnimationStyle: Flow<CollapseAnimationStyle> = context.settingsDataStore.data.map { prefs ->
        CollapseAnimationStyle.valueOf(prefs[COLLAPSE_STYLE_KEY] ?: CollapseAnimationStyle.LINE_RETRACT.name)
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.DEFAULT.name)
    }

    val glowColorArgb: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[GLOW_COLOR_KEY] ?: 0xFF64FFDA
    }

    val selectedSectionId: Flow<Long?> = context.settingsDataStore.data.map { prefs ->
        prefs[SELECTED_SECTION_ID_KEY]
    }

    val zoomEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ZOOM_ENABLED_KEY] ?: false
    }

    val zoomScale: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        (prefs[ZOOM_SCALE_KEY] ?: 1f).coerceIn(0.2f, 3f)
    }

    val longPressPanEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[LONG_PRESS_PAN_KEY] ?: false
    }

    fun zoomEnabled(sectionId: Long): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[zoomEnabledKey(sectionId)] ?: false
    }

    fun zoomScale(sectionId: Long): Flow<Float> = context.settingsDataStore.data.map { prefs ->
        (prefs[zoomScaleKey(sectionId)] ?: 1f).coerceIn(0.2f, 3f)
    }

    fun longPressPanEnabled(sectionId: Long): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[longPressPanKey(sectionId)] ?: false
    }

    val smartRootLayoutEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SMART_ROOT_LAYOUT_ENABLED_KEY] ?: false
    }

    val rootCollisionBehavior: Flow<RootCollisionBehavior> = context.settingsDataStore.data.map { prefs ->
        RootCollisionBehavior.entries.firstOrNull { it.name == prefs[ROOT_COLLISION_BEHAVIOR_KEY] }
            ?: RootCollisionBehavior.MOVE
    }

    val multipleRootsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[MULTIPLE_ROOTS_ENABLED_KEY] ?: false
    }

    val applySectionStyleToAll: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[APPLY_STYLE_TO_ALL_KEY] ?: false
    }

    val globalSectionStyle: Flow<SectionStyle> = context.settingsDataStore.data.map { prefs ->
        SectionStyle(
            backgroundArgb = prefs[GLOBAL_BACKGROUND_COLOR_KEY].takeUsableColor(),
            textArgb = prefs[GLOBAL_TEXT_COLOR_KEY].takeUsableColor(),
            boxArgb = prefs[GLOBAL_BOX_COLOR_KEY].takeUsableColor(),
            completionArgb = prefs[GLOBAL_COMPLETION_COLOR_KEY].takeUsableColor()
        )
    }

    fun sectionStyle(sectionId: Long): Flow<SectionStyle> = context.settingsDataStore.data.map { prefs ->
        SectionStyle(
            backgroundArgb = prefs[backgroundColorKey(sectionId)].takeUsableColor(),
            textArgb = prefs[textColorKey(sectionId)].takeUsableColor(),
            boxArgb = prefs[boxColorKey(sectionId)].takeUsableColor(),
            titleArgb = prefs[titleColorKey(sectionId)].takeUsableColor(),
            completionArgb = prefs[completionColorKey(sectionId)].takeUsableColor()
        )
    }

    fun canvasViewport(sectionId: Long): Flow<CanvasViewport> = context.settingsDataStore.data.map { prefs ->
        CanvasViewport(
            x = prefs[canvasOffsetXKey(sectionId)] ?: 0f,
            y = prefs[canvasOffsetYKey(sectionId)] ?: 0f
        )
    }

    suspend fun setGlowIntensity(value: Float) {
        context.settingsDataStore.edit { it[GLOW_KEY] = value.coerceIn(0f, 1.5f) }
    }

    suspend fun setCollapseAnimationStyle(style: CollapseAnimationStyle) {
        context.settingsDataStore.edit { it[COLLAPSE_STYLE_KEY] = style.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setGlowColor(colorArgb: Long) {
        context.settingsDataStore.edit { it[GLOW_COLOR_KEY] = colorArgb }
    }

    suspend fun setSelectedSectionId(sectionId: Long) {
        context.settingsDataStore.edit { it[SELECTED_SECTION_ID_KEY] = sectionId }
    }

    suspend fun setZoomEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[ZOOM_ENABLED_KEY] = enabled
            if (!enabled) it[ZOOM_SCALE_KEY] = 1f
        }
    }

    suspend fun setZoomScale(scale: Float) {
        context.settingsDataStore.edit { it[ZOOM_SCALE_KEY] = scale.coerceIn(0.2f, 3f) }
    }

    suspend fun setLongPressPanEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[LONG_PRESS_PAN_KEY] = enabled }
    }

    suspend fun setZoomEnabled(sectionId: Long, enabled: Boolean) {
        context.settingsDataStore.edit {
            it[zoomEnabledKey(sectionId)] = enabled
            if (!enabled) it[zoomScaleKey(sectionId)] = 1f
        }
    }

    suspend fun setZoomScale(sectionId: Long, scale: Float) {
        context.settingsDataStore.edit { it[zoomScaleKey(sectionId)] = scale.coerceIn(0.2f, 3f) }
    }

    suspend fun setLongPressPanEnabled(sectionId: Long, enabled: Boolean) {
        context.settingsDataStore.edit { it[longPressPanKey(sectionId)] = enabled }
    }

    suspend fun setSmartRootLayoutEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[SMART_ROOT_LAYOUT_ENABLED_KEY] = enabled }
    }

    suspend fun setRootCollisionBehavior(behavior: RootCollisionBehavior) {
        context.settingsDataStore.edit { it[ROOT_COLLISION_BEHAVIOR_KEY] = behavior.name }
    }

    suspend fun setMultipleRootsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[MULTIPLE_ROOTS_ENABLED_KEY] = enabled }
    }

    suspend fun setCanvasViewport(sectionId: Long, x: Float, y: Float) {
        context.settingsDataStore.edit {
            it[canvasOffsetXKey(sectionId)] = x
            it[canvasOffsetYKey(sectionId)] = y
        }
    }

    suspend fun setApplySectionStyleToAll(enabled: Boolean, sourceStyle: SectionStyle) {
        context.settingsDataStore.edit {
            it[APPLY_STYLE_TO_ALL_KEY] = enabled
            if (enabled) {
                sourceStyle.backgroundArgb?.let { color ->
                    it[GLOBAL_BACKGROUND_COLOR_KEY] = color
                } ?: run { it[GLOBAL_BACKGROUND_COLOR_KEY] = RESET_COLOR }
                sourceStyle.textArgb?.let { color ->
                    it[GLOBAL_TEXT_COLOR_KEY] = color
                } ?: run { it[GLOBAL_TEXT_COLOR_KEY] = RESET_COLOR }
                sourceStyle.boxArgb?.let { color ->
                    it[GLOBAL_BOX_COLOR_KEY] = color
                } ?: run { it[GLOBAL_BOX_COLOR_KEY] = RESET_COLOR }
                sourceStyle.completionArgb?.let { color ->
                    it[GLOBAL_COMPLETION_COLOR_KEY] = color
                } ?: run { it[GLOBAL_COMPLETION_COLOR_KEY] = RESET_COLOR }
            }
        }
    }

    suspend fun setSectionBackground(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            it[backgroundColorKey(sectionId)] = colorArgb
            if (applyToAll) it[GLOBAL_BACKGROUND_COLOR_KEY] = colorArgb
        }
    }

    suspend fun clearSectionBackground(sectionId: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            if (applyToAll) {
                it[GLOBAL_BACKGROUND_COLOR_KEY] = RESET_COLOR
            } else {
                it[backgroundColorKey(sectionId)] = RESET_COLOR
            }
        }
    }

    suspend fun setSectionTextColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            it[textColorKey(sectionId)] = colorArgb
            if (applyToAll) it[GLOBAL_TEXT_COLOR_KEY] = colorArgb
        }
    }

    suspend fun clearSectionTextColor(sectionId: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            if (applyToAll) it[GLOBAL_TEXT_COLOR_KEY] = RESET_COLOR
            else it[textColorKey(sectionId)] = RESET_COLOR
        }
    }

    suspend fun setSectionBoxColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            it[boxColorKey(sectionId)] = colorArgb
            if (applyToAll) it[GLOBAL_BOX_COLOR_KEY] = colorArgb
        }
    }

    suspend fun clearSectionBoxColor(sectionId: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            if (applyToAll) it[GLOBAL_BOX_COLOR_KEY] = RESET_COLOR
            else it[boxColorKey(sectionId)] = RESET_COLOR
        }
    }

    suspend fun setSectionCompletionColor(sectionId: Long, colorArgb: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            it[completionColorKey(sectionId)] = colorArgb
            if (applyToAll) it[GLOBAL_COMPLETION_COLOR_KEY] = colorArgb
        }
    }

    suspend fun clearSectionCompletionColor(sectionId: Long, applyToAll: Boolean) {
        context.settingsDataStore.edit {
            if (applyToAll) it[GLOBAL_COMPLETION_COLOR_KEY] = RESET_COLOR
            else it[completionColorKey(sectionId)] = RESET_COLOR
        }
    }

    suspend fun setSectionTitleColor(sectionId: Long, colorArgb: Long) {
        context.settingsDataStore.edit { it[titleColorKey(sectionId)] = colorArgb }
    }

    suspend fun clearSectionTitleColor(sectionId: Long) {
        context.settingsDataStore.edit { it[titleColorKey(sectionId)] = RESET_COLOR }
    }

    private fun backgroundColorKey(sectionId: Long) = longPreferencesKey("section_${sectionId}_background_color")
    private fun textColorKey(sectionId: Long) = longPreferencesKey("section_${sectionId}_text_color")
    private fun boxColorKey(sectionId: Long) = longPreferencesKey("section_${sectionId}_box_color")
    private fun titleColorKey(sectionId: Long) = longPreferencesKey("section_${sectionId}_title_color")
    private fun completionColorKey(sectionId: Long) = longPreferencesKey("section_${sectionId}_completion_color")
    private fun zoomEnabledKey(sectionId: Long) = booleanPreferencesKey("section_${sectionId}_zoom_enabled")
    private fun zoomScaleKey(sectionId: Long) = floatPreferencesKey("section_${sectionId}_zoom_scale")
    private fun longPressPanKey(sectionId: Long) = booleanPreferencesKey("section_${sectionId}_long_press_pan")
    private fun canvasOffsetXKey(sectionId: Long) = floatPreferencesKey("section_${sectionId}_canvas_x")
    private fun canvasOffsetYKey(sectionId: Long) = floatPreferencesKey("section_${sectionId}_canvas_y")
}

private fun Long?.takeUsableColor(): Long? = this?.takeUnless { it == SettingsRepository.RESET_COLOR }
