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
                handleOnlineClick(onlineSong)
            }
        )
        binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)
        binding.rvOnlineResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchOnline.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun handleOnlineClick(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir Agora (Stream)", "Baixar Música", "Adicionar à Playlist")
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startStreaming(onlineSong)
                    1 -> startDownload(onlineSong)
                    2 -> showPlaylistSelector(onlineSong)
                }
            }
            .show()
    }

    private fun startStreaming(onlineSong: OnlineSong) {
        Toast.makeText(this, "Obtendo áudio...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val fullSong = youtubeRepository.extractMusicInfo(onlineSong.videoId)
            if (fullSong?.streamUrl != null) {
                LocalPlayerManager.playOnline(fullSong, this@OnlineSearchActivity)
            } else {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao obter link de áudio", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "Funcionalidade de playlist em breve", Toast.LENGTH_SHORT).show()
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val results = youtubeRepository.searchTracks(query)
            binding.progressBar.visibility = View.GONE
            if (results.isEmpty()) {
                Toast.makeText(this@OnlineSearchActivity, "Nenhum resultado encontrado", Toast.LENGTH_SHORT).show()
            } else {
                adapter.updateList(results)
            }
        }
    }
}
