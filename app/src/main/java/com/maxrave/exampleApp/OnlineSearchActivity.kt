package com.maxrave.exampleApp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SearchAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var youtubeRepository: YouTubeRepository
    
    private val TAG = "OnlineActivity"

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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchState.collect { state ->
                    when(state) {
                        is SearchState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is SearchState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            Log.d(TAG, "Busca finalizada: ${state.results.size} resultados encontrados.")
                            searchAdapter.submitList(state.results)
                        }
                        is SearchState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@OnlineSearchActivity, "Erro: ${state.message}", Toast.LENGTH_LONG).show()
                        }
                        is SearchState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter { videoMeta ->
            handleOnlineClick(videoMeta)
        }
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Detecta ação de busca no teclado
        binding.etSearchOnline.setOnEditorActionListener { v, actionId, _ ->
            Log.d(TAG, "Teclado acionado. ActionId: $actionId")
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                actionId == EditorInfo.IME_ACTION_DONE || 
                actionId == EditorInfo.IME_ACTION_NEXT) {
                
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    viewModel.performSearch(query)
                } else {
                    Toast.makeText(this, "Digite algo para pesquisar", Toast.LENGTH_SHORT).show()
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchOnline.windowToken, 0)
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
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        // Bloqueia nova interação visual enquanto processa
        binding.progressBar.visibility = View.VISIBLE
        Log.d(TAG, "Iniciando processo de Stream para ID: ${videoMeta.videoId}")

        lifecycleScope.launch {
            // BYPASS 6: Hotlink Delay (Humano decidindo o que ouvir)
            delay(Random.nextLong(800, 1600))
            
            try {
                val fullSong = youtubeRepository.extractMusicInfo(videoMeta.videoId)
                
                binding.progressBar.visibility = View.GONE
                
                if (fullSong?.streamUrl != null) {
                    Log.d(TAG, "Link obtido com sucesso. Iniciando player.")
                    LocalPlayerManager.playOnline(fullSong, this@OnlineSearchActivity)
                } else {
                    Log.e(TAG, "Falha na extração: streamingData nulo ou negado.")
                    Toast.makeText(this@OnlineSearchActivity, "Não foi possível carregar o áudio deste vídeo.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Erro startStreaming: ${e.message}")
                Toast.makeText(this@OnlineSearchActivity, "Erro de conexão.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(videoMeta: VideoMeta) {
        binding.progressBar.visibility = View.VISIBLE
        Log.d(TAG, "Iniciando processo de Download para ID: ${videoMeta.videoId}")
        
        lifecycleScope.launch {
            // BYPASS 6: Hotlink Delay (Humano clicando para baixar)
            delay(Random.nextLong(1200, 2200))
            
            try {
                youtubeRepository.downloadMusic(videoMeta.videoId)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@OnlineSearchActivity, "Extraindo link para download...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Erro startDownload: ${e.message}")
            }
        }
    }
}
