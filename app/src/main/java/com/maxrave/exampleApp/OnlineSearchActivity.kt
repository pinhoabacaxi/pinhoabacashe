package com.maxrave.exampleApp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SearchAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var youtubeRepository: YouTubeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        youtubeRepository = YouTubeRepository(this)
        setupRecyclerView()
        setupSearchInput()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter { videoMeta ->
            // Ao clicar, extraímos o link de streaming e tocamos
            startStreaming(videoMeta)
        }
        
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupSearchInput() {
        binding.etSearchOnline.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotEmpty()) {
                    viewModel.search(query)
                    hideKeyboard()
                }
                true
            } else false
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        searchAdapter.submitList(state.results)
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@OnlineSearchActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // BYPASS/Extração do YouTube
                val streamData = youtubeRepository.extractAudioLink(videoMeta.videoId)
                
                if (streamData != null) {
                    // Convertemos o VideoMeta da busca para o nosso modelo OnlineSong
                    val onlineSong = OnlineSong(
                        id = videoMeta.videoId,
                        title = videoMeta.title,
                        author = videoMeta.author,
                        thumbnailUrl = videoMeta.thumbnailUrl,
                        streamingUrl = streamData.url, // URL real do servidor do YT
                        duration = videoMeta.duration
                    )
                    
                    binding.progressBar.visibility = View.GONE
                    
                    // Lógica Híbrida: Toca a música e a coloca na fila atual
                    LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                    
                    Toast.makeText(this@OnlineSearchActivity, "A tocar: ${videoMeta.title}", Toast.LENGTH_SHORT).show()
                    finish() // Opcional: volta para a tela principal para ver o player
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@OnlineSearchActivity, "Erro ao carregar áudio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchOnline.windowToken, 0)
    }
}
