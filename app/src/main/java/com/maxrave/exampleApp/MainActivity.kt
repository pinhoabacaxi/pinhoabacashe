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
    
    private var currentList = mutableListOf<Any>() // Lista completa original
    private var filteredList = mutableListOf<Any>() // Lista exibida no momento

    // Elementos do Mini Player
    private lateinit var miniPlayerContainer: MaterialCardView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var ivMiniArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var pbMiniProgress: ProgressBar

    // Launcher para múltiplas permissões (Áudio + Notificações)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) {
            loadLocalSongs()
        } else {
            Toast.makeText(this, "Permissão de arquivos negada.", Toast.LENGTH_SHORT).show()
        }
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
        
        // Inicialização Segura do MiniPlayer (Include)
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

            // Agora o MiniPlayer responde a cliques para abrir o player cheio
            miniPlayerContainer.setOnClickListener {
                startActivity(Intent(this, FullPlayerActivity::class.java))
            }
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

        val listToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (listToRequest.isEmpty()) {
            loadLocalSongs()
        } else {
            requestPermissionsLauncher.launch(listToRequest.toTypedArray())
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                LocalPlayerManager.setQueueAndPlay(filteredList.toList(), position, this)
            },
            onMoreOptionsClick = { item ->
                showBottomSheetOptions(item)
            },
            onFavoriteClick = { item ->
                toggleFavorite(item)
            }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    private fun setupFiltersAndSearch() {
        // Botão para Pesquisa Online
        findViewById<Chip>(R.id.chipOnline).setOnClickListener {
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        // Filtro: Recentes
        findViewById<Chip>(R.id.chipRecent).setOnClickListener {
            filteredList = currentList.filterIsInstance<Song>().sortedByDescending { it.id }.toMutableList()
            hybridAdapter.setList(filteredList)
        }

        // Filtro: Favoritos (Simulado)
        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            // Aqui você filtraria por uma lista de IDs salvos no banco ou SharedPreferences
            Toast.makeText(this, "Filtro de Favoritos Ativado", Toast.LENGTH_SHORT).show()
        }

        // Filtro: Álbuns
        findViewById<Chip>(R.id.chipAlbums).setOnClickListener {
            filteredList = currentList.filterIsInstance<Song>().sortedBy { it.album }.toMutableList()
            hybridAdapter.setList(filteredList)
        }

        // Barra de Pesquisa na Biblioteca
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

    private fun setupMiniPlayerObservers() {
        if (!::miniPlayerContainer.isInitialized) return

        LocalPlayerManager.onTrackChanged = { item ->
            miniPlayerContainer.visibility = View.VISIBLE
            updateMiniPlayerUI(item)
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(iconRes)
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
                Glide.with(this).load(item.uri).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
            }
            is OnlineSong -> {
                tvMiniTitle.text = item.title
                tvMiniArtist.text = item.author
                Glide.with(this).load(item.thumbnailUrl).placeholder(android.R.drawable.ic_media_play).into(ivMiniArt)
            }
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

    private fun toggleFavorite(item: Any) {
        // Lógica para salvar no Banco de Dados ou SharedPreferences
        val title = when(item) {
            is Song -> item.title
            is OnlineSong -> item.title
            else -> "Música"
        }
        Toast.makeText(this, "$title adicionada aos favoritos", Toast.LENGTH_SHORT).show()
    }

    private fun showBottomSheetOptions(item: Any) {
        Toast.makeText(this, "Opções abertas para o item", Toast.LENGTH_SHORT).show()
    }

    private fun setupSwipeToDismiss() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedItem = filteredList[position]
                hybridAdapter.removeItem(position)
                filteredList.removeAt(position)
                
                Snackbar.make(rvSongs, "Removido da lista atual", Snackbar.LENGTH_LONG)
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
        // Verificação de segurança para evitar o crash de UninitializedProperty
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
