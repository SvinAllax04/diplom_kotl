package ru.vsu.cs.diplom_kotl.presentation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.domain.recommendation.FurnitureRecommendationEngine
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysis
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysisService

data class ArUiState(
    val selectedFurniture: FurnitureItem? = null,
    val roomDominantColor: Int? = null,
    val roomDominantColors: List<Int> = emptyList(),
    val averageBrightness: Float = 0f,
    val lowLightWarning: Boolean = false,
    val recommendationsEnabled: Boolean = true,
    val selectedStyle: InteriorStyle? = null,
    val recommendations: List<FurnitureItem> = emptyList(),
)

class ArViewModel(
    private val catalog: FurnitureCatalog,
    private val prefs: UserPreferencesRepository,
    private val roomAnalysisService: RoomAnalysisService = RoomAnalysisService(),
    private val recommendationEngine: FurnitureRecommendationEngine = FurnitureRecommendationEngine(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    private fun buildInitialState(): ArUiState {
        val prefsRoom = roomSnapshotFromPrefs()
        val style = prefs.getPreferredStyle()
        val enabled = prefs.isRecommendationsEnabled()
        val ranked = recommendationEngine.recommend(
            room = prefsRoom,
            items = catalog.all(),
            preferredStyle = style,
            recommendationsEnabled = enabled,
        )
        return ArUiState(
            selectedFurniture = ranked.firstOrNull(),
            roomDominantColor = prefsRoom.dominantColors.firstOrNull(),
            roomDominantColors = prefsRoom.dominantColors,
            averageBrightness = prefsRoom.averageBrightness,
            lowLightWarning = prefsRoom.isLowLight,
            recommendationsEnabled = enabled,
            selectedStyle = style,
            recommendations = ranked,
        )
    }

    private fun roomSnapshotFromPrefs(): RoomAnalysis {
        val colors = prefs.getRoomDominantColorInts()
        return RoomAnalysis(
            dominantColors = colors.ifEmpty { listOf(Color.GRAY) },
            averageBrightness = prefs.getRoomBrightness(),
            isLowLight = prefs.isRoomLowLight(),
            isLikelyEmpty = colors.isEmpty(),
        )
    }

    fun selectFurniture(item: FurnitureItem) {
        _uiState.value = _uiState.value.copy(selectedFurniture = item)
    }

    fun setStyle(style: InteriorStyle?) {
        prefs.setPreferredStyle(style)
        val old = _uiState.value
        val room = currentRoomAnalysis(old)
        val ranked = recommendationEngine.recommend(
            room = room,
            items = catalog.all(),
            preferredStyle = style,
            recommendationsEnabled = old.recommendationsEnabled,
        )
        _uiState.value = old.copy(
            selectedStyle = style,
            recommendations = ranked,
            selectedFurniture = ranked.firstOrNull() ?: old.selectedFurniture,
        )
    }

    fun setRecommendationsEnabled(enabled: Boolean) {
        prefs.setRecommendationsEnabled(enabled)
        val old = _uiState.value
        val room = currentRoomAnalysis(old)
        val ranked = recommendationEngine.recommend(
            room = room,
            items = catalog.all(),
            preferredStyle = old.selectedStyle,
            recommendationsEnabled = enabled,
        )
        _uiState.value = old.copy(
            recommendationsEnabled = enabled,
            recommendations = ranked,
            selectedFurniture = ranked.firstOrNull() ?: old.selectedFurniture,
        )
    }

    /**
     * После смены палитры комнаты в профиле (новый снимок).
     */
    fun applySavedRoomFromPrefs() {
        val analysis = roomSnapshotFromPrefs()
        val old = _uiState.value
        val ranked = recommendationEngine.recommend(
            room = analysis,
            items = catalog.all(),
            preferredStyle = old.selectedStyle,
            recommendationsEnabled = old.recommendationsEnabled,
        )
        _uiState.value = old.copy(
            roomDominantColor = analysis.dominantColors.firstOrNull(),
            roomDominantColors = analysis.dominantColors,
            averageBrightness = analysis.averageBrightness,
            lowLightWarning = analysis.isLowLight,
            recommendations = ranked,
            selectedFurniture = ranked.firstOrNull() ?: old.selectedFurniture,
        )
    }

    /**
     * Обновить только стиль и флаг рекомендаций из prefs; живые цвета с камеры сохраняются.
     */
    fun reloadPrefsWithoutResettingLiveColors() {
        val old = _uiState.value
        val style = prefs.getPreferredStyle()
        val enabled = prefs.isRecommendationsEnabled()
        val room = currentRoomAnalysis(old)
        val ranked = recommendationEngine.recommend(
            room = room,
            items = catalog.all(),
            preferredStyle = style,
            recommendationsEnabled = enabled,
        )
        _uiState.value = old.copy(
            selectedStyle = style,
            recommendationsEnabled = enabled,
            recommendations = ranked,
            selectedFurniture = ranked.firstOrNull() ?: old.selectedFurniture,
        )
    }

    private fun currentRoomAnalysis(old: ArUiState): RoomAnalysis {
        val dominant = old.roomDominantColors.ifEmpty {
            prefs.getRoomDominantColorInts().ifEmpty { listOf(Color.GRAY) }
        }
        val hasLive = old.roomDominantColors.isNotEmpty()
        return RoomAnalysis(
            dominantColors = dominant,
            averageBrightness = if (hasLive) old.averageBrightness else prefs.getRoomBrightness(),
            isLowLight = if (hasLive) old.lowLightWarning else prefs.isRoomLowLight(),
            isLikelyEmpty = prefs.getRoomDominantColorInts().isEmpty() && !hasLive,
        )
    }

    fun updateRoomColor(bitmap: Bitmap) {
        viewModelScope.launch {
            val analysis = roomAnalysisService.analyze(bitmap)
            val ranked = recommendationEngine.recommend(
                room = analysis,
                items = catalog.all(),
                preferredStyle = _uiState.value.selectedStyle,
                recommendationsEnabled = _uiState.value.recommendationsEnabled,
            )
            _uiState.value = _uiState.value.copy(
                roomDominantColor = analysis.dominantColors.firstOrNull(),
                roomDominantColors = analysis.dominantColors,
                averageBrightness = analysis.averageBrightness,
                lowLightWarning = analysis.isLowLight,
                recommendations = ranked,
                selectedFurniture = ranked.firstOrNull() ?: _uiState.value.selectedFurniture,
            )
        }
    }
}
