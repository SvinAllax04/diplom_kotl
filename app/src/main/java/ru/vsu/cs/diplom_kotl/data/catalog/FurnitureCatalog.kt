package ru.vsu.cs.diplom_kotl.data.catalog

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.vsu.cs.diplom_kotl.data.auth.UserRole

data class FurnitureItem(
    val id: String,
    val title: String,
    val category: FurnitureCategory,
    val assetPath: String,
    /** Путь относительно корня assets, например `catalog/images/chair.jpg` */
    val thumbnailAssetPath: String? = null,
    /** Галерея фото (пути в assets), первый элемент считается главным фото. */
    val galleryAssetPaths: List<String> = emptyList(),
    val previewColor: Int,
    val style: InteriorStyle,
    val widthM: Float,
    val depthM: Float,
    val heightM: Float,
    val storeName: String,
    val priceRub: Double,
    val uploadedByRole: UserRole = UserRole.STORE,
    val approved: Boolean = true,
)

enum class InteriorStyle {
    MODERN,
    SCANDI,
    LOFT,
    CLASSIC,
}

/**
 * Каталог из `assets/catalog/catalog.json`.
 * Положите `.glb` в `assets/catalog/models/` (или укажите свой путь в JSON),
 * превью — в `assets/catalog/images/` и задайте `thumbnailAssetPath`.
 */
class FurnitureCatalog(private val context: Context) {

    fun all(): List<FurnitureItem> {
        synchronized(lock) {
            cached?.let { return it }
            val loaded = loadFromAssetsOrFallback()
            cached = loaded
            return loaded
        }
    }

    fun byId(id: String): FurnitureItem? = all().firstOrNull { it.id == id }

    companion object {
        private const val CATALOG_ASSET = "catalog/catalog.json"
        private val lock = Any()
        @Volatile private var cached: List<FurnitureItem>? = null

        fun invalidateCache() {
            synchronized(lock) {
                cached = null
            }
        }
    }

    private fun loadFromAssetsOrFallback(): List<FurnitureItem> {
        return try {
            context.assets.open(CATALOG_ASSET).bufferedReader().use { reader ->
                val type = object : TypeToken<List<CatalogJsonDto>>() {}.type
                Gson().fromJson<List<CatalogJsonDto>>(reader, type).map { it.toItem() }
            }
        } catch (_: Throwable) {
            builtinFallback()
        }
    }

    private fun builtinFallback(): List<FurnitureItem> {
        return listOf(
            FurnitureItem(
                id = "chair_beige",
                title = "Кресло бежевое",
                category = FurnitureCategory.inferFromTitle("Кресло бежевое"),
                assetPath = "catalog/models/chair.glb",
                thumbnailAssetPath = null,
                galleryAssetPaths = emptyList(),
                previewColor = Color.parseColor("#BFA98A"),
                style = InteriorStyle.SCANDI,
                widthM = 0.7f,
                depthM = 0.7f,
                heightM = 1.0f,
                storeName = "Икея Лофт",
                priceRub = 15_990.0,
                uploadedByRole = UserRole.ADMIN,
                approved = true,
            ),
            FurnitureItem(
                id = "sofa_gray",
                title = "Диван серый",
                category = FurnitureCategory.inferFromTitle("Диван серый"),
                assetPath = "catalog/models/chair.glb",
                thumbnailAssetPath = null,
                galleryAssetPaths = emptyList(),
                previewColor = Color.parseColor("#8D8F94"),
                style = InteriorStyle.MODERN,
                widthM = 2.1f,
                depthM = 0.95f,
                heightM = 0.9f,
                storeName = "WoodCraft",
                priceRub = 89_500.0,
                uploadedByRole = UserRole.STORE,
                approved = true,
            ),
            FurnitureItem(
                id = "table_oak",
                title = "Стол дубовый",
                category = FurnitureCategory.inferFromTitle("Стол дубовый"),
                assetPath = "catalog/models/chair.glb",
                thumbnailAssetPath = null,
                galleryAssetPaths = emptyList(),
                previewColor = Color.parseColor("#A7794A"),
                style = InteriorStyle.LOFT,
                widthM = 1.4f,
                depthM = 0.8f,
                heightM = 0.75f,
                storeName = "Мебельный двор",
                priceRub = 42_300.0,
                uploadedByRole = UserRole.STORE,
                approved = true,
            ),
            FurnitureItem(
                id = "shelf_white",
                title = "Стеллаж белый",
                category = FurnitureCategory.inferFromTitle("Стеллаж белый"),
                assetPath = "catalog/models/chair.glb",
                thumbnailAssetPath = null,
                galleryAssetPaths = emptyList(),
                previewColor = Color.parseColor("#E7E7E5"),
                style = InteriorStyle.CLASSIC,
                widthM = 0.9f,
                depthM = 0.35f,
                heightM = 1.8f,
                storeName = "ClassicHome",
                priceRub = 21_150.0,
                uploadedByRole = UserRole.ADMIN,
                approved = true,
            ),
        )
    }
}
