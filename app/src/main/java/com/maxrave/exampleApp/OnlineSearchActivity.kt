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
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import kotlinx.coroutines.launch
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    
    // Unificado para usar apenas o SearchAdapter que lida com VideoMeta
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var youtubeRepository: YouTubeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        youtubeRepository = YouTubeRepository(this)
        
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when(state) {
                    is SearchState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.recyclerViewResults.visibility = View.GONE
                    }
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerViewResults.visibility = View.VISIBLE
                        searchAdapter.submitList(state.results)
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@OnlineSearchActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is SearchState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        // Agora o clique no item abre o menu de opções (Stream ou Download)
        searchAdapter = SearchAdapter { videoMeta ->
            handleOnlineClick(videoMeta)
        }
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = searchAdapter
    }

    private fun setupListeners() {
        // Listener para o botão de busca (caso exista no XML)
        binding.buttonSearch?.setOnClickListener {
            val query = binding.etSearchOnline.text.toString().trim()
            if (query.isNotEmpty()) performSearch(query)
        }

        // Listener para a tecla "Enter/Busca" do teclado
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearchOnline.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                
                // Esconder teclado
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSearchOnline.windowToken, 0)
                
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun performSearch(query: String) {
        // Delegamos a busca para o ViewModel (Nova metodologia InnerTube)
        viewModel.performSearch(query)
    }

    private fun handleOnlineClick(videoMeta: VideoMeta) {
        val options = arrayOf("Ouvir Agora (Stream)", "Baixar Música")
        AlertDialog.Builder(this)
            .setTitle(videoMeta.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startStreaming(videoMeta)
                    1 -> startDownload(videoMeta)
                }
            }
            .show()
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        Toast.makeText(this, "Obtendo áudio...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            // Usa o repositório para a extração pesada do link real
            val fullSong = youtubeRepository.extractMusicInfo(videoMeta.videoId)
            if (fullSong?.streamUrl != null) {
                LocalPlayerManager.playOnline(fullSong, this@OnlineSearchActivity)
            } else {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao obter link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(videoMeta: VideoMeta) {
        Toast.makeText(this, "Iniciando download...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            youtubeRepository.downloadMusic(videoMeta.videoId)
        }
    }
}
