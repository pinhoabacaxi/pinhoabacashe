package com.maxrave.exampleApp

import android.content.Context // Importação que faltava
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager // Importação para o teclado
import android.widget.Toast
import androidx.activity.viewModels
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
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    // 2. Seu Adapter do RecyclerView
    private lateinit var adapter: SearchAdapter
    private lateinit var adapter: OnlineSongAdapter
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
        // O código que você postou entra exatamente aqui:
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
                        // Atualiza o adapter com a lista de VideoMeta
                        adapter.submitList(state.results)
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
    private fun setupSearchListener() {
        binding.buttonSearch.setOnClickListener {
            val query = binding.editTextSearch.text.toString()
            if (query.isNotEmpty()) {
                // 3. Dispara a busca no ViewModel
                viewModel.performSearch(query)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter { videoMeta ->
            // Ação ao clicar em um item (ex: abrir o player)
        }
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearchOnline.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                
                // Esconder teclado corretamente
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSearchOnline.windowToken, 0)
                
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun handleOnlineClick(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir Agora (Stream)", "Baixar Música")
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startStreaming(onlineSong)
                    1 -> startDownload(onlineSong)
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
                Toast.makeText(this@OnlineSearchActivity, "Erro ao obter link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(onlineSong: OnlineSong) {
        lifecycleScope.launch {
            youtubeRepository.downloadMusic(onlineSong.videoId)
        }
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val results = youtubeRepository.searchTracks(query)
            binding.progressBar.visibility = View.GONE
            if (results.isEmpty()) {
                Toast.makeText(this@OnlineSearchActivity, "Nenhum resultado", Toast.LENGTH_SHORT).show()
            } else {
                adapter.updateList(results)
            }
        }
    }
}
