package com.maxrave.exampleApp

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.OnlineSongAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.player.LocalPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineSearchBinding
    private lateinit var adapter: OnlineSongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
    }

    private fun setupRecyclerView() {
        // Inicializa o adapter passando a ação de clique
        adapter = OnlineSongAdapter(
            onItemClick = { onlineSong ->
                handleOnlineClick(onlineSong)
            }
        )
        
        binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)
        binding.rvOnlineResults.adapter = adapter
    }

    private fun setupListeners() {
        // Configura a barra de busca para pesquisar ao apertar "Enter" no teclado do celular
        binding.etSearchOnline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchOnline.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun handleOnlineClick(onlineSong: OnlineSong) {
        val options = arrayOf("Ouvir Agora (Stream)", "Baixar Música", "Adicionar à Playlist")
    
        AlertDialog.Builder(this)
            .setTitle(onlineSong.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startStreaming(onlineSong)
                    1 -> startDownload(onlineSong)
                    2 -> showPlaylistSelector(onlineSong)
                }
            }
            .show()
    }

    private fun startStreaming(onlineSong: OnlineSong) {
        Toast.makeText(this, "A iniciar stream: ${onlineSong.title}", Toast.LENGTH_SHORT).show()
        
        // Exemplo de como você vai integrar o stream quando o extrator estiver pronto:
        /*
        lifecycleScope.launch {
            val extracted = withContext(Dispatchers.IO) {
                // youtubeExtractor.extract(onlineSong.videoId)
            }
            if (extracted?.streamUrl != null) {
                LocalPlayerManager.playOnline(extracted, this@OnlineSearchActivity)
            } else {
                Toast.makeText(this@OnlineSearchActivity, "Erro ao obter áudio", Toast.LENGTH_SHORT).show()
            }
        }
        */
    }

    private fun startDownload(onlineSong: OnlineSong) {
        Toast.makeText(this, "Iniciando download...", Toast.LENGTH_SHORT).show()
        // Aqui entrará a lógica do DownloadManager que você fará no futuro
    }

    private fun showPlaylistSelector(onlineSong: OnlineSong) {
        Toast.makeText(this, "Adicionar à playlist...", Toast.LENGTH_SHORT).show()
        // Aqui entrará a lógica para salvar na playlist local
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            // Faz a busca em background para não travar a UI
            val results = withContext(Dispatchers.IO) {
                // AQUI VOCÊ CHAMA A FUNÇÃO DE BUSCA DO SEU REPOSITÓRIO
                // Exemplo: youtubeRepository.search(query)
                emptyList<OnlineSong>() // <- Temporário até você conectar o repositório
            }
            
            binding.progressBar.visibility = View.GONE
            if (results.isEmpty()) {
                Toast.makeText(this@OnlineSearchActivity, "Nenhum resultado ou busca não implementada", Toast.LENGTH_SHORT).show()
            } else {
                adapter.updateList(results)
            }
        }
    }
}
