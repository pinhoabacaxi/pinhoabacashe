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
    private lateinit var musicLoader: MusicLoader
    private var currentList: List<Song> = emptyList()

    // Launcher para múltiplas permissões (Necessário para Android 13+)
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            loadSongs()
        } else {
            Toast.makeText(this, "Permissões necessárias para o app funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inicialização de componentes
        musicLoader = MusicLoader(this)
        
        // 2. Configuração da UI
        setupRecyclerView()
        setupPlayerListeners()
        
        // 3. Verificação de dados
        checkPermissionsAndLoad()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
            val index = currentList.indexOf(song)
            if (index != -1) {
                LocalPlayerManager.playList(currentList, index, this)
            }
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun setupPlayerListeners() {
        // Observer para troca de música
        LocalPlayerManager.onTrackChanged = { song ->
            binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
            binding.includeMiniPlayer.tvMiniTitle.text = song.title
            binding.includeMiniPlayer.tvMiniArtist.text = song.artist
            binding.includeMiniPlayer.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }

        // Observer para status de play/pause
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
        }

        // Cliques no Mini Player
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

    private fun checkPermissionsAndLoad() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            loadSongs()
        } else {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            try {
                currentList = musicLoader.loadLocalSongs()
                if (currentList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSongs.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSongs.visibility = View.VISIBLE
                    songAdapter.updateList(currentList)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar músicas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza o mini player caso volte de outra tela ou app
        LocalPlayerManager.currentSong?.let { song ->
            binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
            binding.includeMiniPlayer.tvMiniTitle.text = song.title
            binding.includeMiniPlayer.tvMiniArtist.text = song.artist
            val icon = if (LocalPlayerManager.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
        }
    }
}
