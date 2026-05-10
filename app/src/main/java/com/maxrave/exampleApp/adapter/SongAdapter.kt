package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song

class SongAdapter(
    private var songs: List<Song>,

    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    private var songsFull: List<Song> = songs // Cópia da lista completa

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.tvSongTitle.text = song.title
        holder.binding.tvSongArtist.text = song.artist
        
        // CORREÇÃO AQUI: Use holder.binding.root para o clique
        holder.binding.root.setOnClickListener { onSongClick(song) }
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
