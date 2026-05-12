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

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvPlaylistName)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeletePlaylist)

        fun bind(playlist: Playlist) {
            tvName.text = playlist.name
            
            // CORREÇÃO: Utiliza a propriedade itemView nativa do ViewHolder
            itemView.setOnClickListener { onClick(playlist) }
            btnDelete.setOnClickListener { onDelete(playlist) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount() = playlists.size

    fun updateList(newList: List<Playlist>) {
        this.playlists = newList
        notifyDataSetChanged()
    }
}
