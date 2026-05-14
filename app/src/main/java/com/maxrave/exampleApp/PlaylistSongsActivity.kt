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
import kotlinx.coroutines.flow.collect
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
        
        binding.tvPlaylistName.text = playlistName
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        repository = PlaylistRepository(this)
        setupRecyclerView()
        observePlaylistSongs() // Observação reativa

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
            onMoreOptionsClick = { },
            onFavoriteClick = { },
            onLongItemClick = { }
        )

        binding.rvPlaylistSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistSongsActivity)
            adapter = hybridAdapter
        }
    }

    private fun observePlaylistSongs() {
        lifecycleScope.launch {
            repository.getSongsFromPlaylistFlow(playlistId).collect { result ->
                if (result.isNotEmpty()) {
                    val songsFromDb = result[0].songs
                    binding.tvPlaylistInfo.text = "${songsFromDb.size} músicas"
                    
                    val mappedList = songsFromDb.map { entity ->
                        if (entity.isOnline) {
                            OnlineSong(entity.id, entity.title, entity.artist, entity.thumbnailUrl ?: "", entity.sourcePath, "0")
                        } else {
                            Song(entity.id.toLongOrNull() ?: 0L, entity.title, entity.artist, "Playlist", 0L, entity.sourcePath, 0L)
                        }
                    }
                    hybridAdapter.setList(mappedList)
                    binding.tvEmptyState.visibility = if (mappedList.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    binding.tvPlaylistInfo.text = "0 músicas"
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            }
        }
    }
}
