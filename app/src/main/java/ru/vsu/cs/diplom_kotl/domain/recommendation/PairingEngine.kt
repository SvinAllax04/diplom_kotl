package ru.vsu.cs.diplom_kotl.domain.recommendation

import android.graphics.Color
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import kotlin.math.abs

class PairingEngine {
    fun pickTogether(base: FurnitureItem, all: List<FurnitureItem>, limit: Int = 6): List<FurnitureItem> {
        return all.asSequence()
            .filter { it.id != base.id }
            .sortedByDescending { score(base, it) }
            .take(limit)
            .toList()
    }

    private fun score(base: FurnitureItem, candidate: FurnitureItem): Double {
        val styleScore = if (base.style == candidate.style) 1.0 else 0.65
        val colorScore = hsvSimilarity(base.previewColor, candidate.previewColor)
        return (0.6 * styleScore) + (0.4 * colorScore)
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
}
