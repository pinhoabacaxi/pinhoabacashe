package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Size
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
// Importe a classe VideoMeta se estiver usando a biblioteca do YouTube Extractor
import com.maxrave.kotlinyoutubeextractor.VideoMeta

class HybridAdapter(
    private val onItemClick: (item: Any, position: Int) -> Unit,
    private val onMoreOptionsClick: (item: Any) -> Unit
) : RecyclerView.Adapter<HybridAdapter.MusicViewHolder>() {

    // Lista única para músicas locais e online
    private var items = mutableListOf<Any>()

    fun setList(newList: List<Any>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    // --- LÓGICA DE SWIPE TO DISMISS ---

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

    // ----------------------------------

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Song -> TYPE_LOCAL
            is OnlineSong -> TYPE_ONLINE // Adicione is VideoMeta aqui se necessário
            else -> TYPE_LOCAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        // Usa o layout unificado que criamos
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvSongArtist)
        private val ivArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val ivSourceIndicator: ImageView = itemView.findViewById(R.id.ivSourceIndicator)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMoreOptions)

        fun bind(item: Any, position: Int) {
            when (item) {
                is Song -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.artist
                    
                    // Ícone de pasta/disco para indicar que é local
                    ivSourceIndicator.setImageResource(R.drawable.ic_folder) // Certifique-se de ter este ícone ou use um padrão

                    // Carrega a arte do MediaStore via Glide
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)
                        Glide.with(itemView.context)
                            .load(uri)
                            .placeholder(android.R.drawable.ic_media_play) // Placeholder premium
                            .into(ivArt)
                    } else {
                        // Lógica de capa para Android antigo se necessário
                        Glide.with(itemView.context).load(android.R.drawable.ic_media_play).into(ivArt)
                    }
                }
                
                is OnlineSong -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    
                    // Ícone de nuvem para indicar que é do YouTube
                    ivSourceIndicator.setImageResource(R.drawable.ic_cloud) // Certifique-se de ter este ícone

                    // Carrega a thumbnail do YouTube
                    Glide.with(itemView.context)
                        .load(item.thumbnailUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(ivArt)
                }
                
                is VideoMeta -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    ivSourceIndicator.setImageResource(R.drawable.ic_cloud)
                    Glide.with(itemView.context).load(item.thumbUrl).into(ivArt)
                }
            }

            // Cliques
            itemView.setOnClickListener { onItemClick(item, position) }
            btnMore.setOnClickListener { onMoreOptionsClick(item) }
        }
    }

    companion object {
        private const val TYPE_LOCAL = 0
        private const val TYPE_ONLINE = 1
    }
}
