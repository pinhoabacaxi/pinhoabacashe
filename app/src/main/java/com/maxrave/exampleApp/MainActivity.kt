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
        val granted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (granted) loadLocalSongs()
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
        findViewById<ImageButton>(R.id.btnScan).setOnClickListener { loadLocalSongs() }
        
        val miniView = findViewById<View>(R.id.includeMiniPlayer)
        if (miniView != null) {
            miniPlayerContainer = miniView as MaterialCardView
            tvMiniTitle = miniView.findViewById(R.id.tvMiniTitle)
            tvMiniArtist = miniView.findViewById(R.id.tvMiniArtist)
            ivMiniArt = miniView.findViewById(R.id.ivMiniArt)
            btnPlayPause = miniView.findViewById(R.id.btnPlayPause)
            btnNext = miniView.findViewById(R.id.btnNext)
            btnPrev = miniView.findViewById(R.id.btnPrev)
            pbMiniProgress = miniView.findViewById(R.id.pbMiniProgress)

            miniPlayerContainer.setOnClickListener {
                startActivity(Intent(this, FullPlayerActivity::class.java))
            }
        }
    }

    private fun setupRecyclerView() {
        // CORREÇÃO: Passando todos os parâmetros que o HybridAdapter agora exige
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                LocalPlayerManager.setQueueAndPlay(filteredList.toList(), position, this)
            },
            onMoreOptionsClick = { item -> /* Abrir BottomSheet */ },
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
        findViewById<Chip>(R.id.chipAll).setOnClickListener { updateDisplayList(currentList) }

        findViewById<Chip>(R.id.chipRecent).setOnClickListener {
            val sorted = currentList.filterIsInstance<Song>().sortedByDescending { it.id }
            updateDisplayList(sorted)
        }

        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            lifecycleScope.launch {
                // Filtra apenas o que o repositório diz que é favorito
                val favorites = currentList.filter { item ->
                    val id = if (item is Song) item.id.toString() else (item as OnlineSong).videoId
                    repository.isFavorite(id)
                }
                updateDisplayList(favorites)
            }
        }

        findViewById<Chip>(R.id.chipOnline).setOnClickListener {
            // Abre sua Activity de busca no YouTube/API
            val intent = Intent(this, Class.forName("com.maxrave.exampleApp.OnlineSearchActivity"))
            startActivity(intent)
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
                Toast.makeText(this@MainActivity, "Erro ao carregar", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkPermissionsAndLoad() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO) 
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        
        if (ContextCompat.checkSelfPermission(this, perm[0]) == PackageManager.PERMISSION_GRANTED) loadLocalSongs()
        else requestPermissionsLauncher.launch(perm)
    }

    private fun setupMiniPlayerObservers() {
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
        val (title, artist, img) = when(item) {
            is Song -> Triple(item.title, item.artist, item.path)
            is OnlineSong -> Triple(item.title, item.author, item.thumbnailUrl)
            else -> Triple("---", "---", null)
        }
        tvMiniTitle.text = title
        tvMiniArtist.text = artist
        Glide.with(this).load(img).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
    }

    private fun toggleFavorite(item: Any) {
        val id = if (item is Song) item.id.toString() else (item as OnlineSong).videoId
        lifecycleScope.launch {
            if (repository.isFavorite(id)) {
                repository.removeFavorite(id)
                Toast.makeText(this@MainActivity, "Removido", Toast.LENGTH_SHORT).show()
            } else {
                repository.addFavorite(id)
                Toast.makeText(this@MainActivity, "Favoritado!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showQuickActionDialog(song: OnlineSong) {
        AlertDialog.Builder(this)
            .setTitle(song.title)
            .setItems(arrayOf("Baixar e Tocar")) { _, _ ->
                // Inicia o Worker de download que você já tem
                val data = androidx.work.workDataOf("URL" to song.url, "FILE_NAME" to "${song.title}.mp3")
                val request = androidx.work.OneTimeWorkRequestBuilder<MusicDownloadWorker>().setInputData(data).build()
                androidx.work.WorkManager.getInstance(this).enqueue(request)
                
                LocalPlayerManager.addToEnd(song)
                Toast.makeText(this, "Baixando...", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun setupSwipeToDismiss() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                val item = filteredList[pos]
                hybridAdapter.removeItem(pos)
                filteredList.removeAt(pos)
                Snackbar.make(rvSongs, "Removido", Snackbar.LENGTH_LONG).setAction("Desfazer") {
                    hybridAdapter.restoreItem(item, pos)
                    filteredList.add(pos, item)
                }.show()
            }
        })
        helper.attachToRecyclerView(rvSongs)
    }
}
