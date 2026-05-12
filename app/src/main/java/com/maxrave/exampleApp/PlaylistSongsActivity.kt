package com.maxrave.exampleApp

import android.content.Intent
import android.os.Bundle
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
            if (hybridAdapter.itemCount > 0) {
                LocalPlayerManager.isShuffle = true
                LocalPlayerManager.setQueueAndPlay(hybridAdapter.getList(), 0, this)
                startActivity(Intent(this, FullPlayerActivity::class.java))
            }
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item ->
                val list = hybridAdapter.getList()
                val pos = list.indexOf(item)
                LocalPlayerManager.setQueueAndPlay(list, pos, this)
                startActivity(Intent(this, FullPlayerActivity::class.java))
            },
            onLongItemClick = { item ->
                // Aqui você pode mostrar um diálogo para remover da playlist
            }
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
                        OnlineSong(entity.id, entity.title, entity.artist, entity.thumbnailUrl ?: "", entity.sourcePath)
                    } else {
                        // Mapeia para o seu modelo Song local
                        Song(entity.id.toLong(), entity.title, entity.artist, "", 0, entity.sourcePath, null)
                    }
                }
                hybridAdapter.setList(mappedList.toMutableList())
            }
        }
    }
}
