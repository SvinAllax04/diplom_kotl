package ru.vsu.cs.diplom_kotl.data.preferences

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysis

class UserPreferencesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    private fun notifyChanged() {
        _updates.tryEmit(Unit)
    }

    /** По умолчанию true — чтобы при обновлении приложения не блокировать уже работавших пользователей. */
    fun isOnboardingComplete(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, true)

    fun setOnboardingComplete(done: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, done) }
        notifyChanged()
    }

    fun getPreferredStyle(): InteriorStyle? =
        prefs.getString(KEY_PREFERRED_STYLE, null)?.let { name ->
            runCatching { InteriorStyle.valueOf(name) }.getOrNull()
        }

    fun setPreferredStyle(style: InteriorStyle?) {
        prefs.edit {
            if (style == null) remove(KEY_PREFERRED_STYLE)
            else putString(KEY_PREFERRED_STYLE, style.name)
        }
        notifyChanged()
    }

    fun isRecommendationsEnabled(): Boolean =
        prefs.getBoolean(KEY_RECOMMENDATIONS_ENABLED, true)

    fun setRecommendationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RECOMMENDATIONS_ENABLED, enabled) }
        notifyChanged()
    }

    fun getRoomDominantColorsHex(): List<String> {
        val raw = prefs.getString(KEY_ROOM_COLORS_HEX, null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    }

    fun getRoomDominantColorInts(): List<Int> =
        getRoomDominantColorsHex().mapNotNull { hex ->
            runCatching { Color.parseColor(hex) }.getOrNull()
        }

    fun getRoomBrightness(): Float =
        prefs.getFloat(KEY_ROOM_BRIGHTNESS, 0.35f)

    fun isRoomLowLight(): Boolean =
        prefs.getBoolean(KEY_ROOM_LOW_LIGHT, false)

    fun saveRoomAnalysis(analysis: RoomAnalysis) {
        val hexList = analysis.dominantColors.joinToString(",") { c ->
            String.format("#%06X", 0xFFFFFF and c)
        }
        prefs.edit {
            putString(KEY_ROOM_COLORS_HEX, hexList)
            putFloat(KEY_ROOM_BRIGHTNESS, analysis.averageBrightness)
            putBoolean(KEY_ROOM_LOW_LIGHT, analysis.isLowLight)
        }
        notifyChanged()
    }

    fun clearRoomPalette() {
        prefs.edit {
            remove(KEY_ROOM_COLORS_HEX)
            remove(KEY_ROOM_BRIGHTNESS)
            remove(KEY_ROOM_LOW_LIGHT)
        }
        notifyChanged()
    }

    companion object {
        private const val PREFS_NAME = "diplom_user_prefs_v1"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PREFERRED_STYLE = "preferred_style"
        private const val KEY_RECOMMENDATIONS_ENABLED = "rec_enabled"
        private const val KEY_ROOM_COLORS_HEX = "room_colors_hex"
        private const val KEY_ROOM_BRIGHTNESS = "room_brightness"
        private const val KEY_ROOM_LOW_LIGHT = "room_low_light"
    }
}
