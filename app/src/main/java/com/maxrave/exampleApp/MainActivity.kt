package com.maxrave.exampleApp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
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

class MainActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var hybridAdapter: HybridAdapter
    private var currentList = mutableListOf<Any>() // Lista atual sendo exibida

    // Elementos do Mini Player (do includeMiniPlayer)
    private lateinit var miniPlayerContainer: MaterialCardView
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

        initViews()
        setupRecyclerView()
        setupSwipeToDismiss()
        setupMiniPlayerObservers()
        setupFiltersAndSearch()

        // TODO: Carregar as músicas locais do seu repositório/banco de dados
        // loadLocalSongs() 
    }

    private fun initViews() {
        rvSongs = findViewById(R.id.rvSongs)
        
        // Mini Player Views
        val includeMiniPlayer = findViewById<View>(R.id.includeMiniPlayer)
        miniPlayerContainer = includeMiniPlayer.findViewById(R.id.miniPlayerContainer)
        tvMiniTitle = includeMiniPlayer.findViewById(R.id.tvMiniTitle)
        tvMiniArtist = includeMiniPlayer.findViewById(R.id.tvMiniArtist)
        ivMiniArt = includeMiniPlayer.findViewById(R.id.ivMiniArt)
        btnPlayPause = includeMiniPlayer.findViewById(R.id.btnPlayPause)
        btnNext = includeMiniPlayer.findViewById(R.id.btnNext)
        btnPrev = includeMiniPlayer.findViewById(R.id.btnPrev)
        pbMiniProgress = includeMiniPlayer.findViewById(R.id.pbMiniProgress)

        // Botão para abrir o Full Player ao clicar no Mini Player
        miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        hybridAdapter = HybridAdapter(
            onItemClick = { item, position ->
                // Quando clicar numa música, define a fila inteira e toca
                LocalPlayerManager.setQueueAndPlay(currentList, position, this)
            },
            onMoreOptionsClick = { item ->
                // Aqui você pode abrir o dialog_add_to_playlist
                // showBottomSheetOptions(item)
            }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = hybridAdapter
        }
    }

    // A LÓGICA DO SWIPE QUE PLANEJAMOS
    private fun setupSwipeToDismiss() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                
                // 1. Remove da UI
                val removedItem = hybridAdapter.removeItem(position)
                currentList.removeAt(position) // Mantém a lista local sincronizada
                
                // 2. Remove do Player Logic
                LocalPlayerManager.removeFromQueue(position)

                // 3. Feedback Premium com opção de Desfazer
                Snackbar.make(rvSongs, "Música removida", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") {
                        hybridAdapter.restoreItem(removedItem, position)
                        currentList.add(position, removedItem)
                        LocalPlayerManager.restoreToQueue(position, removedItem)
                    }.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvSongs)
    }

    private fun setupMiniPlayerObservers() {
        // Observa a mudança de faixa para atualizar a UI
        LocalPlayerManager.onTrackChanged = { item ->
            miniPlayerContainer.visibility = View.VISIBLE
            
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

        // Observa o status de Play/Pause
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(iconRes)
        }

        // Se tiver implementado onProgressChanged no Manager, atualize a ProgressBar aqui
        LocalPlayerManager.onProgressChanged = { current, total ->
            if (total > 0) {
                pbMiniProgress.progress = (current * 100) / total
            }
        }

        // Controles do Mini Player
        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    }

    private fun setupFiltersAndSearch() {
        val chipOnline = findViewById<Chip>(R.id.chipOnline)
        chipOnline.setOnClickListener {
            // Navega para a tela de busca do YouTube
            startActivity(Intent(this, OnlineSearchActivity::class.java))
        }

        val searchView = findViewById<SearchView>(R.id.searchViewLibrary)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                // TODO: Filtrar a sua 'currentList' baseada no newText e chamar hybridAdapter.setList()
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Se a música estiver tocando em background, garante que o mini player apareça ao voltar pro app
        if (LocalPlayerManager.currentIndex != -1) {
            miniPlayerContainer.visibility = View.VISIBLE
        }
    }
}
