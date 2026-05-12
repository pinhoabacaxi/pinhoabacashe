package com.maxrave.exampleApp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
            // Ao clicar, extraímos o link de streaming e tocamos
            startStreaming(videoMeta)
        }
        
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    // No setupSearchInput, use o ID correto do layout premium (searchViewOnline)
    private fun setupSearchInput() {
        val searchView = binding.cardSearchContainer.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchViewOnline)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    viewModel.search(query)
                    hideKeyboard()
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // Usando collect para observar o StateFlow do SearchViewModel
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
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
                // Chama o repositório para extrair o link direto do áudio (Streaming Data)
                val streamData = youtubeRepository.extractAudioLink(videoMeta.videoId)
                
                if (streamData != null) {
                    // Mapeamento correto para o modelo OnlineSong.kt que definimos
                    val onlineSong = OnlineSong(
                        videoId = videoMeta.videoId,
                        title = videoMeta.title,
                        author = videoMeta.author,
                        thumbnailUrl = videoMeta.thumbnailUrl,
                        url = streamData.url, // URL final de streaming do YouTube
                        duration = videoMeta.duration.toString()
                    )
                    
                    binding.progressBar.visibility = View.GONE
                    
                    // Envia para o Manager tocar e atualizar a fila híbrida
                    LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                    
                    Toast.makeText(this@OnlineSearchActivity, "Iniciando: ${videoMeta.title}", Toast.LENGTH_SHORT).show()
                    
                    // Fecha a busca para que o usuário veja o Mini Player na tela principal
                    finish() 
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@OnlineSearchActivity, "Não foi possível obter o áudio", Toast.LENGTH_SHORT).show()
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
