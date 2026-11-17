package com.example.videowallpaper

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videowallpaper.databinding.ItemVideoBinding
import java.io.File

class VideoAdapter(
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private var items: List<File> = emptyList()

    fun submit(list: List<File>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.b.name.text = VideoStore.displayName(file)
        holder.b.size.text = Formatter.formatShortFileSize(holder.itemView.context, file.length())
        holder.b.btnDelete.setOnClickListener { onDelete(file) }
    }
}
