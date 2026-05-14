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

/**
 * Adapter unificado capaz de renderizar tanto vídeos (VideoMeta) quanto coleções (YouTubePlaylist).
 */
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
                    // CONFIGURAÇÃO PARA VÍDEO
                    tvOnlineTitle.text = item.title
                    tvOnlineChannel.text = item.author
                    
                    // Ícone de Play indica conteúdo executável imediatamente
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_media_play)
                    
                    // Botão de download visível apenas para vídeos individuais
                    btnDownload?.visibility = View.VISIBLE
                    
                    val thumbToLoad = item.thumbnailUrl.ifEmpty { 
                        "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" 
                    }
                    loadImage(holder, thumbToLoad)
                }
                is YouTubePlaylist -> {
                    // CONFIGURAÇÃO PARA PLAYLIST
                    tvOnlineTitle.text = "[PLAYLIST] ${item.title}"
                    
                    // Exibe o autor e a quantidade de vídeos da coleção
                    tvOnlineChannel.text = "${item.author} • ${item.videoCount} vídeos"
                    
                    // Ícone de Agenda/Lista indica que o clique abrirá uma nova lista
                    ivTypeIcon?.setImageResource(android.R.drawable.ic_menu_agenda)
                    
                    // Download em massa será gerenciado por uma ação interna na Activity
                    btnDownload?.visibility = View.GONE
    
                    loadImage(holder, item.thumbnailUrl)
                }
            }
            
            // AÇÃO DE CLIQUE: Repassa o objeto completo para a Activity decidir o fluxo
            root.setOnClickListener { onItemClick(item) }
            
            // AÇÃO DE DOWNLOAD: Ativada apenas para vídeos
            btnDownload?.setOnClickListener { 
                if (item is VideoMeta) onDownloadClick(item) 
            }
        }
    }

    /**
     * Gerencia o carregamento de imagens com transição crossfade para melhor UX.
     */
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
     * Atualiza a lista unificada (vídeos + playlists) e notifica o RecyclerView.
     */
    fun submitList(newList: List<Any>) {
        this.results = newList
        notifyDataSetChanged()
    }
}
