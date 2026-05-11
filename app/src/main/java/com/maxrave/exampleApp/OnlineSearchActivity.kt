package com.maxrave.exampleApp

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.OnlineSongAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private lateinit var adapter: OnlineSongAdapter
    private lateinit var youtubeRepository: YouTubeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        youtubeRepository = YouTubeRepository(this)

        setupRecyclerView()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = OnlineSongAdapter(
            onItemClick = { onlineSong ->
                showOptionsDialog(onlineSong)
            }
        )
        
        binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)
        binding.rvOnlineResults.adapter = adapter
    }

    private fun setupListeners() {
        // Atalho para buscar ao apertar "Enter" no teclado
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchOnline.text.toString()
                if (query.isNotEmpty()) performSearch(query)
                true
            } else false
        }
    }

    private fun showOptionsDialog(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir Agora", "Baixar Música", "Adicionar à Playlist")
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playOnline(onlineSong)
                    1 -> startDownload(onlineSong)
                    2 -> showPlaylistSelector(onlineSong)
                }
            }
            .show()
    }

    private fun playOnline(onlineSong: OnlineSong) {
        if (onlineSong.streamUrl == null) {
            Toast.makeText(this, "Extraindo áudio...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val info = youtubeRepository.extractMusicInfo(onlineSong.videoId)
                if (info?.streamUrl != null) {
                    // Nota: Você precisará implementar o método playStream no LocalPlayerManager
                    // ou converter OnlineSong para o modelo Song temporariamente.
                    Toast.makeText(this@OnlineSearchActivity, "Reproduzindo...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startDownload(onlineSong: OnlineSong) {
        lifecycleScope.launch {
            Toast.makeText(this@OnlineSearchActivity, "Preparando download...", Toast.LENGTH_SHORT).show()
            youtubeRepository.downloadMusic(onlineSong.videoId)
        }
    }

    private fun showPlaylistSelector(onlineSong: OnlineSong) {
        Toast.makeText(this, "Funcionalidade em desenvolvimento", Toast.LENGTH_SHORT).show()
    }
    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        android.util.Log.d("BUSCA", "Iniciando busca por: $query") // ADICIONE ISSO
    
        lifecycleScope.launch {
            val results = youtubeRepository.searchTracks(query)
            android.util.Log.d("BUSCA", "Resultados encontrados: ${results.size}") // ADICIONE ISSO
        
            binding.progressBar.visibility = View.GONE
            adapter.updateList(results)
        
            if (results.isEmpty()) {
                Toast.makeText(this@OnlineSearchActivity, "Nada encontrado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
