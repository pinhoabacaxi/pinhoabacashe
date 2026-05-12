package com.maxrave.exampleApp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
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
            startStreaming(videoMeta)
        }
        
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupSearchInput() {
        // CORREÇÃO: Aceder ao SearchView de forma segura usando o ViewBinding
        // Se o ID no XML for 'searchViewOnline', o binding deve reconhecer. 
        // Caso contrário, usamos o findViewById dentro do container.
        val searchView = binding.cardSearchContainer.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchViewOnline)
        
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    viewModel.performSearch(query)
                    hideKeyboard()
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        // Esconder o estado vazio ao carregar
                        binding.emptyStateContainer.visibility = View.GONE
                    }
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        searchAdapter.submitList(state.results)
                        if (state.results.isEmpty()) {
                            binding.emptyStateContainer.visibility = View.VISIBLE
                        }
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
                // CORREÇÃO: Agora chama 'extractAudioLink' que foi atualizada no Repository
                val streamData = youtubeRepository.extractAudioLink(videoMeta.videoId)
                
                if (streamData != null) {
                    // Mapeamento corrigido para o modelo OnlineSong
                    val onlineSong = OnlineSong(
                        videoId = videoMeta.videoId,
                        title = videoMeta.title,
                        author = videoMeta.author,
                        thumbnailUrl = videoMeta.thumbnailUrl,
                        url = streamData.url, // URL final de áudio
                        duration = videoMeta.duration.toString() // Convertido para String
                    )
                    
                    binding.progressBar.visibility = View.GONE
                    
                    // Envia para o Manager atualizar a fila híbrida e começar o play
                    LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                    
                    Toast.makeText(this@OnlineSearchActivity, "Iniciando: ${videoMeta.title}", Toast.LENGTH_SHORT).show()
                    
                    // Volta para a tela principal para mostrar o Mini Player
                    finish() 
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@OnlineSearchActivity, "Falha ao extrair áudio.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@OnlineSearchActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
