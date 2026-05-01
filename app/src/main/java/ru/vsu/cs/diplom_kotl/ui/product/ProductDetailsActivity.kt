package ru.vsu.cs.diplom_kotl.ui.product

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.domain.recommendation.PairingEngine
import ru.vsu.cs.diplom_kotl.ui.main.MainShellActivity
import java.text.NumberFormat
import java.util.Locale

class ProductDetailsActivity : AppCompatActivity() {
    private val catalog by lazy { FurnitureCatalog(this) }
    private val pairing by lazy { PairingEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_details)

        val id = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        val item = catalog.byId(id) ?: run {
            finish()
            return
        }
        bindItem(item)
    }

    private fun bindItem(item: FurnitureItem) {
        findViewById<TextView>(R.id.productTitle).text = item.title
        val hex = String.format("#%06X", 0xFFFFFF and item.previewColor)
        findViewById<TextView>(R.id.productColor).text = getString(R.string.card_color_label, hex)
        findViewById<TextView>(R.id.productStyle).text = getString(R.string.card_style_label, styleRu(item.style))
        findViewById<TextView>(R.id.productDimensions).text = getString(
            R.string.card_dimensions_m,
            item.widthM,
            item.depthM,
            item.heightM,
        )
        findViewById<TextView>(R.id.productStore).text = getString(R.string.card_store_label, item.storeName)
        val nf = NumberFormat.getNumberInstance(Locale("ru", "RU"))
        findViewById<TextView>(R.id.productPrice).text = getString(
            R.string.card_price_value,
            nf.format(item.priceRub.toLong()),
        )

        val photos = if (item.galleryAssetPaths.isNotEmpty()) {
            item.galleryAssetPaths
        } else {
            item.thumbnailAssetPath?.let { listOf(it) } ?: emptyList()
        }
        val pager = findViewById<ViewPager2>(R.id.productGalleryPager)
        val counter = findViewById<TextView>(R.id.productGalleryCounter)
        val pageCount = photos.size.coerceAtLeast(1)
        counter.text = getString(R.string.product_gallery_counter, 1, pageCount)
        pager.adapter = ProductGalleryAdapter(photos, item.previewColor)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                counter.text = getString(R.string.product_gallery_counter, position + 1, pageCount)
            }
        })

        findViewById<MaterialButton>(R.id.productTryInArButton).setOnClickListener {
            startActivity(
                Intent(this, MainShellActivity::class.java).apply {
                    putExtra(MainShellActivity.EXTRA_START_TAB, MainShellActivity.TAB_AR)
                    putExtra(MainShellActivity.EXTRA_AR_ITEM_ID, item.id)
                },
            )
        }

        val togetherItems = pairing.pickTogether(item, catalog.all(), limit = 8)
        val togetherRecycler = findViewById<RecyclerView>(R.id.productTogetherRecycler)
        togetherRecycler.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        togetherRecycler.adapter = TogetherFurnitureAdapter(togetherItems) { selected ->
            startActivity(
                Intent(this, ProductDetailsActivity::class.java).apply {
                    putExtra(EXTRA_ITEM_ID, selected.id)
                },
            )
        }
    }

    private fun styleRu(s: InteriorStyle): String {
        val res = when (s) {
            InteriorStyle.MODERN -> R.string.style_modern
            InteriorStyle.SCANDI -> R.string.style_scandi
            InteriorStyle.LOFT -> R.string.style_loft
            InteriorStyle.CLASSIC -> R.string.style_classic
        }
        return getString(res)
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
