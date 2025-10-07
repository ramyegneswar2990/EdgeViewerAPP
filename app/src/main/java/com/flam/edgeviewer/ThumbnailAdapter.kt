package com.flam.edgeviewer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ThumbnailAdapter(
    private val maxItems: Int = 12,
    private val onClick: ((Bitmap) -> Unit)? = null
) : RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    private val items = ArrayList<Bitmap>()

    fun addThumbnail(bm: Bitmap) {
        items.add(0, bm)
        if (items.size > maxItems) {
            // Recycle the last bitmap to free memory
            val removed = items.removeLast()
            removed.recycle()
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thumbnail, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bmp = items[position]
        holder.image.setImageBitmap(bmp)
        holder.itemView.setOnClickListener { onClick?.invoke(bmp) }
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.imageThumb)
    }
}
