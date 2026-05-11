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
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
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
    
    private var allSongs: List<Song> = emptyList()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) loadSongs()
        else Toast.makeText(this, "Permissão negada para ler músicas.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inicializar Repositórios
        musicLoader = MusicLoader(this)
        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        playlistManager = PlaylistManager(this)
        
        // 2. Inicializar Player Global
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
                LocalPlayerManager.setList(allSongs)
                LocalPlayerManager.play(this, song)
                updateMiniPlayerUI(song)
            },
            onFavClick = { song ->
                favoriteManager.toggleFavorite(song.id)
                songAdapter.notifyDataSetChanged()
            },
            onLongClick = { song ->
                Toast.makeText(this, "Opções: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupListeners() {
        // Busca na Biblioteca
        binding.searchViewLibrary.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                songAdapter.filter(newText ?: "")
                return true
            }
        })

        // Filtros por Chip
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> songAdapter.updateList(allSongs)
                R.id.chipFavorites -> {
                    val favs = allSongs.filter { favoriteManager.isFavorite(it.id) }
                    songAdapter.updateList(favs)
                }
                R.id.chipRecent -> {
                    val recents = recentManager.getRecentSongs(allSongs)
                    songAdapter.updateList(recents)
                }
            }
        }

        // Chip Buscar Online (Ação de clique)
        binding.chipOnline.setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        // Botão Scan
        binding.btnScan.setOnClickListener { loadSongs() }

        // Mini Player
        binding.includeMiniPlayer.root.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }

        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
        }
        
        LocalPlayerManager.onPlaybackStatusChanged = { _ ->
            LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) loadSongs()
        else permissionsLauncher.launch(missing.toTypedArray())
    }

    private fun loadSongs() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                allSongs = musicLoader.loadLocalSongs()
                binding.progressBar.visibility = View.GONE
                
                if (allSongs.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSongs.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSongs.visibility = View.VISIBLE
                    songAdapter.updateList(allSongs)
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
