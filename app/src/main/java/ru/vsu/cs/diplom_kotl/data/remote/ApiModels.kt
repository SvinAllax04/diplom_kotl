package ru.vsu.cs.diplom_kotl.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class FurnitureModelDto(
    val id: String,
    val title: String,
    val modelUrl: String,
    val previewColorHex: String,
    val style: String,
    val widthM: Float,
    val depthM: Float,
    val heightM: Float,
    val approved: Boolean
)

@Serializable
data class RecommendationRequestDto(
    val role: String,
    val dominantColorsHex: List<String>,
    val averageBrightness: Float,
    val lowLight: Boolean,
    val preferredStyle: String?,
    val recommendationsEnabled: Boolean,
    val roomWidthM: Float?,
    val roomDepthM: Float?,
    val freeAreaM2: Float?
)

@Serializable
data class RecommendationResponseDto(
    val warning: String? = null,
    val items: List<FurnitureModelDto> = emptyList()
)

@Serializable
data class UploadModelRequestDto(
    val title: String,
    val modelUrl: String,
    val previewColorHex: String,
    val style: String,
    val widthM: Float,
    val depthM: Float,
    val heightM: Float
)
