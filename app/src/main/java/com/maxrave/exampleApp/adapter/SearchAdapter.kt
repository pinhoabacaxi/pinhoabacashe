package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
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
                    tvOnlineTitle.text = item.title
                    tvOnlineChannel.text = item.author
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_media_play)
                    
                    val thumbToLoad = item.thumbnailUrl.ifEmpty { 
                        "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" 
                    }
                    loadImage(holder, thumbToLoad)
                }
                is YouTubePlaylist -> {
                    // Diferenciação para Playlist
                    tvOnlineTitle.text = "[PLAYLIST] ${item.title}"
                    tvOnlineChannel.text = "${item.author} • ${item.videoCount} vídeos"
                    
                    // Ícone de álbum/lista para playlists
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_menu_agenda)

                    loadImage(holder, item.thumbnailUrl)
                }
            }
            
            root.setOnClickListener { onItemClick(item) }
            
            // Botão de download só faz sentido para vídeos individuais
            btnDownload?.setOnClickListener { onDownloadClick(item) }
        }
    }

    private fun ItemOnlineSongBinding.loadImage(holder: SearchViewHolder, url: String) {
        Glide.with(holder.itemView.context)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(ivThumbnail)
    }

    override fun getItemCount(): Int = results.size

    fun submitList(newList: List<Any>) {
        this.results = newList
        notifyDataSetChanged()
    }
}
