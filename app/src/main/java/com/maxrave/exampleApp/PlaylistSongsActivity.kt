package com.maxrave.exampleApp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SongAdapter
import com.maxrave.exampleApp.databinding.ActivityPlaylistSongsBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.FavoriteManager
import com.maxrave.exampleApp.repository.MusicLoader
import com.maxrave.exampleApp.repository.PlaylistManager
import kotlinx.coroutines.launch
import android.content.Intent

class PlaylistSongsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistSongsBinding
    private lateinit var playlistName: String
    private lateinit var songAdapter: SongAdapter
    private lateinit var playlistManager: PlaylistManager
    private lateinit var musicLoader: MusicLoader
    private lateinit var favoriteManager: FavoriteManager // Certifique-se que esta linha existe
    // ... resto do código
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistSongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistName = intent.getStringExtra("PLAYLIST_NAME") ?: ""
        binding.tvPlaylistName.text = playlistName

        playlistManager = PlaylistManager(this)
        musicLoader = MusicLoader(this)

        setupRecyclerView()
        loadPlaylistSongs()
    }

    private fun setupRecyclerView() {
        // 1. Criamos o Adapter com os novos parâmetros
        songAdapter = SongAdapter(
            songs = emptyList(), // Esta lista será preenchida pelo seu carregador de músicas
            favoriteManager = FavoriteManager(this),
            onSongClick = { song, position -> 
                // 2. IMPORTANTE: Usamos a lista atual do adapter para garantir que 
                // a fila de reprodução respeite a ordem da playlist
                val currentPlaylist = songAdapter.getSongsList() // Ver nota abaixo
                LocalPlayerManager.setQueueAndPlay(currentPlaylist, position, this)
                
                // Abre o FullPlayer para o utilizador ver o que está a tocar
                startActivity(Intent(this, FullPlayerActivity::class.java))
            },
            onFavClick = { song -> 
                // Lógica para alternar favorito
                favoriteManager.toggleFavorite(song.id)
                // O notifyItemChanged já é tratado dentro do Adapter que corrigimos
            },
            onLongClick = { song -> 
                // Exibir diálogo de opções (Remover, Adicionar à outra playlist, etc)
                showBottomSheetOptions(song) 
            }
        )
    
        binding.rvPlaylistSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistSongsActivity)
            adapter = songAdapter
        }
    }
    private fun loadPlaylistSongs() {
        lifecycleScope.launch {
            val allSongs = musicLoader.loadLocalSongs()
            val songIds = playlistManager.getSongIdsFromPlaylist(playlistName)
            val filteredSongs = allSongs.filter { songIds.contains(it.id.toString()) }
            songAdapter.updateList(filteredSongs)
        }
    }
}
