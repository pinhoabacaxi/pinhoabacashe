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

class PlaylistSongsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistSongsBinding
    private lateinit var playlistName: String
    private lateinit var songAdapter: SongAdapter
    private lateinit var playlistManager: PlaylistManager
    private lateinit var musicLoader: MusicLoader

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
        songAdapter = SongAdapter(
            emptyList(),
            FavoriteManager(this),
            onSongClick = { song ->
                // Ao clicar, toca a playlist atual começando desta música
                // LocalPlayerManager deve receber a lista filtrada
            },
            onFavClick = { /* Lógica de favorito */ },
            onLongClick = { /* Opção de remover da playlist */ }
        )
        binding.rvPlaylistSongs.layoutManager = LinearLayoutManager(this)
        binding.rvPlaylistSongs.adapter = songAdapter
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
