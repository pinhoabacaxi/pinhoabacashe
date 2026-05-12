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

    // ViewHolder que mapeia os componentes do item_playlist.xml
    inner class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPlaylistName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePlaylist)

        fun bind(playlist: Playlist) {
            tvName.text = playlist.name
            
            // Clique simples para abrir a playlist
            itemView.setOnClickListener { onClick(playlist) }
            
            // Clique no botão de lixeira para excluir
            btnDelete.setOnClickListener { onDelete(playlist) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        // Corrigido: Inflação do layout correta e passagem para o ViewHolder
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        // Corrigido: Chamada da função bind com o objeto Playlist completo
        holder.bind(playlists[position])
    }

    override fun getItemCount() = playlists.size

    // Atualiza a lista quando houver mudanças no banco de dados Room
    fun updateList(newList: List<Playlist>) {
        this.playlists = newList
        notifyDataSetChanged()
    }
}
