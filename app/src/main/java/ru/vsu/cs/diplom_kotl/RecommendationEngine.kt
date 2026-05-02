package ru.vsu.cs.diplom_kotl.domain.recommendation

import android.graphics.Bitmap
import android.graphics.Color
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import kotlin.math.abs

enum class RecommendationReason {
    STYLE_MATCH,
    PALETTE_MATCH,
    SIMILAR_HUE,
    LOW_LIGHT_OPTIMAL,
    CATALOG_BROWSE,
}

data class RecommendedFurniture(
    val item: FurnitureItem,
    val reasons: List<RecommendationReason>,
)

data class RoomAnalysis(
    val dominantColors: List<Int>,
    val averageBrightness: Float,
    val isLowLight: Boolean,
    val isLikelyEmpty: Boolean
)

class RoomAnalysisService {
    fun analyze(bitmap: Bitmap, sampleStep: Int = 10, topColors: Int = 3): RoomAnalysis {
        require(sampleStep > 0) { "sampleStep must be > 0" }

        val histogram = HashMap<Int, Int>(512)
        var brightnessAcc = 0f
        var satAcc = 0f
        var samples = 0
        val hsv = FloatArray(3)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                brightnessAcc += hsv[2]
                satAcc += hsv[1]
                histogram[quantize(pixel)] = (histogram[quantize(pixel)] ?: 0) + 1
                samples++
                x += sampleStep
            }
            y += sampleStep
        }

        val dominant = histogram.entries
            .sortedByDescending { it.value }
            .take(topColors.coerceAtLeast(1))
            .map { dequantize(it.key) }
            .ifEmpty { listOf(Color.GRAY) }

        val avgBrightness = if (samples == 0) 0f else brightnessAcc / samples
        val avgSat = if (samples == 0) 0f else satAcc / samples

        return RoomAnalysis(
            dominantColors = dominant,
            averageBrightness = avgBrightness,
            isLowLight = avgBrightness < 0.25f,
            isLikelyEmpty = avgSat < 0.08f
        )
    }

    private fun quantize(color: Int): Int {
        val r = Color.red(color) / 32
        val g = Color.green(color) / 32
        val b = Color.blue(color) / 32
        return (r shl 6) or (g shl 3) or b
    }

    private fun dequantize(bucket: Int): Int {
        val r = ((bucket shr 6) and 0x07) * 32 + 16
        val g = ((bucket shr 3) and 0x07) * 32 + 16
        val b = (bucket and 0x07) * 32 + 16
        return Color.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
    }
}

class FurnitureRecommendationEngine {
    fun recommend(
        room: RoomAnalysis,
        items: List<FurnitureItem>,
        preferredStyle: InteriorStyle?,
        recommendationsEnabled: Boolean
    ): List<FurnitureItem> {
        return recommendDetailed(room, items, preferredStyle, recommendationsEnabled).map { it.item }
    }

    fun recommendDetailed(
        room: RoomAnalysis,
        items: List<FurnitureItem>,
        preferredStyle: InteriorStyle?,
        recommendationsEnabled: Boolean
    ): List<RecommendedFurniture> {
        if (!recommendationsEnabled) {
            return items.map { RecommendedFurniture(it, listOf(RecommendationReason.CATALOG_BROWSE)) }
        }

        val candidates = if (preferredStyle != null) {
            items.filter { it.style == preferredStyle }
        } else {
            items
        }

        val base = if (room.isLikelyEmpty && preferredStyle != null && candidates.isNotEmpty()) {
            candidates
        } else {
            if (candidates.isNotEmpty()) candidates else items
        }

        val sorted = base.sortedByDescending { item ->
            val colorScore = bestColorScore(room.dominantColors, item.previewColor)
            val styleScore = if (preferredStyle == null || item.style == preferredStyle) 1.0 else 0.65
            val lightPenalty = if (room.isLowLight && isVeryDark(item.previewColor)) 0.7 else 1.0
            colorScore * styleScore * lightPenalty
        }

        return sorted.map { item ->
            val reasons = buildList {
                if (preferredStyle != null && item.style == preferredStyle) {
                    add(RecommendationReason.STYLE_MATCH)
                }
                val colorScore = bestColorScore(room.dominantColors, item.previewColor)
                when {
                    colorScore >= 0.55 -> add(RecommendationReason.PALETTE_MATCH)
                    colorScore >= 0.35 -> add(RecommendationReason.SIMILAR_HUE)
                }
                if (room.isLowLight && !isVeryDark(item.previewColor)) {
                    add(RecommendationReason.LOW_LIGHT_OPTIMAL)
                }
                if (isEmpty()) add(RecommendationReason.CATALOG_BROWSE)
            }
            RecommendedFurniture(item = item, reasons = reasons.distinct())
        }
    }

    private fun bestColorScore(roomColors: List<Int>, furnitureColor: Int): Double {
        return roomColors.maxOfOrNull { hsvSimilarity(it, furnitureColor) } ?: 0.0
    }

    private fun hsvSimilarity(a: Int, b: Int): Double {
        val ahsv = FloatArray(3)
        val bhsv = FloatArray(3)
        Color.colorToHSV(a, ahsv)
        Color.colorToHSV(b, bhsv)

        val hueDelta = minHueDistance(ahsv[0], bhsv[0]) / 180f
        val satDelta = abs(ahsv[1] - bhsv[1])
        val valueDelta = abs(ahsv[2] - bhsv[2])
        val distance = (0.55f * hueDelta) + (0.25f * satDelta) + (0.20f * valueDelta)
        return (1f - distance.coerceIn(0f, 1f)).toDouble()
    }

    private fun minHueDistance(a: Float, b: Float): Float {
        val delta = abs(a - b)
        return minOf(delta, 360f - delta)
    }

    private fun isVeryDark(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2] < 0.20f
    }
}
