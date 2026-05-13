package com.maxrave.exampleApp.adapter

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.databinding.ItemSongBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.FavoriteManager
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class SongAdapter(
    private var songs: List<Song>,
    private val favoriteManager: FavoriteManager,
    private val onSongClick: (Song, Int) -> Unit, 
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
            
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )

            Glide.with(holder.itemView.context)
                .load(albumArtUri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(android.R.drawable.ic_media_play) 
                .placeholder(R.drawable.ic_music_note) // Use um ícone padrão do seu app
                .error(R.drawable.ic_music_note)       // Caso falhe (como no seu log), carrega o padrão
                .error(android.R.drawable.ic_media_play)      
                .into(ivAlbumArt)

            val isFav = favoriteManager.isFavorite(song.id)
            btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )

            root.setOnClickListener { onSongClick(song, holder.adapterPosition) }
            
            btnFavorite.setOnClickListener { 
                onFavClick(song)
                notifyItemChanged(holder.adapterPosition)
            }

            root.setOnLongClickListener {
                onLongClick(song)
                true
            }

            // NOVA LÓGICA DO MENU DE OPÇÕES (Tocar a seguir, Fila, Playlist)
            btnMoreOptions.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Tocar a seguir")
                popup.menu.add(0, 2, 1, "Último da fila de reprodução")
                popup.menu.add(0, 3, 2, "Adicionar à playlist")

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            LocalPlayerManager.playNext(song)
                            Toast.makeText(view.context, "Tocará a seguir", Toast.LENGTH_SHORT).show()
                            true
                        }
                        2 -> {
                            LocalPlayerManager.addToEnd(song)
                            Toast.makeText(view.context, "Adicionado ao final da fila", Toast.LENGTH_SHORT).show()
                            true
                        }
                        3 -> {
                            showAddToPlaylistDialog(view.context, song)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
    
    // --- DIÁLOGOS DE PLAYLIST ---
    private fun showAddToPlaylistDialog(context: Context, item: Any) {
        val repository = PlaylistRepository(context)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Busca as playlists existentes no banco
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
                                // Adiciona a música à playlist selecionada. 
                                // (Ajuste o nome 'addSongToPlaylist' se estiver diferente no seu Repository)
                                repository.addSongToPlaylist(selectedPlaylist.id, item)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Adicionado à '${selectedPlaylist.name}'", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Erro ao salvar música. O método addSongToPlaylist existe no Repository?", Toast.LENGTH_LONG).show()
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
                        repository.createPlaylist(name)
                        // Puxamos as playlists novamente para encontrar o ID da que acabamos de criar
                        val playlists = repository.getAllPlaylists()
                        val newPlaylist = playlists.find { it.name == name }
                        
                        newPlaylist?.let {
                            try {
                                repository.addSongToPlaylist(it.id, item)
                            } catch (e: Exception) { }
                        }
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Playlist '$name' criada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun getItemCount(): Int = songs.size
   
    fun getSongsList(): List<Song> { return songs }
    
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
