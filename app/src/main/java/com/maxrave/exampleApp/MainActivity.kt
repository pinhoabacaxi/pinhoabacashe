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
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var hybridAdapter: HybridAdapter
    private lateinit var musicLoader: MusicLoader
    private lateinit var tvEmptyState: TextView
    private lateinit var progressBar: ProgressBar
    
    private var currentList = mutableListOf<Any>() 
    private var filteredList = mutableListOf<Any>() 

    // Mini Player
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
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) loadLocalSongs()
        else Toast.makeText(this, "Permissão necessária para ler músicas.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicLoader = MusicLoader(this)
        
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

        // Botão de Scan do seu XML
        findViewById<ImageButton>(R.id.btnScan).setOnClickListener {
            loadLocalSongs()
            Toast.makeText(this, "Atualizando biblioteca...", Toast.LENGTH_SHORT).show()
        }
        
        // Setup Seguro do MiniPlayer
        val miniPlayerInclude = findViewById<View>(R.id.includeMiniPlayer)
        if (miniPlayerInclude != null) {
            miniPlayerContainer = miniPlayerInclude.findViewById(R.id.miniPlayerContainer)
            tvMiniTitle = miniPlayerInclude.findViewById(R.id.tvMiniTitle)
            tvMiniArtist = miniPlayerInclude.findViewById(R.id.tvMiniArtist)
            ivMiniArt = miniPlayerInclude.findViewById(R.id.ivMiniArt)
            btnPlayPause = miniPlayerInclude.findViewById(R.id.btnPlayPause)
            btnNext = miniPlayerInclude.findViewById(R.id.btnNext)
            btnPrev = miniPlayerInclude.findViewById(R.id.btnPrev)
            pbMiniProgress = miniPlayerInclude.findViewById(R.id.pbMiniProgress)

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
            onMoreOptionsClick = { item -> /* Abrir Menu */ },
            onFavoriteClick = { item -> /* Lógica de Favoritar */ }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    private fun setupFiltersAndSearch() {
        // IDs sincronizados com seu XML
        findViewById<Chip>(R.id.chipAll).setOnClickListener {
            updateDisplayList(currentList)
        }

        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            // Exemplo: Filtrar apenas locais (você pode mudar para sua lógica de favoritos)
            val favs = currentList.filterIsInstance<Song>().filter { it.title.contains("Loved", true) }
            updateDisplayList(favs)
        }

        findViewById<Chip>(R.id.chipRecent).setOnClickListener {
            val recent = currentList.filterIsInstance<Song>().sortedByDescending { it.id }
            updateDisplayList(recent)
        }

        findViewById<Chip>(R.id.chipOnline).setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        findViewById<SearchView>(R.id.searchViewLibrary).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSearch(newText)
                return true
            }
        })
    }

    private fun updateDisplayList(newList: List<Any>) {
        filteredList = newList.toMutableList()
        hybridAdapter.setList(filteredList)
        tvEmptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun filterSearch(query: String?) {
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
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            loadLocalSongs()
        } else {
            requestPermissionsLauncher.launch(perms)
        }
    }

    private fun setupMiniPlayerObservers() {
        if (!::miniPlayerContainer.isInitialized) return

        LocalPlayerManager.onTrackChanged = { item ->
            miniPlayerContainer.visibility = View.VISIBLE
            updateMiniUI(item)
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        LocalPlayerManager.onProgressChanged = { current, total ->
            if (total > 0) pbMiniProgress.progress = (current * 100 / total).toInt()
        }

        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    }

    private fun updateMiniUI(item: Any) {
        when (item) {
            is Song -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.artist
                Glide.with(this).load(item.path).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
            }
            is OnlineSong -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.author
                Glide.with(this).load(item.thumbnailUrl).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
            }
        }
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
                updateMiniUI(current)
                btnPlayPause.setImageResource(if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            }
        }
    }
}
