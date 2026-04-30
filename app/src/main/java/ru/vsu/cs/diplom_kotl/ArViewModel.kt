package ru.vsu.cs.diplom_kotl.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
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
    val recommendations: List<FurnitureItem> = emptyList()
)

class ArViewModel(
    private val catalog: FurnitureCatalog = FurnitureCatalog(),
    private val roomAnalysisService: RoomAnalysisService = RoomAnalysisService(),
    private val recommendationEngine: FurnitureRecommendationEngine = FurnitureRecommendationEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ArUiState(
            selectedFurniture = catalog.all().firstOrNull(),
            recommendations = catalog.all()
        )
    )
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    fun selectFurniture(item: FurnitureItem) {
        _uiState.value = _uiState.value.copy(selectedFurniture = item)
    }

    fun setStyle(style: InteriorStyle?) {
        val old = _uiState.value
        val ranked = recommendationEngine.recommend(
            room = RoomAnalysis(
                dominantColors = old.roomDominantColors.ifEmpty { listOf(old.roomDominantColor ?: 0xFF808080.toInt()) },
                averageBrightness = old.averageBrightness,
                isLowLight = old.lowLightWarning,
                isLikelyEmpty = false
            ),
            items = catalog.all(),
            preferredStyle = style,
            recommendationsEnabled = old.recommendationsEnabled
        )
        _uiState.value = old.copy(
            selectedStyle = style,
            recommendations = ranked,
            selectedFurniture = ranked.firstOrNull()
        )
    }

    fun setRecommendationsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(recommendationsEnabled = enabled)
    }

    fun updateRoomColor(bitmap: Bitmap) {
        viewModelScope.launch {
            val analysis = roomAnalysisService.analyze(bitmap)
            val ranked = recommendationEngine.recommend(
                room = analysis,
                items = catalog.all(),
                preferredStyle = _uiState.value.selectedStyle,
                recommendationsEnabled = _uiState.value.recommendationsEnabled
            )
            _uiState.value = _uiState.value.copy(
                roomDominantColor = analysis.dominantColors.firstOrNull(),
                roomDominantColors = analysis.dominantColors,
                averageBrightness = analysis.averageBrightness,
                lowLightWarning = analysis.isLowLight,
                recommendations = ranked,
                selectedFurniture = ranked.firstOrNull()
            )
        }
    }
}
