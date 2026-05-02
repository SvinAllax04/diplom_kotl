package ru.vsu.cs.diplom_kotl.data.catalog

import android.graphics.Color
import com.google.gson.annotations.SerializedName
import ru.vsu.cs.diplom_kotl.data.auth.UserRole

internal data class CatalogJsonDto(
    val id: String,
    val title: String,
    val category: String? = null,
    val assetPath: String,
    @SerializedName("thumbnailAssetPath") val thumbnailAssetPath: String? = null,
    @SerializedName("galleryAssetPaths") val galleryAssetPaths: List<String> = emptyList(),
    val previewColorHex: String,
    val style: String,
    val widthM: Float,
    val depthM: Float,
    val heightM: Float,
    val storeName: String,
    val priceRub: Double,
    val uploadedByRole: String = "STORE",
    val approved: Boolean = true,
) {
    fun toItem(): FurnitureItem {
        val role = runCatching { UserRole.valueOf(uploadedByRole.uppercase()) }.getOrDefault(UserRole.STORE)
        val interiorStyle = runCatching { InteriorStyle.valueOf(style.uppercase()) }.getOrDefault(InteriorStyle.MODERN)
        val color = runCatching { Color.parseColor(previewColorHex) }.getOrDefault(Color.GRAY)
        val cat = FurnitureCategory.fromJsonOrInfer(category, title)
        return FurnitureItem(
            id = id,
            title = title,
            category = cat,
            assetPath = assetPath,
            thumbnailAssetPath = thumbnailAssetPath?.takeIf { it.isNotBlank() },
            galleryAssetPaths = galleryAssetPaths.filter { it.isNotBlank() },
            previewColor = color,
            style = interiorStyle,
            widthM = widthM,
            depthM = depthM,
            heightM = heightM,
            storeName = storeName,
            priceRub = priceRub,
            uploadedByRole = role,
            approved = approved,
        )
    }
}
