package com.maxrave.exampleApp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    
    private var currentList: List<Song> = emptyList()

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

        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        playlistManager = PlaylistManager(this)
        musicLoader = MusicLoader(this)
        
        LocalPlayerManager.initRecentManager(this)

        setupRecyclerView()
        setupFilters()
        setupSearch()
        setupPlayerListeners()

        binding.btnScan.setOnClickListener {
            Toast.makeText(this, "Atualizando biblioteca...", Toast.LENGTH_SHORT).show()
            loadSongs()
        }

        checkPermissionsAndLoad()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                songAdapter.filter(newText ?: "")
                return true
            }
        })
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> songAdapter.updateList(currentList)
                R.id.chipFavorites -> {
                    val favs = currentList.filter { favoriteManager.isFavorite(it.id) }
                    songAdapter.updateList(favs)
                }
                R.id.chipRecent -> {
                    val recentIds = recentManager.getRecentIds()
                    val recents = currentList.filter { recentIds.contains(it.id.toString()) }
                    val sortedRecents = recents.sortedBy { recentIds.indexOf(it.id.toString()) }
                    songAdapter.updateList(sortedRecents)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        // CORREÇÃO: Estrutura do Adapter corrigida e variável 'position' definida
        songAdapter = SongAdapter(
            mutableListOf(),
            onSongClick = { selectedSong ->
                val position = currentList.indexOf(selectedSong)
                if (position != -1) {
                    LocalPlayerManager.startPlaying(this, currentList, position)
                    updateMiniPlayerUI(selectedSong)
                }
            },
            onFavClick = { song ->
                favoriteManager.toggleFavorite(song.id)
                songAdapter.notifyDataSetChanged()
            },
            onLongClick = { song ->
                showPlaylistOptionsDialog(song)
            }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun showPlaylistOptionsDialog(song: Song) {
        val playlists = playlistManager.getPlaylistNames().toList()
        val options = mutableListOf("＋ Criar Nova Playlist")
        options.addAll(playlists)

        AlertDialog.Builder(this)
            .setTitle("Adicionar '${song.title}' a:")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showCreatePlaylistDialog(song)
                } else {
                    val selectedPlaylist = options[which]
                    playlistManager.addSongToPlaylist(selectedPlaylist, song.id)
                    Toast.makeText(this, "Adicionado a $selectedPlaylist", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showCreatePlaylistDialog(song: Song) {
        val editText = EditText(this)
        editText.hint = "Nome da playlist"
        
        AlertDialog.Builder(this)
            .setTitle("Nova Playlist")
            .setView(editText)
            .setPositiveButton("Criar") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {
                    playlistManager.createPlaylist(name)
                    playlistManager.addSongToPlaylist(name, song.id)
                    Toast.makeText(this, "Playlist '$name' criada!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupPlayerListeners() {
        LocalPlayerManager.onTrackChanged = { song ->
            updateMiniPlayerUI(song)
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

        binding.includeMiniPlayer.root.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }
    }

    private fun updateMiniPlayerUI(song: Song) {
        binding.includeMiniPlayer.root.visibility = View.VISIBLE
        binding.includeMiniPlayer.tvMiniTitle.text = song.title
        binding.includeMiniPlayer.tvMiniArtist.text = song.artist
        val icon = if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
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
        LocalPlayerManager.currentSong?.let { song ->
            updateMiniPlayerUI(song)
        }
    }
}
