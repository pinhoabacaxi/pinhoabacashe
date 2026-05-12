package com.maxrave.exampleApp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.chip.Chip
import com.maxrave.exampleApp.adapter.HybridAdapter
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.MusicLoader
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.exampleApp.service.MusicDownloadWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var hybridAdapter: HybridAdapter
    private lateinit var musicLoader: MusicLoader
    private lateinit var repository: PlaylistRepository
    
    private var currentList = mutableListOf<Any>()
    private var filteredList = mutableListOf<Any>()

    // Variáveis do Mini Player (Sincronizadas com seu layoutplayer.xml)
    private lateinit var miniPlayerContainer: View
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var ivMiniArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var pbMiniProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicLoader = MusicLoader(this)
        repository = PlaylistRepository(this)
        rvSongs = findViewById(R.id.rvSongs)
        
        setupMiniPlayerUI()
        setupRecyclerView()
        setupFiltersAndSearch()
        setupSwipeToDismiss() // Agora dentro da classe corretamente
        checkPermissionsAndLoad()
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                LocalPlayerManager.setQueueAndPlay(filteredList, position, this)
                updateMiniPlayerUI(item)
            },
            onMoreOptionsClick = { item -> showSongOptions(item) },
            onFavoriteClick = { item -> toggleFavorite(item) },
            onLongItemClick = { item -> showSongOptions(item) }
        )

        rvSongs.layoutManager = LinearLayoutManager(this)
        rvSongs.adapter = hybridAdapter
    }

    private fun setupFiltersAndSearch() {
        findViewById<Chip>(R.id.chipAll).setOnClickListener { updateDisplayList(currentList) }
    
        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            lifecycleScope.launch {
                val favorites = currentList.filter { item ->
                    val id = if (item is Song) item.id.toString() else (item as OnlineSong).videoId
                    repository.isFavorite(id)
                }
                updateDisplayList(favorites)
            }
        }
    
        findViewById<Chip>(R.id.chipPlaylists).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        findViewById<SearchView>(R.id.searchViewLibrary).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun filterList(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            currentList
        } else {
            currentList.filter { item ->
                when (item) {
                    is Song -> item.title.contains(query, true) || item.artist.contains(query, true)
                    is OnlineSong -> item.title.contains(query, true) || item.author.contains(query, true)
                    else -> false
                }
            }
        }
        updateDisplayList(filtered.toMutableList())
    }

    private fun updateDisplayList(newList: List<Any>) {
        filteredList.clear()
        filteredList.addAll(newList)
        hybridAdapter.setList(filteredList)
        findViewById<TextView>(R.id.tvEmptyState).visibility = if (newList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleFavorite(item: Any) {
        lifecycleScope.launch {
            val id = if (item is Song) item.id.toString() else (item as OnlineSong).videoId
            if (repository.isFavorite(id)) {
                repository.removeFavorite(id)
                Toast.makeText(this@MainActivity, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            } else {
                repository.addFavorite(id)
                Toast.makeText(this@MainActivity, "Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSongOptions(item: Any) {
        val options = if (item is OnlineSong) arrayOf("Baixar e Tocar", "Adicionar à Playlist") 
                      else arrayOf("Adicionar à Playlist", "Informações")

        AlertDialog.Builder(this)
            .setTitle(if (item is Song) item.title else (item as OnlineSong).title)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Baixar e Tocar" -> startDownload(item as OnlineSong)
                    "Adicionar à Playlist" -> {} // Implementar lógica de playlist
                }
            }.show()
    }

    private fun startDownload(song: OnlineSong) {
        val data = workDataOf("URL" to song.url, "FILE_NAME" to "${song.title}.mp3")
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>().setInputData(data).build()
        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Baixando...", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) loadSongs() else Toast.makeText(this, "Permissão necessária para carregar músicas", Toast.LENGTH_SHORT).show()
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            val songs = musicLoader.loadLocalSongs()
            currentList.clear()
            currentList.addAll(songs)
            updateDisplayList(currentList)
        }
    }

    private fun setupMiniPlayerUI() {
        miniPlayerContainer = findViewById(R.id.includeMiniPlayer)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        ivMiniArt = findViewById(R.id.ivMiniArt)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        pbMiniProgress = findViewById(R.id.pbMiniProgress)

        btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            updatePlayPauseButton()
        }

        btnNext.setOnClickListener { 
            LocalPlayerManager.playNext(this)
            LocalPlayerManager.getCurrentTrack()?.let { updateMiniPlayerUI(it) }
        }

        btnPrev.setOnClickListener { 
            LocalPlayerManager.playPrevious(this)
            LocalPlayerManager.getCurrentTrack()?.let { updateMiniPlayerUI(it) }
        }

        miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }
    }

    private fun updateMiniPlayerUI(item: Any) {
        miniPlayerContainer.visibility = View.VISIBLE
        when (item) {
            is Song -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.artist
                ivMiniArt.setImageResource(android.R.drawable.ic_media_play)
            }
            is OnlineSong -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.author
                Glide.with(this).load(item.thumbnailUrl).into(ivMiniArt)
            }
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        val icon = if (LocalPlayerManager.isPlaying()) 
            android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        btnPlayPause.setImageResource(icon)
    }

    private fun setupSwipeToDismiss() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                val item = filteredList[pos]
                hybridAdapter.removeItem(pos)
                filteredList.removeAt(pos)
                Snackbar.make(rvSongs, "Removido da fila", Snackbar.LENGTH_LONG).setAction("Desfazer") {
                    hybridAdapter.restoreItem(item, pos)
                    filteredList.add(pos, item)
                }.show()
            }
        })
        helper.attachToRecyclerView(rvSongs)
    }
}
