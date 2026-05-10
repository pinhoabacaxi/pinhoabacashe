package com.maxrave.exampleApp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SongAdapter
import com.maxrave.exampleApp.databinding.ActivityMainBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.MusicLoader
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private val musicLoader by lazy { MusicLoader(this) }
    private var currentList: List<Song> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadSongs()
        else Toast.makeText(this, "Acesso negado às músicas.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
            val index = currentList.indexOf(song)
            LocalPlayerManager.playList(currentList, index, this)
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun setupPlayerListeners() {
        LocalPlayerManager.onTrackChanged = { song ->
            binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
            binding.includeMiniPlayer.tvMiniTitle.text = song.title
            binding.includeMiniPlayer.tvMiniArtist.text = song.artist
            binding.includeMiniPlayer.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
        }

        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
        }

        binding.includeMiniPlayer.btnNext.setOnClickListener {
            LocalPlayerManager.next(this)
        }

        binding.includeMiniPlayer.btnPrev.setOnClickListener {
            LocalPlayerManager.previous(this)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.firstOrNull {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest != null) {
            permissionLauncher.launch(toRequest)
        } else {
            loadSongs()
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            currentList = musicLoader.loadLocalSongs()
            if (currentList.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvSongs.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvSongs.visibility = View.VISIBLE
                songAdapter.updateList(currentList)
                setupPlayerListeners()
            }
        }
    }
}
