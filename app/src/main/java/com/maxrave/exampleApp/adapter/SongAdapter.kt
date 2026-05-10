package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.repository.FavoriteManager

class SongAdapter(
    private var songs: List<Song>,
    private val favoriteManager: FavoriteManager,
    private val onSongClick: (Song) -> Unit,
    private val onFavClick: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit // Novo parâmetro
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    
    private var songsFull: List<Song> = songs

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.tvSongTitle.text = song.title
        holder.binding.tvSongArtist.text = song.artist
        
        // Atualiza o ícone de favorito (Supondo que existe um ImageView chamado ivFavorite no seu XML)
        val isFav = favoriteManager.isFavorite(song.id)
        // Exemplo de troca de ícone (ajuste para os seus recursos):
        // holder.binding.ivFavorite.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

        holder.binding.root.setOnClickListener { onSongClick(song) }
        
        // Se houver um botão de favorito no item_song.xml:
        // holder.binding.btnFavorite.setOnClickListener { onFavClick(song) }
    }

    override fun getItemCount(): Int = songs.size
   
    fun updateList(newSongs: List<Song>) {
        this.songs = newSongs
        this.songsFull = newSongs
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val filteredList = if (query.isEmpty()) {
            songsFull
        } else {
            songsFull.filter { 
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) 
            }
        }
        this.songs = filteredList
        notifyDataSetChanged()
    } 
}
