package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maxrave.exampleApp.databinding.ItemSongBinding // Reutilizando layout de item simples ou similar
import android.view.View

class PlaylistAdapter(
    private var playlists: List<String>,
    private val onClick: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        // Para simplificar, usaremos um TextView simples se não houver layout específico
        // Aqui assumo um layout básico ou o próprio ItemSongBinding adaptado
        fun bind(name: String) {
            // Lógica de bind simplificada
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
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
