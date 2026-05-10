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

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.tvSongTitle.text = song.title
        holder.binding.tvSongArtist.text = song.artist
        holder.root.setOnClickListener { onSongClick(song) }
    }

    override fun getItemCount(): Int = songs.size

    // ESTA FUNÇÃO É O QUE ESTÁ FALTANDO:
    fun updateList(newSongs: List<Song>) {
        this.songs = newSongs
        notifyDataSetChanged()
    }
}
