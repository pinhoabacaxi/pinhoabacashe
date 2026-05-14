package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.maxrave.exampleApp.databinding.ItemOnlineSongBinding
import com.maxrave.exampleApp.repository.YouTubePlaylist
import com.maxrave.kotlinyoutubeextractor.VideoMeta

class SearchAdapter(
    private val onItemClick: (Any) -> Unit,
    private val onDownloadClick: (Any) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    private var results: List<Any> = emptyList()

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
            when (item) {
                is VideoMeta -> {
                    // Configuração visual para VÍDEO
                    tvOnlineTitle.text = item.title
                    tvOnlineChannel.text = item.author
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_media_play)
                    btnDownload?.visibility = View.VISIBLE
                    
                    val thumbToLoad = item.thumbnailUrl.ifEmpty { 
                        "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" 
                    }
                    loadImage(holder, thumbToLoad)
                }
                is YouTubePlaylist -> {
                    // Configuração visual para PLAYLIST
                    tvOnlineTitle.text = "[PLAYLIST] ${item.title}"
                    tvOnlineChannel.text = "${item.author} • ${item.videoCount} vídeos"
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_menu_agenda)
                    
                    // Esconde download para playlists (evita erros no Worker)
                    btnDownload?.visibility = View.GONE
    
                    loadImage(holder, item.thumbnailUrl)
                }
            }
            
            // Repassa o clique para a Activity resolver
            root.setOnClickListener { onItemClick(item) }
            
            btnDownload?.setOnClickListener { 
                if (item is VideoMeta) onDownloadClick(item) 
            }
        }
    }
    private fun ItemOnlineSongBinding.loadImage(holder: SearchViewHolder, url: String) {
        Glide.with(holder.itemView.context)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .centerCrop()
            .into(ivThumbnail)
    }

    override fun getItemCount(): Int = results.size

    fun submitList(newList: List<Any>) {
        this.results = newList
        notifyDataSetChanged()
    }
}
