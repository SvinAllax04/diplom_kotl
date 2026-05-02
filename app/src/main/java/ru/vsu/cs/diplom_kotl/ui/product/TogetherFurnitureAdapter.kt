package ru.vsu.cs.diplom_kotl.ui.product

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem
import java.text.NumberFormat
import java.util.Locale

class TogetherFurnitureAdapter(
    private val items: List<FurnitureItem>,
    private val onClick: (FurnitureItem) -> Unit,
) : RecyclerView.Adapter<TogetherFurnitureAdapter.TogetherVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TogetherVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_together_furniture, parent, false)
        return TogetherVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TogetherVH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class TogetherVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image = itemView.findViewById<ImageView>(R.id.togetherImage)
        private val title = itemView.findViewById<TextView>(R.id.togetherTitle)
        private val price = itemView.findViewById<TextView>(R.id.togetherPrice)

        fun bind(item: FurnitureItem, onClick: (FurnitureItem) -> Unit) {
            title.text = item.title
            val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
            price.text = itemView.context.getString(R.string.card_price_value, nf.format(item.priceRub.toLong()))
            val path = item.thumbnailAssetPath ?: item.galleryAssetPaths.firstOrNull()
            if (!path.isNullOrBlank()) {
                image.load("file:///android_asset/$path") {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)
                }
            } else {
                image.setImageDrawable(null)
                image.setBackgroundColor(item.previewColor)
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
