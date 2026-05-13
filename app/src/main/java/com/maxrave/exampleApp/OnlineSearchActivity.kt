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
    private fun showSavePlaylistDialog(playlistItems: List<OnlineSong>) {
        val editText = EditText(this)
        editText.hint = "Nome da Playlist Local"

        AlertDialog.Builder(this)
            .setTitle("Salvar Playlist Localmente")
            .setMessage("Digite o nome para sua nova playlist:")
            .setView(editText)
            .setPositiveButton("Baixar Tudo") { _, _ ->
                val customName = editText.text.toString()
                if (customName.isNotEmpty()) {
                    downloadFullPlaylist(customName, playlistItems)
                } else {
                    Toast.makeText(this, "Nome não pode ser vazio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupSearchInput() {
        // Corrigido: Acesso direto via binding para evitar findViewById desnecessário
        binding.searchViewOnline.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
                        binding.emptyStateContainer.visibility = View.GONE
                    }
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        searchAdapter.submitList(state.results)
                        binding.emptyStateContainer.visibility = if (state.results.isEmpty()) View.VISIBLE else View.GONE
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
                // Extrai o link real
                val streamData = youtubeRepository.extractAudioLink(videoMeta.videoId)
                
                if (streamData != null) {
                    val onlineSong = OnlineSong(
                        videoId = videoMeta.videoId,
                        title = videoMeta.title,
                        author = videoMeta.author,
                        thumbnailUrl = videoMeta.thumbnailUrl,
                        url = streamData.url, 
                        duration = videoMeta.duration.toString()
                    )
                    
                    // CORREÇÃO: Chama a função que adicionamos/verificamos no LocalPlayerManager
                    LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                    
                    kotlinx.coroutines.delay(200)
                    binding.progressBar.visibility = View.GONE
                    finish() 
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@OnlineSearchActivity, "Vídeo indisponível", Toast.LENGTH_SHORT).show()
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
