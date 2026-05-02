package ru.vsu.cs.diplom_kotl.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.presentation.HomeRecommendationRow
import java.text.NumberFormat
import java.util.Locale

class RecommendationCardAdapter(
    private val rows: MutableList<HomeRecommendationRow> = mutableListOf(),
    private val onItemClick: (FurnitureItem) -> Unit,
) : RecyclerView.Adapter<RecommendationCardAdapter.CardVH>() {

    fun submit(list: List<HomeRecommendationRow>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_card, parent, false)
        return CardVH(v)
    }

    override fun onBindViewHolder(holder: CardVH, position: Int) {
        val row = rows[position]
        holder.bind(row)
        holder.itemView.setOnClickListener { onItemClick(row.item) }
    }

    override fun getItemCount(): Int = rows.size

    class CardVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photo = itemView.findViewById<ImageView>(R.id.cardPhoto)
        private val dot = itemView.findViewById<View>(R.id.cardColorDot)
        private val title = itemView.findViewById<TextView>(R.id.cardTitle)
        private val colorHex = itemView.findViewById<TextView>(R.id.cardColorHex)
        private val style = itemView.findViewById<TextView>(R.id.cardStyle)
        private val dimensions = itemView.findViewById<TextView>(R.id.cardDimensions)
        private val store = itemView.findViewById<TextView>(R.id.cardStore)
        private val price = itemView.findViewById<TextView>(R.id.cardPrice)
        private val reason = itemView.findViewById<TextView>(R.id.cardRecommendReason)

        fun bind(row: HomeRecommendationRow) {
            val item = row.item
            title.text = item.title
            val hex = String.format("#%06X", 0xFFFFFF and item.previewColor)
            colorHex.text = itemView.context.getString(R.string.card_color_label, hex)
            style.text = itemView.context.getString(R.string.card_style_label, styleRu(item.style, itemView.context))
            dimensions.text = itemView.context.getString(
                R.string.card_dimensions_m,
                item.widthM,
                item.depthM,
                item.heightM,
            )
            store.text = itemView.context.getString(R.string.card_store_label, item.storeName)
            price.text = formatRub(item.priceRub)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.previewColor)
            }
            val path = item.thumbnailAssetPath ?: item.galleryAssetPaths.firstOrNull()
            if (!path.isNullOrBlank()) {
                photo.load("file:///android_asset/$path") {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)
                }
            } else {
                photo.setImageDrawable(null)
                photo.setBackgroundColor(item.previewColor)
            }
            reason.text = row.reasonLine
            reason.visibility = if (row.reasonLine.isBlank()) View.GONE else View.VISIBLE
        }

        private fun styleRu(s: InteriorStyle, ctx: android.content.Context): String {
            val res = when (s) {
                InteriorStyle.MODERN -> R.string.style_modern
                InteriorStyle.SCANDI -> R.string.style_scandi
                InteriorStyle.LOFT -> R.string.style_loft
                InteriorStyle.CLASSIC -> R.string.style_classic
            }
            return ctx.getString(res)
        }

        private fun formatRub(value: Double): String {
            val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
            return itemView.context.getString(R.string.card_price_value, nf.format(value.toLong()))
        }
    }
}
