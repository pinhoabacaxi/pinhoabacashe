package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.repository.FavoriteManager
import com.bumptech.glide.Glide
import androidx.appcompat.app.AlertDialog


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
        
        val albumArtUri = getAlbumArtUri(song.albumId)
        Glide.with(holder.itemView.context)
            .load(albumArtUri)
            .placeholder(android.R.drawable.ic_media_play) // Imagem padrão enquanto carrega
            .error(android.R.drawable.ic_media_play)       // Imagem caso não exista capa
            .into(holder.binding.ivAlbumArt) // Certifique-se que o ID no XML é este

        val song = songs[position]
        holder.binding.apply {
            tvSongTitle.text = song.title
            tvSongArtist.text = song.artist
            
            // Ícone de favorito nativo do Android para manter simplicidade
            val isFav = favoriteManager.isFavorite(song.id)
            btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )

            root.setOnClickListener { onSongClick(song) }
            btnFavorite.setOnClickListener { onFavClick(song) }
            root.setOnLongClickListener {
                onLongClick(song)
                true
            }
        }
    }
    
    override fun getItemCount(): Int = songs.size
   
    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }
    fun updateList(newSongs: List<Song>) {
        this.songs = newSongs
        // Se a lista completa for vazia (primeira carga), atualizamos ela também
        if (this.songsFull.isEmpty()) this.songsFull = newSongs
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
