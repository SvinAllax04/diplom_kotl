package ru.vsu.cs.diplom_kotl.presentation

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.domain.recommendation.FurnitureRecommendationEngine
import ru.vsu.cs.diplom_kotl.domain.recommendation.RecommendationReason
import ru.vsu.cs.diplom_kotl.domain.recommendation.RecommendedFurniture
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysis

data class HomeRecommendationRow(
    val item: FurnitureItem,
    val reasonLine: String,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferencesRepository(application)
    private val catalog = FurnitureCatalog(application)
    private val engine = FurnitureRecommendationEngine()

    private val _rows = MutableStateFlow<List<HomeRecommendationRow>>(emptyList())
    val rows: StateFlow<List<HomeRecommendationRow>> = _rows.asStateFlow()

    /** Режим: персональные рекомендации или обычный каталог. */
    private val _personalizedActive = MutableStateFlow(true)
    val personalizedActive: StateFlow<Boolean> = _personalizedActive.asStateFlow()

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
        val useStyle = prefs.isRecommendationStyleEnabled()
        val usePalette = prefs.isRecommendationPaletteEnabled()
        _personalizedActive.value = prefs.isAnyPersonalizedRecommendationEnabled()
        val detailed = engine.recommendDetailed(
            room = analysis,
            items = catalog.all(),
            preferredStyle = prefs.getPreferredStyle(),
            useStyle = useStyle,
            usePalette = usePalette,
        )
        val ctx = getApplication<Application>()
        _rows.value = detailed.map { rf ->
            HomeRecommendationRow(
                item = rf.item,
                reasonLine = formatReasons(ctx, rf),
            )
        }
    }

    private fun formatReasons(ctx: Application, rf: RecommendedFurniture): String {
        val parts = rf.reasons.distinct().map { reason ->
            when (reason) {
                RecommendationReason.STYLE_MATCH -> ctx.getString(R.string.rec_reason_style)
                RecommendationReason.PALETTE_MATCH -> ctx.getString(R.string.rec_reason_palette)
                RecommendationReason.SIMILAR_HUE -> ctx.getString(R.string.rec_reason_hue)
                RecommendationReason.LOW_LIGHT_OPTIMAL -> ctx.getString(R.string.rec_reason_low_light)
                RecommendationReason.CATALOG_BROWSE -> ctx.getString(R.string.rec_reason_catalog)
            }
        }
        return parts.distinct().joinToString(" · ")
    }
}
