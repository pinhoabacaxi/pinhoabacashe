package com.maxrave.exampleApp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.HybridAdapter
import com.maxrave.exampleApp.databinding.ActivityPlaylistSongsBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.PlaylistRepository
import kotlinx.coroutines.launch

class PlaylistSongsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistSongsBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var hybridAdapter: HybridAdapter
    private var playlistId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistSongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra("PLAYLIST_ID", -1)
        val playlistName = intent.getStringExtra("PLAYLIST_NAME") ?: "Playlist"
        
        // Sincroniza o cabeçalho com o nome da playlist
        binding.tvPlaylistName.text = playlistName
        
        // Configura Toolbar para voltar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        repository = PlaylistRepository(this)
        setupRecyclerView()
        loadPlaylistSongs()

        binding.fabPlayShuffle.setOnClickListener {
            val list = hybridAdapter.getList()
            if (list.isNotEmpty()) {
                LocalPlayerManager.isShuffle = true
                LocalPlayerManager.setQueueAndPlay(list, 0, this)
                startActivity(Intent(this, FullPlayerActivity::class.java))
            }
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                LocalPlayerManager.setQueueAndPlay(hybridAdapter.getList(), position, this)
                startActivity(Intent(this, FullPlayerActivity::class.java))
            },
            onMoreOptionsClick = { item -> 
                // Futuro: Menu de remoção
            },
            onFavoriteClick = { item -> },
            onLongItemClick = { item -> }
        )

        binding.rvPlaylistSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistSongsActivity)
            adapter = hybridAdapter
        }
    }

    private fun loadPlaylistSongs() {
        lifecycleScope.launch {
            val result = repository.getSongsFromPlaylist(playlistId)
            if (result.isNotEmpty()) {
                val songsFromDb = result[0].songs
                
                // Atualiza o contador de músicas no layout
                binding.tvPlaylistInfo.text = "${songsFromDb.size} músicas"
                
                val mappedList = songsFromDb.map { entity ->
                    if (entity.isOnline) {
                        OnlineSong(
                            videoId = entity.id, 
                            title = entity.title, 
                            author = entity.artist, 
                            thumbnailUrl = entity.thumbnailUrl ?: "", 
                            url = entity.sourcePath,
                            duration = "0"
                        )
                    // Dentro de loadPlaylistSongs, no mapeamento do Song:
                    // Dentro de loadPlaylistSongs()
                    } else {
                        Song(
                            id = entity.id.toLongOrNull() ?: 0L,
                            title = entity.title, 
                            artist = entity.artist, 
                            album = "Playlist",    // <--- Faltava este
                            duration = 0L,         // <--- Garantir que seja Long (0L)
                            path = entity.sourcePath,
                            albumId = 0L           // <--- Faltava este
                        )
                    }
                }
                hybridAdapter.setList(mappedList)
                
                // Sincronia de visibilidade com o novo TextView tvEmptyState
                binding.tvEmptyState.visibility = if (mappedList.isEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.tvPlaylistInfo.text = "0 músicas"
                binding.tvEmptyState.visibility = View.VISIBLE
            }
        }
    }
}
