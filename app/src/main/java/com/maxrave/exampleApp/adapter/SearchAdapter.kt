package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.maxrave.exampleApp.databinding.ItemOnlineSongBinding
import com.maxrave.kotlinyoutubeextractor.VideoMeta

class SearchAdapter(
    private val onItemClick: (VideoMeta) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    private var results: List<VideoMeta> = emptyList()

    class SearchViewHolder(val binding: ItemOnlineSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemOnlineSongBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = results[position]
        holder.binding.apply {
            // Mapeamento dos campos do VideoMeta para o seu XML
            tvOnlineTitle.text = item.title
            tvOnlineChannel.text = item.author
            
            // Usando a melhor thumbnail (dinâmica do InnerTube ou fallback estático)
            // Note: Se você não criou a propriedade 'bestThumbnail' no VideoMeta, 
            // pode usar 'item.thumbnailUrl.ifEmpty { item.hqImageUrl }'
            val thumbToLoad = if (item.thumbnailUrl.isNotEmpty()) item.thumbnailUrl else "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg"

            Glide.with(holder.itemView.context)
                .load(thumbToLoad)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(ivThumbnail)

            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = results.size

    /**
     * Este é o método que o ViewModel chamará através da Activity
     */
    fun submitList(newList: List<VideoMeta>) {
        this.results = newList
        notifyDataSetChanged()
    }
}
