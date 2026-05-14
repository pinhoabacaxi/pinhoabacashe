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
import kotlinx.coroutines.launch

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private val youtubeRepository by lazy { YouTubeRepository(this) }
    private lateinit var searchAdapter: SearchAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permissões negadas. O download pode falhar.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        setupRecyclerView()
        setupSearchView()
        observeViewModel()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        searchAdapter = SearchAdapter(
            onItemClick = { item ->
                when (item) {
                    is VideoMeta -> playVideo(item)
                    is YouTubePlaylist -> {
                        Toast.makeText(this, "Abrindo playlist...", Toast.LENGTH_SHORT).show()
                        viewModel.loadPlaylistVideos(item.playlistId)
                    }
                }
            },
            onDownloadClick = { item ->
                when (item) {
                    is VideoMeta -> startDownload(item)
                    is YouTubePlaylist -> showSavePlaylistDialog(item.playlistId, item.title)
                }
            }
        )
        binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)
        binding.rvOnlineResults.adapter = searchAdapter
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

    private fun observeViewModel() {
        lifecycleScope.launchWhenStarted {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is SearchState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        // Cast seguro para garantir compatibilidade com o Adapter
                        searchAdapter.submitList(state.results as List<Any>)
                        binding.rvOnlineResults.visibility = View.VISIBLE
                        binding.emptyStateContainer.visibility = View.GONE
                    }
                    is SearchState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@OnlineSearchActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun playVideo(videoMeta: VideoMeta) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val onlineSong = youtubeRepository.extractAudioLink(videoMeta.videoId)
            binding.progressBar.visibility = View.GONE
            
            if (onlineSong != null) {
                LocalPlayerManager.playOnline(onlineSong, this@OnlineSearchActivity)
                // Opcional: finish() se você quiser fechar a busca ao dar play
            } else {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao extrair áudio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(videoMeta: VideoMeta) {
        val workData = workDataOf(
            "VIDEO_ID" to videoMeta.videoId,
            "FILE_NAME" to "${videoMeta.title}.mp3"
        )
        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .build()
        WorkManager.getInstance(this).enqueue(downloadRequest)
        Toast.makeText(this, "Download iniciado: ${videoMeta.title}", Toast.LENGTH_SHORT).show()
    }

    private fun showSavePlaylistDialog(playlistId: String, playlistTitle: String) {
        val editText = EditText(this)
        editText.setText(playlistTitle)
        editText.setSelection(editText.text.length)

        AlertDialog.Builder(this)
            .setTitle("Baixar Playlist")
            .setMessage("Escolha um nome para a pasta:")
            .setView(editText)
            .setPositiveButton("Baixar Tudo") { _, _ ->
                val customFolderName = editText.text.toString()
                if (customFolderName.isNotEmpty()) {
                    downloadFullPlaylist(playlistId, customFolderName)
                } else {
                    Toast.makeText(this, "Nome inválido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun downloadFullPlaylist(playlistId: String, customName: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
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
                    Toast.makeText(this@OnlineSearchActivity, "Baixando ${songs.size} músicas em $customName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@OnlineSearchActivity, "Playlist vazia", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnlineSearchActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
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
