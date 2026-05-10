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
import com.maxrave.exampleApp.repository.*
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
        if (permissions.all { it.value }) loadSongs()
        else Toast.makeText(this, "Permissões necessárias para ler músicas", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialização do Player e Persistência
        LocalPlayerManager.init(this)
        
        initRepositories()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupClickListeners()
        checkPermissions()
        setupRestoreLastSong()
    }

    private fun initRepositories() {
        musicLoader = MusicLoader(this)
        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        playlistManager = PlaylistManager(this)
        LocalPlayerManager.initRecentManager(this)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            songs = emptyList(),
            favoriteManager = favoriteManager,
            onSongClick = { song ->
                val index = allSongs.indexOf(song)
                LocalPlayerManager.playList(allSongs, index, this)
                updateMiniPlayerUI(song)
            },
            onFavClick = { song ->
                favoriteManager.toggleFavorite(song.id)
                songAdapter.notifyDataSetChanged()
            },
            onLongClick = { song ->
                // Opcional: Implementar diálogo de "Adicionar à Playlist" aqui
                Toast.makeText(this, "Opções para: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = songAdapter
    }

    private fun setupSearch() {
        // Busca em tempo real conforme o usuário digita
        binding.etSearch.addTextChangedListener { text ->
            songAdapter.filter(text.toString())
        }
        
        binding.btnScan.setOnClickListener { loadSongs() }
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> songAdapter.updateList(allSongs)
                
                R.id.chipFavorites -> {
                    val favs = allSongs.filter { favoriteManager.isFavorite(it.id) }
                    songAdapter.updateList(favs)
                }
                
                R.id.chipRecent -> {
                    val recentIds = recentManager.getRecentIds()
                    val recentSongs = allSongs.filter { recentIds.contains(it.id.toString()) }
                        .sortedByDescending { recentIds.indexOf(it.id.toString()) }
                    songAdapter.updateList(recentSongs)
                }
                
                R.id.chipPlaylists -> {
                    showBrowsePlaylistsDialog()
                    // Desmarca o chip para permitir clicar novamente
                    binding.chipGroupFilters.clearCheck()
                }
            }
        }
    }

    private fun setupRestoreLastSong() {
        val playerPrefs = PlayerPrefs(this)
        val lastId = playerPrefs.getLastSongId()
        
        lifecycleScope.launch {
            // Aguarda carregar as músicas se a lista estiver vazia
            if (allSongs.isEmpty()) allSongs = musicLoader.loadLocalSongs()
            
            val lastSong = allSongs.find { it.id == lastId }
            lastSong?.let { updateMiniPlayerUI(it) }
        }
    }

    private fun showBrowsePlaylistsDialog() {
        val playlists = playlistManager.getPlaylistNames().toList()
        if (playlists.isEmpty()) {
            Toast.makeText(this, "Nenhuma playlist criada.", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Minhas Playlists")
        builder.setItems(playlists.toTypedArray()) { _, which ->
            val selected = playlists[which]
            val intent = Intent(this, PlaylistSongsActivity::class.java)
            intent.putExtra("PLAYLIST_NAME", selected)
            startActivity(intent)
        }
        builder.show()
    }

    private fun setupClickListeners() {
        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            updateMiniPlayerUI(LocalPlayerManager.currentSong ?: return@setOnClickListener)
        }

        binding.includeMiniPlayer.miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
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

        val missing = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) loadSongs()
        else permissionsLauncher.launch(missing.toTypedArray())
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            try {
                allSongs = musicLoader.loadLocalSongs()
                if (allSongs.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSongs.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSongs.visibility = View.VISIBLE
                    songAdapter.updateList(allSongs)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar biblioteca", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMiniPlayerUI(song: Song) {
        binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
        binding.includeMiniPlayer.tvMiniTitle.text = song.title
        binding.includeMiniPlayer.tvMiniArtist.text = song.artist
        binding.includeMiniPlayer.btnPlayPause.setImageResource(
            if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause 
            else android.R.drawable.ic_media_play
        )
    }

    override fun onResume() {
        super.onResume()
        LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
    }
}
