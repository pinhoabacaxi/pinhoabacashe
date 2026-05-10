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

        initRepositories()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupClickListeners()
        checkPermissions()
    }
    binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            return false
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            songAdapter.filter(newText ?: "")
            return true
        }
    })

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
                // Implementaremos o diálogo de playlist na Fase 5
                Toast.makeText(this, "Opções para: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = songAdapter
    }

   // Adicione um botão no seu XML da MainActivity (ou use um Chip novo)
// No onCreate ou setupFilters da MainActivity:

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

    private fun setupSearch() {
        // Supondo que você tenha um EditText de busca no seu XML de layout anterior
        // Se não houver, adicione um EditText ou SearchView com ID 'etSearch'
        binding.btnScan.setOnClickListener { loadSongs() }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            songAdapter.updateList(allSongs)
        }

        binding.chipFavorites.setOnClickListener {
            val favs = allSongs.filter { favoriteManager.isFavorite(it.id) }
            songAdapter.updateList(favs)
        }

        binding.chipRecent.setOnClickListener {
            val recentIds = recentManager.getRecentIds()
            val recentSongs = recentIds.mapNotNull { id ->
                allSongs.find { it.id.toString() == id }
            }
            songAdapter.updateList(recentSongs)
        }
    }

    private fun setupClickListeners() {
        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            val isPlaying = LocalPlayerManager.isPlaying()
            binding.includeMiniPlayer.btnPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
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
