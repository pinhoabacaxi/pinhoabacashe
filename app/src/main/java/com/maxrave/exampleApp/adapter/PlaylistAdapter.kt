package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.Room.Playlist

class PlaylistAdapter(
    private var playlists: List<Playlist>,
    private val onClick: (Playlist) -> Unit,
    private val onDelete: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPlaylistName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePlaylist)

        fun bind(playlist: Playlist) {
            tvName.text = playlist.name
            itemView.setOnClickListener { onClick(playlist) }
            btnDelete.setOnClickListener { onDelete(playlist) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val name = playlists[position]
        (holder.view as android.widget.TextView).text = name
        holder.view.setOnClickListener { onClick(name) }
        holder.view.setOnLongClickListener { 
            onDelete(name)
            true 
        }
    }

    override fun getItemCount() = playlists.size

    fun updateList(newList: List<String>) {
        playlists = newList
        notifyDataSetChanged()
    }
}
