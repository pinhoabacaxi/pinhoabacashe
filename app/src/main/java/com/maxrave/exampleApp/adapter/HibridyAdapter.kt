package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.VideoMeta

class HybridAdapter(
    private val onItemClick: (item: Any, position: Int) -> Unit,
    private val onMoreOptionsClick: (item: Any) -> Unit,
    private val onFavoriteClick: (item: Any) -> Unit // Adicionado para corrigir os botões que não respondiam
) : RecyclerView.Adapter<HybridAdapter.MusicViewHolder>() {

    private var items = mutableListOf<Any>()

    fun setList(newList: List<Any>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    // --- LÓGICA DE SWIPE / REMOÇÃO ---
    fun removeItem(position: Int): Any {
        val removedItem = items[position]
        items.removeAt(position)
        notifyItemRemoved(position)
        return removedItem
    }

    fun restoreItem(item: Any, position: Int) {
        items.add(position, item)
        notifyItemInserted(position)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Song -> TYPE_LOCAL
            is OnlineSong, is VideoMeta -> TYPE_ONLINE
            else -> TYPE_LOCAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return MusicViewHolder(view)
        // Adicione no ViewHolder
        itemView.setOnLongClickListener {
            onLongItemClick(items[adapterPosition])
            true
        }
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Mapeamento de IDs conforme o seu item_song.xml
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvSongArtist)
        private val ivArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val ivSourceIndicator: ImageView = itemView.findViewById(R.id.ivSourceIndicator)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMoreOptions)

        fun bind(item: Any, position: Int) {
            when (item) {
                is Song -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.artist
                    
                    // Indicador Local (Pasta/Android)
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_save)
                    ivSourceIndicator.alpha = 0.5f

                    // Carrega capa local
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)
                    Glide.with(itemView.context)
                        .load(uri)
                        .placeholder(android.R.drawable.ic_media_play)
                        .error(android.R.drawable.ic_media_play)
                        .into(ivArt)
                    
                    // Atualiza ícone de favorito se a lógica existir no seu model Song
                    // btnFavorite.setImageResource(if(item.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                }
                
                is OnlineSong -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    
                    // Indicador Online (Nuvem/Globo)
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_search)
                    ivSourceIndicator.alpha = 0.8f

                    Glide.with(itemView.context)
                        .load(item.thumbnailUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(ivArt)
                }

                is VideoMeta -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_search)
                    
                    Glide.with(itemView.context)
                        .load(item.thumbnailUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(ivArt)
                }
            }

            // --- CORREÇÃO DOS CLIQUES ---
            
            // Clique na linha inteira: Dá o Play
            itemView.setOnClickListener { 
                onItemClick(item, position) 
            }

            // Clique no botão de favoritos: Não dá play, apenas executa a ação de favoritar
            btnFavorite.setOnClickListener {
                onFavoriteClick(item)
                // Opcional: animar ou trocar o ícone aqui
            }

            // Clique no botão de mais opções (Menu)
            btnMore.setOnClickListener { 
                onMoreOptionsClick(item) 
            }
        }
    }

    companion object {
        private const val TYPE_LOCAL = 0
        private const val TYPE_ONLINE = 1
    }
}
