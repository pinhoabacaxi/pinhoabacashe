package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HybridAdapter(
    private val onItemClick: (item: Any, position: Int) -> Unit,
    private val onMoreOptionsClick: (item: Any) -> Unit, 
    private val onFavoriteClick: (item: Any) -> Unit,
    private val onLongItemClick: (item: Any) -> Unit
) : RecyclerView.Adapter<HybridAdapter.MusicViewHolder>() {

    private var items = mutableListOf<Any>()

    fun getList(): List<Any> = items
    
    fun setList(newList: List<Any>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int): Any {
        val removedItem = items[position]
        items.removeAt(position)
        notifyItemRemoved(position)
        return removedItem
    }

    fun restoreItem(item: Any, position: Int) {
        items.add(position, item)
        notifyItemInserted(position)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Song -> TYPE_LOCAL
            is OnlineSong, is VideoMeta -> TYPE_ONLINE
            else -> TYPE_LOCAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvSongArtist)
        private val ivArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val ivSourceIndicator: ImageView = itemView.findViewById(R.id.ivSourceIndicator)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMoreOptions)

        fun bind(item: Any, position: Int) {
            when (item) {
                is Song -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.artist
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_save)
                    ivSourceIndicator.alpha = 0.5f

                    // Carregando a capa do álbum via albumId para evitar erro de JNI
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        item.albumId
                    )

                    Glide.with(itemView.context)
                        .asBitmap()
                        .load(albumArtUri)
                        .placeholder(android.R.drawable.ic_media_play)
                        .error(android.R.drawable.ic_media_play)
                        .centerCrop()
                        .into(ivArt)
                }

                is OnlineSong -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_search)
                    ivSourceIndicator.alpha = 0.8f

                    Glide.with(itemView.context)
                        .asBitmap()
                        .load(item.thumbnailUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(ivArt)
                }

                is VideoMeta -> {
                    tvTitle.text = item.title
                    tvArtist.text = item.author
                    ivSourceIndicator.setImageResource(android.R.drawable.ic_menu_search)
                    ivSourceIndicator.alpha = 0.8f

                    Glide.with(itemView.context)
                        .asBitmap()
                        .load(item.thumbnailUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .fallback(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(ivArt)
                }
            }

            itemView.setOnClickListener { onItemClick(item, position) }

            itemView.setOnLongClickListener {
                onLongItemClick(item)
                true
            }

            btnFavorite.setOnClickListener { onFavoriteClick(item) }

            btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Tocar a seguir")
                popup.menu.add(0, 2, 1, "Último da fila de reprodução")
                popup.menu.add(0, 3, 2, "Adicionar à playlist")

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            LocalPlayerManager.playNext(item)
                            Toast.makeText(view.context, "Tocará a seguir", Toast.LENGTH_SHORT).show()
                            true
                        }
                        2 -> {
                            LocalPlayerManager.addToEnd(item)
                            Toast.makeText(view.context, "Adicionado ao final da fila", Toast.LENGTH_SHORT).show()
                            true
                        }
                        3 -> {
                            showAddToPlaylistDialog(view.context, item)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    private fun showAddToPlaylistDialog(context: Context, item: Any) {
        val repository = PlaylistRepository(context)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val playlists = withContext(Dispatchers.IO) { repository.getAllPlaylists() }
                
                if (playlists.isEmpty()) {
                    showCreateNewPlaylistDialog(context, item, repository)
                    return@launch
                }

                val playlistNames = playlists.map { it.name }.toTypedArray()

                AlertDialog.Builder(context)
                    .setTitle("Adicionar à Playlist")
                    .setItems(playlistNames) { _, which ->
                        val selectedPlaylist = playlists[which]
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                repository.addSongToPlaylist(selectedPlaylist.id, item)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Adicionado à '${selectedPlaylist.name}'", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { 
                                    Toast.makeText(context, "Erro ao salvar música.", Toast.LENGTH_SHORT).show() 
                                }
                            }
                        }
                    }
                    .setPositiveButton("Nova Playlist") { _, _ ->
                        showCreateNewPlaylistDialog(context, item, repository)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao carregar playlists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateNewPlaylistDialog(context: Context, item: Any, repository: PlaylistRepository) {
        val input = EditText(context)
        input.hint = "Nome da nova playlist"

        AlertDialog.Builder(context)
            .setTitle("Criar Nova Playlist")
            .setView(input)
            .setPositiveButton("Criar e Adicionar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repository.createPlaylist(name)
                            val playlists = repository.getAllPlaylists()
                            val newPlaylist = playlists.find { it.name == name }
                            
                            newPlaylist?.let {
                                repository.addSongToPlaylist(it.id, item)
                            }
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Playlist '$name' criada com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Erro ao criar playlist.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        private const val TYPE_LOCAL = 0
        private const val TYPE_ONLINE = 1
    }
}
