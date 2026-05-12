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
        
        initViews()
        setupRecyclerView()
        setupMiniPlayerObservers()
        setupFiltersAndSearch()
        setupSwipeToDismiss()
        
        checkPermissionsAndLoad()
    }
    hybridAdapter = HybridAdapter(
        onItemClick = { ... },
        onLongItemClick = { item ->
            if (item is OnlineSong) {
                showQuickActionDialog(item)
            }
        }
    )
    
    private fun showQuickActionDialog(song: OnlineSong) {
        AlertDialog.Builder(this)
            .setTitle(song.title)
            .setItems(arrayOf("Adicionar ao final (Baixar e Tocar)")) { _, _ ->
                // 1. Inicia download em background
                startDownload(song, "mp3")
                // 2. Adiciona à fila temporária
                LocalPlayerManager.addToEnd(song)
                Toast.makeText(this, "Baixando e adicionando à fila...", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    private fun initViews() {
        rvSongs = findViewById(R.id.rvSongs)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        progressBar = findViewById(R.id.progressBar)

        // Botão de Scan do seu XML
        findViewById<ImageButton>(R.id.btnScan).setOnClickListener {
            loadLocalSongs()
        }
        
        // CORREÇÃO DO CRASH: O include já é o MaterialCardView
        val miniPlayerView = findViewById<View>(R.id.includeMiniPlayer)
        if (miniPlayerView != null) {
            // Como o ID do include sobrescreve a raiz, miniPlayerView É o MaterialCardView
            miniPlayerContainer = miniPlayerView as MaterialCardView
            
            // Buscamos os filhos dentro dele
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
                // Play na lista atual (filtrada) para manter a ordem da tela
                LocalPlayerManager.setQueueAndPlay(filteredList.toList(), position, this)
            },
            onMoreOptionsClick = { item -> /* Futuro: BottomSheet */ },
            onFavoriteClick = { item -> toggleFavorite(item) }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    private fun setupFiltersAndSearch() {
        // Chip "Todas"
        findViewById<Chip>(R.id.chipAll).setOnClickListener {
            updateDisplayList(currentList)
        }
        // Dentro de setupFiltersAndSearch() na sua MainActivity.kt
        findViewById<Chip>(R.id.chipArtists).setOnClickListener {
            // Agrupa a lista atual por artista e exibe apenas os nomes dos artistas primeiro
            // Ou simplesmente ordena a lista por artista
            filteredList = currentList.filterIsInstance<Song>()
                .sortedBy { it.artist }.toMutableList()
            hybridAdapter.setList(filteredList) [cite: 41]
        }

        findViewById<Chip>(R.id.chipAlbums).setOnClickListener {
            filteredList = currentList.filterIsInstance<Song>()
                .sortedBy { it.album }.toMutableList()
            hybridAdapter.setList(filteredList) [cite: 41]
        }
        // Chip "Recentes" (Ordena por ID decrescente)
        findViewById<Chip>(R.id.chipRecent).setOnClickListener {
            val sorted = currentList.filterIsInstance<Song>().sortedByDescending { it.id }
            updateDisplayList(sorted)
        }

        // Chip "Favoritas" (Lógica simplificada para exemplo)
        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            Toast.makeText(this, "Funcionalidade de favoritos em breve", Toast.LENGTH_SHORT).show()
        }

        // Chip "Buscar Online"
        findViewById<Chip>(R.id.chipOnline).setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        // Barra de Pesquisa
        findViewById<SearchView>(R.id.searchViewLibrary).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun filterList(query: String?) {
        val filter = query ?: ""
        val result = if (filter.isEmpty()) {
            currentList
        } else {
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
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(icon)
        }

        LocalPlayerManager.onProgressChanged = { current, total ->
            if (total > 0) {
                pbMiniProgress.progress = (current * 100 / total).toInt()
            }
        }

        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    }

    private fun updateMiniPlayerUI(item: Any) {
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

    private fun toggleFavorite(item: Any) {
        // Implementar persistência depois
        Toast.makeText(this, "Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
    }

    private fun setupSwipeToDismiss() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedItem = filteredList[position]
                
                hybridAdapter.removeItem(position)
                filteredList.removeAt(position)
                
                Snackbar.make(rvSongs, "Removido da fila atual", Snackbar.LENGTH_LONG)
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
                val icon = if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                btnPlayPause.setImageResource(icon)
            }
        }
    }
}
