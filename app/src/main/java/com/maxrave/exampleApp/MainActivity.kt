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
    
    private var currentList = mutableListOf<Any>() // Lista original (completa)
    private var filteredList = mutableListOf<Any>() // Lista que o adapter exibe

    // Elementos do Mini Player
    private lateinit var miniPlayerContainer: MaterialCardView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var ivMiniArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var pbMiniProgress: ProgressBar

    // Launcher para permissões
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadLocalSongs()
        else Toast.makeText(this, "Permissão necessária para carregar músicas", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicLoader = MusicLoader(this)
        
        initViews()
        setupRecyclerView()
        setupSwipeToDismiss()
        setupMiniPlayerObservers()
        setupFiltersAndSearch()
        
        checkPermissionsAndLoad()
    }

    private fun initViews() {
    rvSongs = findViewById(R.id.rvSongs)
    
    // Tentamos encontrar o include primeiro
    val miniPlayerInclude = findViewById<View>(R.id.includeMiniPlayer)
    
    if (miniPlayerInclude != null) {
            // Buscamos os IDs de dentro do include
            val container = miniPlayerInclude.findViewById<MaterialCardView>(R.id.miniPlayerContainer)
            
            if (container != null) {
                // Se o container existe, inicializamos tudo com segurança
                miniPlayerContainer = container
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
            } else {
                android.util.Log.e("FATAL_ERROR", "ID 'miniPlayerContainer' não encontrado no layout incluído!")
            }
        } else {
            android.util.Log.e("FATAL_ERROR", "O include 'includeMiniPlayer' não existe na activity_main.xml!")
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                // Passa a lista filtrada para garantir que o 'Next' siga a ordem atual da tela
                LocalPlayerManager.setQueueAndPlay(filteredList.toList(), position, this)
            },
            onMoreOptionsClick = { item ->
                showBottomSheetOptions(item)
            }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    private fun setupSwipeToDismiss() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedItem = filteredList[position]
                
                // 1. Remove da UI e da lista filtrada
                hybridAdapter.removeItem(position)
                filteredList.removeAt(position)
                
                // 2. Remove da lista principal e da fila do Player
                currentList.remove(removedItem)
                LocalPlayerManager.removeFromQueue(position)

                Snackbar.make(rvSongs, "Música removida da fila", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") {
                        hybridAdapter.restoreItem(removedItem, position)
                        filteredList.add(position, removedItem)
                        currentList.add(removedItem)
                        LocalPlayerManager.restoreToQueue(position, removedItem)
                    }.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvSongs)
    }

    private fun setupMiniPlayerObservers() {
        // Observa mudanças de música
        if (!::btnPlayPause.isInitialized) return

        LocalPlayerManager.onTrackChanged = { item ->
            miniPlayerContainer.visibility = View.VISIBLE
            updateMiniPlayerUI(item)
        }
        // Observa Play/Pause
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(iconRes)
        }

        // Observa Progresso
        LocalPlayerManager.onProgressChanged = { current, total ->
            if (total > 0) {
                pbMiniProgress.progress = (current * 100) / total
            }
        }

        // Click Listeners
        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    }

    private fun updateMiniPlayerUI(item: Any) {
        when (item) {
            is Song -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.artist
                Glide.with(this)
                    .load(item.path)
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(ivMiniArt)
            }
            is OnlineSong -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.author
                Glide.with(this)
                    .load(item.thumbnailUrl)
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(ivMiniArt)
            }
        }
    }

    private fun setupFiltersAndSearch() {
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

    private fun filterList(query: String?) {
        val filter = query ?: ""
        filteredList = if (filter.isEmpty()) {
            currentList.toMutableList()
        } else {
            currentList.filter {
                when (it) {
                    is Song -> it.title.contains(filter, true) || it.artist.contains(filter, true)
                    is OnlineSong -> it.title.contains(filter, true) || it.author.contains(filter, true)
                    else -> false
                }
            }.toMutableList()
        }
        hybridAdapter.setList(filteredList)
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadLocalSongs()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadLocalSongs() {
        lifecycleScope.launch {
            try {
                val songs = musicLoader.loadLocalSongs()
                currentList = songs.toMutableList()
                filteredList = songs.toMutableList()
                hybridAdapter.setList(filteredList)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar músicas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBottomSheetOptions(item: Any) {
        // Futura implementação
        Toast.makeText(this, "Opções para: $item", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Sincroniza a UI do mini player caso algo tenha mudado (ex: volta do FullPlayer)
        val current = LocalPlayerManager.getCurrentTrack()
        if (current != null) {
            miniPlayerContainer.visibility = View.VISIBLE
            updateMiniPlayerUI(current)
            val icon = if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(icon)
        }
    }
}
