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
                // No OnItemClick do SearchAdapter
                is VideoMeta -> {
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.VISIBLE
                        
                        // ESSENCIAL: O item da busca não tem a URL de áudio. Temos que extrair agora.
                        val onlineSong = withContext(Dispatchers.IO) {
                            youtubeRepository.extractAudioLink(item.videoId)
                        }
                        
                        binding.progressBar.visibility = View.GONE
                        
                        if (onlineSong != null && !onlineSong.url.isNullOrEmpty()) {
                            LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                        } else {
                            Toast.makeText(this@OnlineSearchActivity, "Link de áudio expirado ou indisponível", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is YouTubePlaylist -> {
                    // Configuração para PLAYLIST
                    tvOnlineTitle.text = "[PLAYLIST] ${item.title}"
                    tvOnlineChannel.text = "${item.author} • ${item.videoCount} vídeos"
                    
                    // Ícone de Lista para playlists
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_menu_agenda)
                    
                    // ESCONDE o botão de download para playlists (evita crash no Worker)
                    btnDownload?.visibility = View.GONE

                    loadImage(holder, item.thumbnailUrl)
                }
            }
            
            // Clique no card (abre o vídeo ou abre a playlist)
            root.setOnClickListener { onItemClick(item) }
            
            // Clique no download (apenas se for vídeo)
            btnDownload?.setOnClickListener { 
                if (item is VideoMeta) {
                    onDownloadClick(item) 
                }
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
