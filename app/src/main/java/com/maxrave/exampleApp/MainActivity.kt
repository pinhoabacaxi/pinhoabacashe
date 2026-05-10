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
    private lateinit var recentManager: RecentSongsManager
    private lateinit var favoriteManager: FavoriteManager
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
    
    
private fun setupSearch() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Dentro do onCreate da MainActivity.kt, adicione:
        binding.btnScan.setOnClickListener {
            Toast.makeText(this, "Atualizando biblioteca...", Toast.LENGTH_SHORT).show()
            loadSongs()
        }
        // ...
        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        LocalPlayerManager.initRecentManager(this)

        setupSearch()
        setupFilters()
    }



        // 1. Inicialização de componentes
        musicLoader = MusicLoader(this)
        // No onCreate, após carregar as músicas:
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean = false

        override fun onQueryTextChange(newText: String?): Boolean {
            songAdapter.filter(newText ?: "")
            return true
        }
     })

        // 2. Configuração da UI
        setupRecyclerView()
        setupPlayerListeners()
        
        // 3. Verificação de dados
        checkPermissionsAndLoad()
    }
   
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.util.Editable?) {
            songAdapter.filter(s.toString())
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
                    // Ordenar conforme a ordem de reprodução recente
                    val sortedRecents = recents.sortedByDescending { recentIds.indexOf(it.id.toString()) }
                    songAdapter.updateList(sortedRecents)
                }
        
            }
        }
    // No MainActivity.kt, dentro do setupRecyclerView()
   
    private fun setupRecyclerView() {
        

    private fun showPlaylistDialog(song: Song) {
        
        songAdapter = SongAdapter(
            emptyList(),
                favManager,
                onSongClick = { song ->
                    val index = currentList.indexOf(song)
                    if (index != -1) {
                        LocalPlayerManager.playList(currentList, index, this)
                        updateMiniPlayerUI(song)
                    }
                },
                onFavClick = { song ->
                    favoriteManager.toggleFavorite(song.id)
                    songAdapter.notifyDataSetChanged()
               },
                onLongClick = { song ->
                    showPlaylistDialog(song) // Chama o diálogo de playlists
                }
            )
    
            binding.rvSongs.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = songAdapter
            }
        }
    private fun showPlaylistDialog(song: Song) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_to_playlist, null)
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPlaylistDialog)
        val btnNew = view.findViewById<android.widget.Button>(R.id.btnCreateNewPlaylist)
        val playlists = playlistManager.getPlaylistNames().toTypedArray()
        val options = mutableListOf("Criar Nova Playlist")
        options.addAll(playlists)
        val favManager = FavoriteManager(this)
            playlistManager = PlaylistManager(this) // Inicializa o gestor

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Adicionar '${song.title}' a:")
        builder.setItems(options.toTypedArray()) { _, which ->
            if (which == 0) {
                showCreatePlaylistDialog(song)
           } else {
                val selectedPlaylist = options[which]
                playlistManager.addSongToPlaylist(selectedPlaylist, song.id)
                Toast.makeText(this, "Adicionado a $selectedPlaylist", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
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
        LocalPlayerManager.currentSong?.let { song ->
            binding.includeMiniPlayer.miniPlayerContainer.visibility = View.VISIBLE
            binding.includeMiniPlayer.tvMiniTitle.text = song.title
            binding.includeMiniPlayer.tvMiniArtist.text = song.artist
        
        // CORREÇÃO: Adicionado os parênteses ()
            val icon = if (LocalPlayerManager.isPlaying()) {
                android.R.drawable.ic_media_pause 
            } else {
                android.R.drawable.ic_media_play
            }
            binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
    
        }
    }
}
