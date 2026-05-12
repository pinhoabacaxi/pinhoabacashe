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
import com.maxrave.exampleApp.adapter.SearchAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
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
                            // ID sincronizado: rvOnlineResults
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
        searchAdapter = SearchAdapter { videoMeta ->
            handleOnlineClick(videoMeta)
        }
        // ID sincronizado: rvOnlineResults
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupListeners() {
        // Como não há botão no XML, usamos apenas a ação do teclado
        binding.etSearchOnline.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.performSearch(query)
                }
                hideKeyboard()
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
            .show()
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        Toast.makeText(this, "Obtendo áudio...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
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
