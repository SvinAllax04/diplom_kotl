package ru.vsu.cs.diplom_kotl.data.scene

import kotlinx.serialization.Serializable

@Serializable
data class SceneState(
    val objects: List<PlacedObjectState> = emptyList()
)

@Serializable
data class PlacedObjectState(
    val assetPath: String,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val scale: Float,
    val rotationY: Float
)
