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
    private val onDownloadClick: (Any) -> Unit // Adicione esta linha aqui
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {
    // Agora aceita qualquer tipo de objeto (Video ou Playlist)
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
                    // Configuração para VÍDEO
                    tvOnlineTitle.text = item.title
                    tvOnlineChannel.text = item.author
                    
                    // Ícone indicador (opcional: mostrar que é uma música única)
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_media_play)

                    val thumbToLoad = item.thumbnailUrl.ifEmpty { 
                        "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" 
                    }

                    loadImage(holder, thumbToLoad)
                }
                is YouTubePlaylist -> {
                    // Configuração para PLAYLIST
                    tvOnlineTitle.text = "[PLAYLIST] ${item.title}"
                    tvOnlineChannel.text = "${item.author} • ${item.videoCount} vídeos"
                    
                    // Diferenciação visual (opcional: mudar cor do texto ou ícone)
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_menu_agenda)

                    loadImage(holder, item.thumbnailUrl)
                }
            }

            // Clique unificado: a Activity decide o que fazer via 'when'
            root.setOnClickListener { onItemClick(item) }
            
            // Se você tiver um botão de download específico no seu item_online_song.xml:
            btnDownload?.setOnClickListener { onDownloadClick?.invoke(item) }
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

    /**
     * Atualizado para aceitar List<Any> vindo do ViewModel
     */
    fun submitList(newList: List<Any>) {
        this.results = newList
        notifyDataSetChanged()
    }
}
