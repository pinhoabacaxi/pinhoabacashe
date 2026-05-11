package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.maxrave.exampleApp.databinding.ItemOnlineSongBinding
import com.maxrave.exampleApp.model.OnlineSong

class OnlineSongAdapter(
    private val onItemClick: (OnlineSong) -> Unit
) : RecyclerView.Adapter<OnlineSongAdapter.OnlineViewHolder>() {

    private var songs: List<OnlineSong> = emptyList()

    class OnlineViewHolder(val binding: ItemOnlineSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineViewHolder {
        val binding = ItemOnlineSongBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return OnlineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnlineViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.apply {
            // Vinculação dos textos
            tvOnlineTitle.text = song.title
            
            // Usamos 'author' para ser coerente com o Model OnlineSong.kt
            tvOnlineChannel.text = song.author

            // Carregamento da Thumbnail com transição suave
            Glide.with(holder.itemView.context)
                .load(song.thumbnailUrl)
                .transition(DrawableTransitionOptions.withCrossFade()) // Efeito de fade ao aparecer
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop() // Garante que a imagem preencha o espaço sem distorcer
                .into(ivThumbnail)

            // Clique no item
            root.setOnClickListener { onItemClick(song) }
        }
    }

    override fun getItemCount(): Int = songs.size

    // Método para atualizar a lista de resultados da busca
    fun updateList(newSongs: List<OnlineSong>) {
        this.songs = newSongs
        notifyDataSetChanged()
    }
}
