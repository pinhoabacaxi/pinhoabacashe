package com.maxrave.exampleApp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maxrave.exampleApp.adapter.SearchAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.exampleApp.repository.YouTubePlaylist
import com.maxrave.exampleApp.service.MusicDownloadWorker
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.exampleApp.viewmodel.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter
    private val youtubeRepository by lazy { YouTubeRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchView()
        observeViewModel()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(
            onItemClick = { item ->
                when (item) {
                    is VideoMeta -> handleVideoClick(item)
                    is YouTubePlaylist -> viewModel.loadPlaylistVideos(item.playlistId)
                }
            },
            onDownloadClick = { item ->
                if (item is VideoMeta) startDownload(item)
            }
        )
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun handleVideoClick(video: VideoMeta) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val song = withContext(Dispatchers.IO) {
                    youtubeRepository.extractAudioLink(video.videoId)
                }
                if (song != null) {
                    LocalPlayerManager.playOnline(song, this@OnlineSearchActivity)
                } else {
                    Toast.makeText(this@OnlineSearchActivity, "Não foi possível obter o link de áudio", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnlineSearchActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launchWhenStarted {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvMessage.visibility = View.GONE
                    }
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        searchAdapter.submitList(state.results)
                        
                        // Lógica para download em massa se o resultado for de uma playlist
                        val firstItem = state.results.firstOrNull()
                        if (state.results.all { it is VideoMeta } && state.results.size > 5) {
                            // Opcional: Mostrar um botão flutuante de "Baixar Tudo"
                            showDownloadAllOption(state.results.filterIsInstance<VideoMeta>())
                        }
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvMessage.apply {
                            visibility = View.VISIBLE
                            text = state.message
                        }
                    }
                    is SearchState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvMessage.visibility = View.VISIBLE
                        binding.tvMessage.text = "Pesquise vídeos ou playlists"
                    }
                }
            }
        }
    }

    private fun showDownloadAllOption(videos: List<VideoMeta>) {
        // Aqui você pode implementar um Snackbar ou botão para download em massa
        // Por agora, vamos apenas logar ou mostrar um Toast
        Toast.makeText(this, "${videos.size} vídeos carregados. Clique num vídeo para baixar ou ouvir.", Toast.LENGTH_SHORT).show()
    }

    private fun startDownload(video: VideoMeta) {
        val workData = workDataOf(
            "VIDEO_ID" to video.videoId,
            "FILE_NAME" to "${video.title}.mp3"
        )
        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(this).enqueue(downloadRequest)
        Toast.makeText(this, "Download iniciado: ${video.title}", Toast.LENGTH_SHORT).show()
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    viewModel.performSearch(it)
                    hideKeyboard()
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Permissões concedidas
        } else {
            Toast.makeText(this, "Permissões necessárias para download e notificações", Toast.LENGTH_LONG).show()
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
