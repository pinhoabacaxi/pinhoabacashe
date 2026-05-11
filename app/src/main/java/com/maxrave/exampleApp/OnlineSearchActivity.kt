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
import kotlinx.coroutines.launch

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private lateinit var adapter: OnlineSongAdapter
    private lateinit var youtubeRepository: YouTubeRepository // Repositório adicionado

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        [span_51](start_span)binding = ActivityOnlineSearchBinding.inflate(layoutInflater)[span_51](end_span)
        setContentView(binding.root)

        youtubeRepository = YouTubeRepository(this) // Inicialização
        setupRecyclerView()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = OnlineSongAdapter(
            onItemClick = { onlineSong ->
                [span_52](start_span)handleOnlineClick(onlineSong)[span_52](end_span)
            }
        )
        [span_53](start_span)binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)[span_53](end_span)
        [span_54](start_span)binding.rvOnlineResults.adapter = adapter[span_54](end_span)
    }

    private fun setupListeners() {
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchOnline.text.toString().trim()
                if (query.isNotEmpty()) {
                    [span_55](start_span)performSearch(query)[span_55](end_span)
                }
                true
            } else {
                false
            }
        [span_56](start_span)}
    }

    private fun handleOnlineClick(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir Agora (Stream)", "Baixar Música", "Adicionar à Playlist")[span_56](end_span)
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    [span_57](start_span)0 -> startStreaming(onlineSong)[span_57](end_span)
                    [span_58](start_span)1 -> startDownload(onlineSong)[span_58](end_span)
                    [span_59](start_span)2 -> showPlaylistSelector(onlineSong)[span_59](end_span)
                }
            }
            .show()
    }

    private fun startStreaming(onlineSong: OnlineSong) {
        Toast.makeText(this, "Obtendo link de áudio...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            // Extrai as informações completas (incluindo streamUrl) antes de tocar
            val fullSong = youtubeRepository.extractMusicInfo(onlineSong.videoId)
            
            if (fullSong?.streamUrl != null) {
                // LocalPlayerManager deve ter um método para tocar stream
                LocalPlayerManager.playOnline(fullSong, this@OnlineSearchActivity)
            } else {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao obter áudio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(onlineSong: OnlineSong) {
        Toast.makeText(this, "Iniciando download...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            youtubeRepository.downloadMusic(onlineSong.videoId)
        }
    }

    private fun showPlaylistSelector(onlineSong: OnlineSong) {
        Toast.makeText(this, "Funcionalidade em desenvolvimento", Toast.LENGTH_SHORT).show()
    }

    private fun performSearch(query: String) {
        [span_60](start_span)binding.progressBar.visibility = View.VISIBLE[span_60](end_span)
        
        lifecycleScope.launch {
            val results = youtubeRepository.searchTracks(query) // Chama a busca real do repositório
            
            [span_61](start_span)binding.progressBar.visibility = View.GONE[span_61](end_span)
            if (results.isEmpty()) {
                [span_62](start_span)Toast.makeText(this@OnlineSearchActivity, "Nenhum resultado encontrado", Toast.LENGTH_SHORT).show()[span_62](end_span)
            } else {
                [span_63](start_span)adapter.updateList(results)[span_63](end_span)
            }
        }
    }
}
