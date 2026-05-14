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
import com.maxrave.exampleApp.repository.PlaylistRepository // Importante
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
    private val playlistRepository by lazy { PlaylistRepository(this) } // Repositório Room

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
                    is YouTubePlaylist -> {
                        viewModel.loadPlaylistVideos(item.playlistId)
                        Toast.makeText(this, "Carregando playlist: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
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
                    Toast.makeText(this@OnlineSearchActivity, "Erro ao extrair link", Toast.LENGTH_SHORT).show()
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
                    is SearchState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        searchAdapter.submitList(state.results)
                        
                        // Detecta se estamos visualizando o conteúdo de uma playlist
                        val videosOnly = state.results.filterIsInstance<VideoMeta>()
                        if (videosOnly.isNotEmpty() && state.results.size == videosOnly.size) {
                            showDownloadAllDialog(videosOnly)
                        }
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@OnlineSearchActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun showDownloadAllDialog(videos: List<VideoMeta>) {
        val input = EditText(this).apply {
            hint = "Nome da pasta no app e no celular"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Download em Massa")
            .setMessage("Deseja baixar estes ${videos.size} vídeos? Eles serão salvos em uma pasta e adicionados às suas Playlists Locais.")
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                val folderName = input.text.toString().trim().ifEmpty { "Minha Playlist" }
                
                // CRUCIAL: Cria a playlist no Room antes de começar o download
                lifecycleScope.launch(Dispatchers.IO) {
                    val existingId = playlistRepository.getPlaylistIdByName(folderName)
                    if (existingId == null) {
                        playlistRepository.createPlaylist(folderName)
                    }
                    
                    withContext(Dispatchers.Main) {
                        startBulkDownload(videos, folderName)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startBulkDownload(videos: List<VideoMeta>, folderName: String) {
        val workManager = WorkManager.getInstance(this)
        videos.forEach { video ->
            val workData = workDataOf(
                "VIDEO_ID" to video.videoId,
                "FILE_NAME" to "${video.title}.mp3",
                "PLAYLIST_NAME" to folderName
            )
            val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                .setInputData(workData)
                .addTag("bulk_download")
                .build()
            
            workManager.enqueue(request)
        }
        Toast.makeText(this, "Iniciado download de ${videos.size} músicas em: Music/$folderName", Toast.LENGTH_LONG).show()
    }

    private fun startDownload(video: VideoMeta) {
        val workData = workDataOf(
            "VIDEO_ID" to video.videoId,
            "FILE_NAME" to "${video.title}.mp3"
        )
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Download iniciado: ${video.title}", Toast.LENGTH_SHORT).show()
    }

    private fun setupSearchView() {
        binding.searchViewOnline.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            val toRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (toRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(toRequest.toTypedArray())
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(this, "Notificações desativadas: o progresso não será exibido.", Toast.LENGTH_SHORT).show()
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
