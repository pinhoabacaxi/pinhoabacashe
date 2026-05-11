package com.maxrave.exampleApp

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.bumptech.glide.Glide
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
        if (permissions.entries.all { it.value }) loadSongs()
        else Toast.makeText(this, "Permissão negada para ler músicas.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inicializar Repositórios e Managers
        musicLoader = MusicLoader(this)
        recentManager = RecentSongsManager(this)
        favoriteManager = FavoriteManager(this)
        playlistManager = PlaylistManager(this)
        
        // 2. Inicializar o Player Global (Obrigatório antes de usar)
        LocalPlayerManager.init(this)

        setupRecyclerView()
        setupListeners()
        setupPlayerObservers() // Monitora mudanças para atualizar o Mini Player
        checkPermissions()
    }

    private fun setupPlayerObservers() {
        // Atualiza a interface quando a música muda (inclusive via 'Next' na notificação)
        LocalPlayerManager.onTrackChanged = { song ->
            updateMiniPlayerUI(song)
        }

        // Atualiza o botão de play/pause quando o estado do player muda
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            songs = currentList,
            favoriteManager = favoriteManager,
            onSongClick = { song ->
                val position = currentList.indexOf(song)
                if (position != -1) {
                    LocalPlayerManager.startPlaying(this, currentList, position)
                    updateMiniPlayerUI(song)
                }
            },
            onFavClick = { song ->
                favoriteManager.toggleFavorite(song.id)
                songAdapter.notifyDataSetChanged()
            },
            onLongClick = { song ->
                // Aqui você pode abrir seu showPlaylistOptionsDialog(song) futuramente
                Toast.makeText(this, "Opções para: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Busca na Biblioteca Local
        binding.searchViewLibrary.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                songAdapter.filter(newText ?: "")
                return true
            }
        })

        // Filtros (Chips)
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> songAdapter.updateList(currentList)
                R.id.chipFavorites -> {
                    val favs = currentList.filter { favoriteManager.isFavorite(it.id) }
                    songAdapter.updateList(favs)
                }
                R.id.chipRecent -> {
                    // Assume que seu RecentSongsManager tem o método getRecentSongs
                    val recents = recentManager.getRecentSongs(currentList)
                    songAdapter.updateList(recents)
                }
            }
        }

        // Botão para Busca Online (YouTube)
        binding.chipOnline.setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        // Mini Player Controles
        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
        }
        binding.includeMiniPlayer.btnNext.setOnClickListener {
            LocalPlayerManager.next(this)
        }
        binding.includeMiniPlayer.btnPrev.setOnClickListener {
            LocalPlayerManager.previous(this)
        }
        
        // Abrir tela cheia (FullPlayerActivity)
        binding.includeMiniPlayer.root.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }

        binding.btnScan.setOnClickListener { loadSongs() }
    }

    private fun updateMiniPlayerUI(song: Song) {
        binding.includeMiniPlayer.root.visibility = View.VISIBLE
        binding.includeMiniPlayer.tvMiniTitle.text = song.title
        binding.includeMiniPlayer.tvMiniArtist.text = song.artist

        // Carregar capa do álbum
        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )

        Glide.with(this)
            .load(albumArtUri)
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .into(binding.includeMiniPlayer.ivMiniArt)

        // Sincronizar ícone de Play/Pause
        val icon = if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.includeMiniPlayer.btnPlayPause.setImageResource(icon)
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

    override fun onResume() {
        super.onResume()
        // Garante que o mini player esteja visível e atualizado se houver música tocando
        LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
        if (::songAdapter.isInitialized) {
            songAdapter.notifyDataSetChanged()
        }
    }
}
