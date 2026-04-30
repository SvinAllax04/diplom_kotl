package ru.vsu.cs.diplom_kotl.data.catalog

import android.graphics.Color
import ru.vsu.cs.diplom_kotl.data.auth.UserRole

data class FurnitureItem(
    val id: String,
    val title: String,
    val assetPath: String,
    val previewColor: Int,
    val style: InteriorStyle,
    val widthM: Float,
    val depthM: Float,
    val heightM: Float,
    val uploadedByRole: UserRole = UserRole.STORE,
    val approved: Boolean = true
)

enum class InteriorStyle {
    MODERN,
    SCANDI,
    LOFT,
    CLASSIC
}

class FurnitureCatalog {
    private val items = listOf(
        FurnitureItem(
            id = "chair_beige",
            title = "Beige Chair",
            assetPath = "models/chair.glb",
            previewColor = Color.parseColor("#BFA98A"),
            style = InteriorStyle.SCANDI,
            widthM = 0.7f,
            depthM = 0.7f,
            heightM = 1.0f,
            uploadedByRole = UserRole.ADMIN,
            approved = true
        ),
        FurnitureItem(
            id = "sofa_gray",
            title = "Gray Sofa",
            assetPath = "models/chair.glb",
            previewColor = Color.parseColor("#8D8F94"),
            style = InteriorStyle.MODERN,
            widthM = 2.1f,
            depthM = 0.95f,
            heightM = 0.9f,
            uploadedByRole = UserRole.STORE,
            approved = true
        ),
        FurnitureItem(
            id = "table_oak",
            title = "Oak Table",
            assetPath = "models/chair.glb",
            previewColor = Color.parseColor("#A7794A"),
            style = InteriorStyle.LOFT,
            widthM = 1.4f,
            depthM = 0.8f,
            heightM = 0.75f,
            uploadedByRole = UserRole.STORE,
            approved = true
        ),
        FurnitureItem(
            id = "shelf_white",
            title = "White Shelf",
            assetPath = "models/chair.glb",
            previewColor = Color.parseColor("#E7E7E5"),
            style = InteriorStyle.CLASSIC,
            widthM = 0.9f,
            depthM = 0.35f,
            heightM = 1.8f,
            uploadedByRole = UserRole.ADMIN,
            approved = true
        )
    )

    fun all(): List<FurnitureItem> = items
}
