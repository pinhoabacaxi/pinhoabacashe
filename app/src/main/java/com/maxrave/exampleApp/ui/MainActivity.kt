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
import com.maxrave.exampleApp.repository.MusicLoader
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var currentList: List<Song> = emptyList()

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
        // Ao clicar, toca a música passando a lista atual e a posição
            val index = currentList.indexOf(song)
            LocalPlayerManager.playList(currentList, index, this)
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private val musicLoader by lazy { MusicLoader(this) }

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
        private fun setupPlayerListeners() {
        LocalPlayerManager.onTrackChanged = { song ->
            binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
            binding.includeMiniPlayer.tvMiniTitle.text = song.title
            binding.includeMiniPlayer.tvMiniArtist.text = song.artist
        // Atualiza ícone para play pois uma nova track sempre começa tocando
            binding.includeMiniPlayer.btnPlayPause.setImageResource(android.media.session.PlaybackState.STATE_PLAYING) 
        // Nota: Usei um recurso nativo acima apenas para exemplo, ideal é ic_media_pause
            binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
    // Agora passamos o 'this' (contexto da Activity)
            LocalPlayerManager.togglePlayPause(this) 
    }

    LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
        val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
    }

    binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
        LocalPlayerManager.togglePlayPause()
    }

    binding.includeMiniPlayer.btnNext.setOnClickListener {
        LocalPlayerManager.next(this)
    }

    binding.includeMiniPlayer.btnPrev.setOnClickListener {
        LocalPlayerManager.previous(this)
    }
}
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
            // Ação de clique: será conectada ao Player na Fase 2
            Toast.makeText(this, "Tocando: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            currentList = musicLoader.loadLocalSongs()
            if (currentList.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                songAdapter.updateList(currentList)
                setupPlayerListeners() // Inicializa os cliques do mini player
            }
        }
    }
}
