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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.SearchAdapter // Verifique se o pacote do adapter está correto
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    
    // Lazy initialization do ViewModel (Requer dependência activity-ktx no Gradle)
    private val viewModel: SearchViewModel by viewModels()
    
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
        // repeatOnLifecycle é a forma recomendada em 2026 para observar Flows/States
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    private fun setupRecyclerView() {
        // Inicializa o adapter com o callback de clique
        searchAdapter = SearchAdapter { videoMeta ->
            handleOnlineClick(videoMeta)
        }
        
        binding.recyclerViewResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
            // Otimização de performance
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Listener para o botão de busca (ID: buttonSearch)
        binding.buttonSearch.setOnClickListener {
            val query = binding.etSearchOnline.text.toString().trim()
            if (query.isNotEmpty()) performSearch(query)
        }

        // Listener para a tecla "Enter/Busca" do teclado (ID: etSearchOnline)
        binding.etSearchOnline.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun performSearch(query: String) {
        viewModel.performSearch(query)
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
            .show()
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        Toast.makeText(this, "Obtendo áudio...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val fullSong = youtubeRepository.extractMusicInfo(videoMeta.videoId)
                if (fullSong?.streamUrl != null) {
                    LocalPlayerManager.playOnline(fullSong, this@OnlineSearchActivity)
                } else {
                    Toast.makeText(this@OnlineSearchActivity, "Erro ao obter link de áudio", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnlineSearchActivity, "Falha na extração", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(videoMeta: VideoMeta) {
        Toast.makeText(this, "Adicionado à fila de download", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                youtubeRepository.downloadMusic(videoMeta.videoId)
            } catch (e: Exception) {
                Toast.makeText(this@OnlineSearchActivity, "Falha ao iniciar download", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
