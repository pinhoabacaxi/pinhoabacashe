package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.repository.FavoriteManager

class SongAdapter(
    private var songs: List<Song>,
    private val favoriteManager: FavoriteManager,
    private val onSongClick: (Song) -> Unit,
    private val onFavClick: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    
    private var songsFull: List<Song> = songs

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        
        holder.binding.apply {
            tvSongTitle.text = song.title
            tvSongArtist.text = song.artist
            
            // Carregamento da capa do álbum com Glide
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )

            Glide.with(holder.itemView.context)
                .load(albumArtUri)
                .placeholder(android.R.drawable.ic_media_play) // Ícone padrão
                .error(android.R.drawable.ic_media_play)       // Caso não encontre a capa
                .into(ivAlbumArt)

            // Configura o ícone de favorito
            val isFav = favoriteManager.isFavorite(song.id)
            btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )

            // Listeners de clique
            root.setOnClickListener { onSongClick(song) }
            btnFavorite.setOnClickListener { onFavClick(song) }
            root.setOnLongClickListener {
                onLongClick(song)
                true
            }
        }
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
