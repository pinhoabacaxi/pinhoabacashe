package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.R // Importe o seu R
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.repository.FavoriteManager

class SongAdapter(
    private var songs: List<Song>,
    private val favoriteManager: FavoriteManager,
    private val onSongClick: (Song, Int) -> Unit, // Adicionado posição para facilitar o player
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
            
            // Uri da capa do álbum
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )

            Glide.with(holder.itemView.context)
                .load(albumArtUri)
                .placeholder(android.R.drawable.ic_media_play) 
                .error(android.R.drawable.ic_media_play)      
                .into(ivAlbumArt)

            // CORREÇÃO: Verificação de Favorito
            // Se o compilador ainda der erro no 'btnFavorite', verifique se o ID no XML 
            // do layout 'item_song.xml' é exatamente: android:id="@+id/btnFavorite"
            val isFav = favoriteManager.isFavorite(song.id)
            btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )

            // Click Listeners
            root.setOnClickListener { onSongClick(song, holder.adapterPosition) }
            
            btnFavorite.setOnClickListener { 
                onFavClick(song)
                // Atualiza apenas este item para refletir a mudança no ícone de favorito
                notifyItemChanged(holder.adapterPosition)
            }

            root.setOnLongClickListener {
                onLongClick(song)
                true
            }
        }
    }
    
    override fun getItemCount(): Int = songs.size
   
    fun getSongsList(): List<Song> {
        return songs // 'songs' é a lista que você já tem no adapter
    }
    fun updateList(newSongs: List<Song>) {
        this.songs = newSongs
        this.songsFull = newSongs
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val filterPattern = query.lowercase().trim()
        
        this.songs = if (filterPattern.isEmpty()) {
            songsFull
        } else {
            songsFull.filter { 
                it.title.lowercase().contains(filterPattern) ||
                it.artist.lowercase().contains(filterPattern) 
            }
        }
        notifyDataSetChanged()
    }
}
