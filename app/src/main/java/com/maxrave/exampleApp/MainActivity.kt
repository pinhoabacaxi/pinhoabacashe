package com.maxrave.exampleApp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SongAdapter
import com.maxrave.exampleApp.databinding.ActivityMainBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.FavoriteManager
import com.maxrave.exampleApp.repository.MusicLoader
import com.maxrave.exampleApp.repository.PlaylistManager
import com.maxrave.exampleApp.repository.RecentSongsManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var musicLoader: MusicLoader
    
    private lateinit var recentManager: RecentSongsManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var playlistManager: PlaylistManager
    
    private var currentList: List<Song> = emptyList()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            loadSongs()
        } else {
            Toast.makeText(this, "Permissão negada. O app não pode ler músicas.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialização de Managers e Repositórios
        musicLoader = MusicLoader(this)
        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        playlistManager = PlaylistManager(this)
        
        // Inicializa o Player Global
        LocalPlayerManager.init(this)

        setupRecyclerView()
        setupListeners()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            songs = emptyList(),
            favoriteManager = favoriteManager,
            onSongClick = { song ->
                LocalPlayerManager.setList(currentList)
                LocalPlayerManager.play(this, song)
                updateMiniPlayerUI(song)
            },
            onFavClick = { song ->
                favoriteManager.toggleFavorite(song.id)
                songAdapter.notifyDataSetChanged()
            },
            onLongClick = { song ->
                // Aqui você pode abrir o diálogo de adicionar à playlist futuramente
                Toast.makeText(this, "Opções para: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupListeners() {
        // Barra de busca local
        binding.etSearch.addTextChangedListener { text ->
            songAdapter.filter(text.toString())
        }

        // Botão para abrir busca online (Verifique se este ID existe no seu activity_main.xml)
        binding.btnOnlineSearch.setOnClickListener {
            val intent = Intent(this, OnlineSearchActivity::class.java)
            startActivity(intent)
        }

        // Mini Player
        binding.includeMiniPlayer.root.setOnClickListener {
            val intent = Intent(this, FullPlayerActivity::class.java)
            startActivity(intent)
        }

        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
        }
        
        // Configura Callbacks do Player para atualizar a UI automaticamente
        LocalPlayerManager.onPlaybackStatusChanged = { _ ->
            LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
        }
    }

    private fun checkPermissions() {
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
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                currentList = musicLoader.loadLocalSongs()
                binding.progressBar.visibility = View.GONE
                
                if (currentList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSongs.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSongs.visibility = View.VISIBLE
                    songAdapter.updateList(currentList)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Erro ao carregar músicas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMiniPlayerUI(song: Song) {
        binding.includeMiniPlayer.root.visibility = View.VISIBLE
        binding.includeMiniPlayer.tvMiniTitle.text = song.title
        binding.includeMiniPlayer.tvMiniArtist.text = song.artist
        
        val isPlaying = LocalPlayerManager.isPlaying()
        binding.includeMiniPlayer.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause 
            else android.R.drawable.ic_media_play
        )
    }

    override fun onResume() {
        super.onResume()
        LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
    }
}
