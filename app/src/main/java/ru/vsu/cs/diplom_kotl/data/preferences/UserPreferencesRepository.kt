package ru.vsu.cs.diplom_kotl.data.preferences

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysis

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ROOM,
    ;

    companion object {
        fun fromKey(s: String?): AppThemeMode =
            entries.find { it.name == s } ?: SYSTEM
    }
}

class UserPreferencesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyRecLock = Any()

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

    private fun ensureLegacyRecMigration() {
        synchronized(legacyRecLock) {
            if (prefs.contains(KEY_RECOMMENDATIONS_ENABLED) && !prefs.contains(KEY_REC_STYLE)) {
                val v = prefs.getBoolean(KEY_RECOMMENDATIONS_ENABLED, true)
                prefs.edit {
                    putBoolean(KEY_REC_STYLE, v)
                    putBoolean(KEY_REC_PALETTE, v)
                    remove(KEY_RECOMMENDATIONS_ENABLED)
                }
            }
        }
    }

    fun isRecommendationStyleEnabled(): Boolean {
        ensureLegacyRecMigration()
        return prefs.getBoolean(KEY_REC_STYLE, true)
    }

    fun isRecommendationPaletteEnabled(): Boolean {
        ensureLegacyRecMigration()
        return prefs.getBoolean(KEY_REC_PALETTE, true)
    }

    fun setRecommendationStyleEnabled(enabled: Boolean) {
        ensureLegacyRecMigration()
        prefs.edit { putBoolean(KEY_REC_STYLE, enabled) }
        notifyChanged()
    }

    fun setRecommendationPaletteEnabled(enabled: Boolean) {
        ensureLegacyRecMigration()
        prefs.edit { putBoolean(KEY_REC_PALETTE, enabled) }
        notifyChanged()
    }

    /** Включена ли хотя бы одна персональная настройка (стиль или палитра). */
    fun isAnyPersonalizedRecommendationEnabled(): Boolean =
        isRecommendationStyleEnabled() || isRecommendationPaletteEnabled()

    fun getAppThemeMode(): AppThemeMode =
        AppThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))

    fun setAppThemeMode(mode: AppThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        notifyChanged()
    }

    fun getNightModeForDelegate(): Int = when (getAppThemeMode()) {
        AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        AppThemeMode.ROOM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun getRoomDominantColorsHex(): List<String> {
        val raw = prefs.getString(KEY_ROOM_COLORS_HEX, null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    }

    /** Основной цвет комнаты (для акцента в рекомендациях); должен быть одним из списка палитры. */
    fun getRoomPrimaryColorHex(): String? =
        prefs.getString(KEY_ROOM_PRIMARY_HEX, null)

    fun setRoomPrimaryColorHex(hex: String?) {
        prefs.edit {
            if (hex.isNullOrBlank()) remove(KEY_ROOM_PRIMARY_HEX)
            else putString(KEY_ROOM_PRIMARY_HEX, hex)
        }
        notifyChanged()
    }

    fun getRoomDominantColorInts(): List<Int> {
        val hexes = getRoomDominantColorsHex()
        val primary = getRoomPrimaryColorHex()
        val ordered = if (primary != null && hexes.any { it.equals(primary, ignoreCase = true) }) {
            val rest = hexes.filterNot { it.equals(primary, ignoreCase = true) }
            listOf(primary) + rest
        } else {
            hexes
        }
        return ordered.mapNotNull { hex ->
            runCatching { Color.parseColor(hex) }.getOrNull()
        }
    }

    fun getRoomBrightness(): Float =
        prefs.getFloat(KEY_ROOM_BRIGHTNESS, 0.35f)

    fun isRoomLowLight(): Boolean =
        prefs.getBoolean(KEY_ROOM_LOW_LIGHT, false)

    fun saveRoomAnalysis(analysis: RoomAnalysis) {
        val hexes = analysis.dominantColors.map { c ->
            String.format("#%06X", 0xFFFFFF and c)
        }
        val hexList = hexes.joinToString(",")
        val hadPrimary = prefs.getString(KEY_ROOM_PRIMARY_HEX, null)
        prefs.edit {
            putString(KEY_ROOM_COLORS_HEX, hexList)
            putFloat(KEY_ROOM_BRIGHTNESS, analysis.averageBrightness)
            putBoolean(KEY_ROOM_LOW_LIGHT, analysis.isLowLight)
            if (hadPrimary == null && hexes.isNotEmpty()) {
                putString(KEY_ROOM_PRIMARY_HEX, hexes.first())
            }
        }
        notifyChanged()
    }

    fun saveManualPalette(
        colorHexes: List<String>,
        primaryHex: String?,
        brightness: Float,
        isLowLight: Boolean,
    ) {
        val cleaned = colorHexes.map { it.trim() }.filter { it.isNotEmpty() }
        val primary = primaryHex?.trim()?.takeIf { p ->
            cleaned.any { it.equals(p, ignoreCase = true) }
        } ?: cleaned.firstOrNull()
        prefs.edit {
            putString(KEY_ROOM_COLORS_HEX, cleaned.joinToString(","))
            putFloat(KEY_ROOM_BRIGHTNESS, brightness)
            putBoolean(KEY_ROOM_LOW_LIGHT, isLowLight)
            if (primary != null) putString(KEY_ROOM_PRIMARY_HEX, primary)
            else remove(KEY_ROOM_PRIMARY_HEX)
        }
        notifyChanged()
    }

    fun clearRoomPalette() {
        prefs.edit {
            remove(KEY_ROOM_COLORS_HEX)
            remove(KEY_ROOM_BRIGHTNESS)
            remove(KEY_ROOM_LOW_LIGHT)
            remove(KEY_ROOM_PRIMARY_HEX)
        }
        notifyChanged()
    }

    companion object {
        private const val PREFS_NAME = "diplom_user_prefs_v1"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PREFERRED_STYLE = "preferred_style"
        private const val KEY_RECOMMENDATIONS_ENABLED = "rec_enabled"
        private const val KEY_REC_STYLE = "rec_style_enabled"
        private const val KEY_REC_PALETTE = "rec_palette_enabled"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_ROOM_COLORS_HEX = "room_colors_hex"
        private const val KEY_ROOM_BRIGHTNESS = "room_brightness"
        private const val KEY_ROOM_LOW_LIGHT = "room_low_light"
        private const val KEY_ROOM_PRIMARY_HEX = "room_primary_hex"
    }
}
