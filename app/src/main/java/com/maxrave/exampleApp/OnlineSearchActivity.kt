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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maxrave.exampleApp.adapter.SearchAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.exampleApp.service.MusicDownloadWorker
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var youtubeRepository: YouTubeRepository

    // 1. GERENCIADOR DE PERMISSÕES (Moderno)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Algumas permissões foram negadas. O download pode não funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        youtubeRepository = YouTubeRepository(this)
        
        checkAndRequestPermissions()
        setupRecyclerView()
        setupSearchInput()
        observeViewModel()
    }

    // 2. LÓGICA DE PERMISSÕES
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Para Notificações (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Para Armazenamento (Android 12 ou inferior)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupRecyclerView() {
        // Agora o clique deve verificar se o item é um vídeo ou uma playlist
        searchAdapter = SearchAdapter { item ->
            when (item) {
                is VideoMeta -> {
                    // Se for vídeo normal, inicia o streaming como antes
                    startStreaming(item)
                }
                is YouTubePlaylist -> {
                    // NOVA FUNCIONALIDADE: Se for playlist, abre o diálogo de download
                    showSavePlaylistDialog(item.playlistId, item.title)
                }
            }
        }
        
        binding.rvOnlineResults.apply {
            layoutManager = LinearLayoutManager(this@OnlineSearchActivity)
            adapter = searchAdapter
        }
    }

    // 3. LÓGICA DE DOWNLOAD DE PLAYLIST COM NOME CUSTOMIZADO
    fun showSavePlaylistDialog(playlistId: String, playlistTitle: String) {
        val editText = EditText(this)
        editText.setText(playlistTitle) // Sugere o nome original da playlist
        editText.setSelection(editText.text.length)

        AlertDialog.Builder(this)
            .setTitle("Baixar Playlist")
            .setMessage("Escolha um nome para a pasta da playlist:")
            .setView(editText)
            .setPositiveButton("Baixar Tudo") { _, _ ->
                val customFolderName = editText.text.toString()
                if (customFolderName.isNotEmpty()) {
                    downloadFullPlaylist(playlistId, customFolderName)
                } else {
                    Toast.makeText(this, "O nome não pode ser vazio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun downloadFullPlaylist(playlistId: String, customName: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                // Busca todos os vídeos da playlist via repositório
                val songs = youtubeRepository.getPlaylistVideos(playlistId)
                
                if (songs.isNotEmpty()) {
                    val workManager = WorkManager.getInstance(this@OnlineSearchActivity)
                    
                    songs.forEach { song ->
                        val workData = workDataOf(
                            "VIDEO_ID" to song.videoId,
                            "FILE_NAME" to "${song.title}.mp3",
                            "PLAYLIST_NAME" to customName
                        )

                        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                            .setInputData(workData)
                            .build()

                        workManager.enqueue(downloadRequest)
                    }
                    Toast.makeText(this@OnlineSearchActivity, "Iniciado download de ${songs.size} músicas em '$customName'", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@OnlineSearchActivity, "Nenhuma música encontrada na playlist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao processar playlist: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupSearchInput() {
        binding.searchViewOnline.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    // Aqui você pode implementar um seletor para buscar Playlist ou Vídeo
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
                        
                        // state.results agora contém a lista (seja de vídeos ou de playlists)
                        searchAdapter.submitList(state.results)
                        
                        // Gerencia o estado vazio
                        binding.emptyStateContainer.visibility = if (state.results.isEmpty()) View.VISIBLE else View.GONE
                        
                        // Opcional: Feedback visual se não houver resultados
                        if (state.results.isEmpty()) {
                            binding.tvEmptyMessage.text = "Nenhum resultado encontrado para esta busca."
                        }
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.emptyStateContainer.visibility = View.VISIBLE
                        Toast.makeText(this@OnlineSearchActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun startStreaming(videoMeta: VideoMeta) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
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
