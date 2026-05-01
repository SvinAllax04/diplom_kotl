package ru.vsu.cs.diplom_kotl.presentation

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.domain.recommendation.FurnitureRecommendationEngine
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysis

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferencesRepository(application)
    private val catalog = FurnitureCatalog(application)
    private val engine = FurnitureRecommendationEngine()

    private val _items = MutableStateFlow<List<FurnitureItem>>(emptyList())
    val items: StateFlow<List<FurnitureItem>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val colors = prefs.getRoomDominantColorInts()
        val analysis = RoomAnalysis(
            dominantColors = colors.ifEmpty { listOf(Color.GRAY) },
            averageBrightness = prefs.getRoomBrightness(),
            isLowLight = prefs.isRoomLowLight(),
            isLikelyEmpty = colors.isEmpty(),
        )
        _items.value = engine.recommend(
            room = analysis,
            items = catalog.all(),
            preferredStyle = prefs.getPreferredStyle(),
            recommendationsEnabled = prefs.isRecommendationsEnabled(),
        )
    }
}
