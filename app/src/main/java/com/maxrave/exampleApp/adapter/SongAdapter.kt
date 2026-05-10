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
    onSongClick = { selectedSong ->
        val position = currentList.indexOf(selectedSong)
        if (position != -1) {
            LocalPlayerManager.startPlaying(this, currentList, position)
            updateMiniPlayerUI(selectedSong)
        }
    },
    onFavClick = { song ->
         favoriteManager.toggleFavorite(song.id)
         songAdapter.notifyDataSetChanged()
      },
    onLongClick = { song ->
        showPlaylistOptionsDialog(song)
    }

    binding.rvSongs.apply {
        layoutManager = LinearLayoutManager(this@MainActivity)
        adapter = songAdapter
    }
    
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
        
        holder.binding.root.setOnClickListener { onSongClick(song) }
        
        // Adicionando suporte ao clique longo para abrir o menu de playlists
        holder.binding.root.setOnLongClickListener {
            onLongClick(song)
            true
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
