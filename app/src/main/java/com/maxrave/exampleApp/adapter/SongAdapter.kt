package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.model.Song

class SongAdapter(
    private var songs: List<Song>,
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
        holder.itemView.setOnClickListener { onClick(song) }
    }

    override fun getItemCount() = songs.size

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvSongTitle)
        private val artist: TextView = view.findViewById(R.id.tvSongArtist)

        fun bind(song: Song) {
            title.text = song.title
            artist.text = "${song.artist} • ${song.album}"
        }
    }
}
