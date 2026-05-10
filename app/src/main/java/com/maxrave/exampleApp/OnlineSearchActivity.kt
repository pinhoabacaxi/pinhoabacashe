package com.maxrave.exampleApp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.adapter.OnlineSongAdapter
import com.maxrave.exampleApp.databinding.ActivityOnlineSearchBinding
import com.maxrave.exampleApp.model.OnlineSong
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
        
        binding.btnSearchOnline.setOnClickListener {
            val query = binding.etOnlineSearch.text.toString()
            if (query.isNotEmpty()) performSearch(query)
        }
    }

    private fun setupRecyclerView() {
        adapter = OnlineSongAdapter(emptyList()) { onlineSong ->
            // Abre o link no navegador ou app do YouTube por padrão
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(onlineSong.videoUrl))
            startActivity(intent)
        }
        binding.rvOnlineResults.layoutManager = LinearLayoutManager(this)
        binding.rvOnlineResults.adapter = adapter
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            // AQUI VOCÊ CHAMA A FUNÇÃO DE BUSCA QUE JÁ EXISTE NO SEU REPO
            // Exemplo fictício baseado na sua estrutura:
            val results = withContext(Dispatchers.IO) {
                // substitua pelo seu método: Repositorio.search(query)
                emptyList<OnlineSong>() 
            }
            
            binding.progressBar.visibility = View.GONE
            if (results.isEmpty()) {
                Toast.makeText(this@OnlineSearchActivity, "Nenhum resultado encontrado", Toast.LENGTH_SHORT).show()
            } else {
                adapter.updateList(results)
            }
        }
    }
}
