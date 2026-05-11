package com.maxrave.exampleApp

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SongAdapter
import com.maxrave.exampleApp.databinding.ActivityMainBinding
import com.maxrave.exampleApp.model.OnlineSong
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
    
    // Repositórios para a parte Online
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var downloadHelper: DownloadHelper
    
    private var allSongs: List<Song> = emptyList()

    // 1. Único Receiver para Downloads
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Recarrega a biblioteca local quando um download termina
            loadSongs()
            Toast.makeText(context, "Biblioteca atualizada com novo download!", Toast.LENGTH_SHORT).show()
        }
    }

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

        // Inicialização
        LocalPlayerManager.init(this)
        initRepositories()
        
        // Registro do Receiver de Download
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

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
        
        // Inicia os novos ajudantes online
        youtubeRepository = YouTubeRepository(this)
        downloadHelper = DownloadHelper(this)
        
        LocalPlayerManager.initRecentManager(this)
    }

    // --- LÓGICA ONLINE (FASE 9) ---

    fun onOnlineSongClicked(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir agora (Stream)", "Baixar para o dispositivo")
        
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playStream(onlineSong)
                    1 -> triggerDownload(onlineSong.videoId)
                }
            }
            .show()
    }

    private fun playStream(onlineSong: OnlineSong) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE // Certifique-se de ter um ProgressBar no XML
            val extracted = youtubeRepository.extractMusicInfo(onlineSong.videoId)
            binding.progressBar.visibility = View.GONE

            if (extracted?.streamUrl != null) {
                LocalPlayerManager.playOnline(extracted, this@MainActivity)
                updateMiniPlayerUI(LocalPlayerManager.currentSong!!)
                Toast.makeText(this@MainActivity, "Iniciando Stream...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Erro ao extrair áudio.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerDownload(videoId: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Preparando download...", Toast.LENGTH_SHORT).show()
            youtubeRepository.downloadMusic(videoId)
        }
    }

    // --- CONFIGURAÇÕES DE UI ---

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
                Toast.makeText(this, "Opções para: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = songAdapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            songAdapter.filter(text.toString())
        }
        binding.btnScan.setOnClickListener { loadSongs() }
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> songAdapter.updateList(allSongs)
                R.id.chipFavorites -> songAdapter.updateList(allSongs.filter { favoriteManager.isFavorite(it.id) })
                R.id.chipRecent -> {
                    val recentIds = recentManager.getRecentIds()
                    val recentSongs = allSongs.filter { recentIds.contains(it.id.toString()) }
                        .sortedByDescending { recentIds.indexOf(it.id.toString()) }
                    songAdapter.updateList(recentSongs)
                }
                R.id.chipPlaylists -> {
                    showBrowsePlaylistsDialog()
                    binding.chipGroupFilters.clearCheck()
                }
                R.id.chipOnline -> {
                    // Aqui você abre sua Activity de busca online
                    val intent = Intent(this, OnlineSearchActivity::class.java)
                    startActivity(intent)
                    binding.chipGroupFilters.clearCheck()
                }
            }
        }
    }

    private fun setupRestoreLastSong() {
        val playerPrefs = PlayerPrefs(this)
        val lastId = playerPrefs.getLastSongId()
        
        lifecycleScope.launch {
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
        AlertDialog.Builder(this)
            .setTitle("Minhas Playlists")
            .setItems(playlists.toTypedArray()) { _, which ->
                val intent = Intent(this, PlaylistSongsActivity::class.java)
                intent.putExtra("PLAYLIST_NAME", playlists[which])
                startActivity(intent)
            }.show()
    }

    private fun setupClickListeners() {
        binding.includeMiniPlayer.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            LocalPlayerManager.currentSong?.let { updateMiniPlayerUI(it) }
        }
        binding.includeMiniPlayer.miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }
    }

    private fun checkPermissions() {
        val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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

    // 2. Implementação do onDestroy para segurança
    override fun onDestroy() {
        super.onDestroy()
        // Importante: desregistrar para evitar que o app tente atualizar uma tela que não existe mais
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Receiver já estava desregistrado
        }
    }
}
