package ru.vsu.cs.diplom_kotl.ui.catalog

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
import java.text.NumberFormat
import java.util.Locale

class FurnitureCatalogAdapter(
    private val onSelected: (FurnitureItem) -> Unit,
) : RecyclerView.Adapter<FurnitureCatalogAdapter.FurnitureViewHolder>() {

    private var items: List<FurnitureItem> = emptyList()
    private var selectedId: String? = null

    fun submit(list: List<FurnitureItem>) {
        items = list
        if (selectedId == null) {
            selectedId = list.firstOrNull()?.id
        }
        notifyDataSetChanged()
    }

    fun selectedItem(): FurnitureItem? = items.firstOrNull { it.id == selectedId }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FurnitureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_furniture, parent, false)
        return FurnitureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FurnitureViewHolder, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId
        holder.bind(item, isSelected)
        holder.itemView.setOnClickListener {
            selectedId = item.id
            notifyDataSetChanged()
            onSelected(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class FurnitureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail = itemView.findViewById<ImageView>(R.id.itemThumbnail)
        private val title = itemView.findViewById<TextView>(R.id.itemTitle)
        private val subtitle = itemView.findViewById<TextView>(R.id.itemSubtitle)
        private val storePrice = itemView.findViewById<TextView>(R.id.itemStorePrice)
        private val colorDot = itemView.findViewById<View>(R.id.itemColorDot)

        fun bind(item: FurnitureItem, selected: Boolean) {
            title.text = item.title
            subtitle.text = "${item.style} • ${item.widthM} × ${item.depthM} × ${item.heightM} м"
            val nf = NumberFormat.getNumberInstance(Locale("ru", "RU"))
            storePrice.text = itemView.context.getString(
                R.string.ar_catalog_store_price,
                item.storeName,
                nf.format(item.priceRub.toLong()),
            )
            colorDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.previewColor)
            }
            val path = item.thumbnailAssetPath ?: item.galleryAssetPaths.firstOrNull()
            if (!path.isNullOrBlank()) {
                thumbnail.load("file:///android_asset/$path") {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                }
            } else {
                thumbnail.setImageDrawable(null)
                thumbnail.setBackgroundColor(item.previewColor)
            }
            itemView.alpha = if (selected) 1f else 0.78f
        }
    }
}
