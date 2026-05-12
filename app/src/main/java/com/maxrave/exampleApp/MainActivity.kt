package com.maxrave.exampleApp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.maxrave.exampleApp.adapter.HybridAdapter
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.MusicLoader
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.exampleApp.service.MusicDownloadWorker
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var hybridAdapter: HybridAdapter
    private lateinit var musicLoader: MusicLoader
    private lateinit var repository: PlaylistRepository
    private lateinit var tvEmptyState: TextView
    private lateinit var progressBar: ProgressBar
    
    private var currentList = mutableListOf<Any>() 
    private var filteredList = mutableListOf<Any>() 

    // Elementos do Mini Player
    private lateinit var miniPlayerContainer: MaterialCardView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var ivMiniArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var pbMiniProgress: ProgressBar

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] 
            ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) loadLocalSongs()
        else Toast.makeText(this, "Permissão necessária para carregar músicas", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicLoader = MusicLoader(this)
        repository = PlaylistRepository(this)
        
        initViews()
        setupRecyclerView()
        setupMiniPlayerObservers()
        setupFiltersAndSearch()
        setupSwipeToDismiss()
        
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        rvSongs = findViewById(R.id.rvSongs)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnScan).setOnClickListener {
            loadLocalSongs()
        }
        
        val miniPlayerView = findViewById<View>(R.id.includeMiniPlayer)
        if (miniPlayerView != null) {
            miniPlayerContainer = miniPlayerView as MaterialCardView
            tvMiniTitle = miniPlayerView.findViewById(R.id.tvMiniTitle)
            tvMiniArtist = miniPlayerView.findViewById(R.id.tvMiniArtist)
            ivMiniArt = miniPlayerView.findViewById(R.id.ivMiniArt)
            btnPlayPause = miniPlayerView.findViewById(R.id.btnPlayPause)
            btnNext = miniPlayerView.findViewById(R.id.btnNext)
            btnPrev = miniPlayerView.findViewById(R.id.btnPrev)
            pbMiniProgress = miniPlayerView.findViewById(R.id.pbMiniProgress)

            miniPlayerContainer.setOnClickListener {
                startActivity(Intent(this, FullPlayerActivity::class.java))
            }
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                LocalPlayerManager.setQueueAndPlay(filteredList.toList(), position, this)
            },
            onMoreOptionsClick = { item -> showBottomSheetOptions(item) },
            onFavoriteClick = { item -> toggleFavorite(item) },
            onLongItemClick = { item ->
                if (item is OnlineSong) showQuickActionDialog(item)
            }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    private fun setupFiltersAndSearch() {
        findViewById<Chip>(R.id.chipAll).setOnClickListener {
            updateDisplayList(currentList)
        }

        findViewById<Chip>(R.id.chipArtists).setOnClickListener {
            val sorted = currentList.filterIsInstance<Song>().sortedBy { it.artist }
            updateDisplayList(sorted)
        }

        findViewById<Chip>(R.id.chipAlbums).setOnClickListener {
            val sorted = currentList.filterIsInstance<Song>().sortedBy { it.album }
            updateDisplayList(sorted)
        }

        findViewById<Chip>(R.id.chipRecent).setOnClickListener {
            val sorted = currentList.filterIsInstance<Song>().sortedByDescending { it.id }
            updateDisplayList(sorted)
        }

        findViewById<Chip>(R.id.chipOnline).setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        findViewById<SearchView>(R.id.searchViewLibrary).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun showQuickActionDialog(song: OnlineSong) {
        AlertDialog.Builder(this)
            .setTitle(song.title)
            .setMessage("Deseja baixar e adicionar ao final da fila?")
            .setPositiveButton("Sim") { _, _ ->
                startDownload(song, "mp3")
                LocalPlayerManager.addToEnd(song)
                Toast.makeText(this, "Baixando e adicionando...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun startDownload(song: OnlineSong, format: String) {
        val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(androidx.work.workDataOf(
                "URL" to song.url,
                "FILE_NAME" to "${song.title.replace(" ", "_")}.$format"
            ))
            .addTag("download_${song.videoId}")
            .build()

        androidx.work.WorkManager.getInstance(this).enqueue(downloadRequest)
    }

    private fun filterList(query: String?) {
        val filter = query ?: ""
        val result = if (filter.isEmpty()) currentList else {
            currentList.filter {
                when (it) {
                    is Song -> it.title.contains(filter, true) || it.artist.contains(filter, true)
                    is OnlineSong -> it.title.contains(filter, true) || it.author.contains(filter, true)
                    else -> false
                }
            }
        }
        updateDisplayList(result)
    }

    private fun updateDisplayList(newList: List<Any>) {
        filteredList = newList.toMutableList()
        hybridAdapter.setList(filteredList)
        tvEmptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadLocalSongs() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val songs = musicLoader.loadLocalSongs()
                currentList = songs.toMutableList()
                updateDisplayList(currentList)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar músicas", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkPermissionsAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) loadLocalSongs()
        else requestPermissionsLauncher.launch(missing.toTypedArray())
    }

    private fun setupMiniPlayerObservers() {
        if (!::miniPlayerContainer.isInitialized) return

        LocalPlayerManager.onTrackChanged = { item ->
            miniPlayerContainer.visibility = View.VISIBLE
            updateMiniPlayerUI(item)
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        LocalPlayerManager.onProgressChanged = { current, total ->
            if (total > 0) pbMiniProgress.progress = (current * 100 / total)
        }

        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    }

    private fun updateMiniPlayerUI(item: Any) {
        val title = if (item is Song) item.title else (item as OnlineSong).title
        val artist = if (item is Song) item.artist else (item as OnlineSong).author
        val path = if (item is Song) item.path else (item as OnlineSong).thumbnailUrl

        tvMiniTitle.text = title
        tvMiniArtist.text = artist
        Glide.with(this).load(path).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
    }

    private fun toggleFavorite(item: Any) {
        val id = if (item is Song) item.id.toString() else (item as OnlineSong).videoId
        lifecycleScope.launch {
            if (repository.isFavorite(id)) {
                repository.removeFavorite(id)
                Toast.makeText(this@MainActivity, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            } else {
                repository.addFavorite(id)
                Toast.makeText(this@MainActivity, "Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBottomSheetOptions(item: Any) {
        // Implementar abertura do OptionsBottomSheet aqui
    }

    private fun setupSwipeToDismiss() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedItem = filteredList[position]
                hybridAdapter.removeItem(position)
                filteredList.removeAt(position)
                
                Snackbar.make(rvSongs, "Removido da fila", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") {
                        hybridAdapter.restoreItem(removedItem, position)
                        filteredList.add(position, removedItem)
                    }.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvSongs)
    }

    override fun onResume() {
        super.onResume()
        if (::miniPlayerContainer.isInitialized) {
            val current = LocalPlayerManager.getCurrentTrack()
            if (current != null) {
                miniPlayerContainer.visibility = View.VISIBLE
                updateMiniPlayerUI(current)
                btnPlayPause.setImageResource(if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            }
        }
    }
}
