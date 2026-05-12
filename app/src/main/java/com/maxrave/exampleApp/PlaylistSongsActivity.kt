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
        
        binding.collapsingToolbar.title = playlistName
        
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
                // Futuro: Abrir menu para remover da playlist
            },
            onFavoriteClick = { item ->
                // Opcional: Lógica de favoritos rápida
            },
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
                    } else {
                        Song(
                            id = entity.id.toLongOrNull() ?: 0L,
                            title = entity.title, 
                            artist = entity.artist, 
                            path = entity.sourcePath,
                            album = "Playlist", 
                            duration = 0, 
                            albumArtUri = null
                        )
                    }
                }
                hybridAdapter.setList(mappedList)
                binding.tvEmptyState.visibility = if (mappedList.isEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.tvEmptyState.visibility = View.VISIBLE
            }
        }
    }
}
