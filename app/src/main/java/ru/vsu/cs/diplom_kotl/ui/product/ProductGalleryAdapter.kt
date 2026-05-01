package ru.vsu.cs.diplom_kotl.ui.product

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.vsu.cs.diplom_kotl.R

class ProductGalleryAdapter(
    private val photos: List<String>,
    private val fallbackColor: Int,
) : RecyclerView.Adapter<ProductGalleryAdapter.GalleryVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_gallery_photo, parent, false) as ViewGroup
        return GalleryVH(view)
    }

    override fun getItemCount(): Int = photos.size.coerceAtLeast(1)

    override fun onBindViewHolder(holder: GalleryVH, position: Int) {
        if (photos.isEmpty()) {
            holder.image.setImageDrawable(null)
            holder.image.setBackgroundColor(fallbackColor)
            return
        }
        holder.image.load("file:///android_asset/${photos[position]}") {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }
    }

    class GalleryVH(container: ViewGroup) : RecyclerView.ViewHolder(container) {
        val image: ImageView = container.findViewById(R.id.galleryImage)
    }
}
