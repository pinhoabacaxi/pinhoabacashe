package com.maxrave.exampleApp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maxrave.exampleApp.Room.Playlist
import com.maxrave.exampleApp.adapter.PlaylistAdapter
import com.maxrave.exampleApp.databinding.ActivityPlaylistListBinding
import com.maxrave.exampleApp.repository.PlaylistRepository
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistListBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var playlistAdapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PlaylistRepository(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadPlaylists()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            playlists = emptyList(),
            onClick = { playlist ->
                // Abre a tela de músicas passando os dados da playlist
                val intent = Intent(this, PlaylistSongsActivity::class.java).apply {
                    putExtra("PLAYLIST_ID", playlist.id)
                    putExtra("PLAYLIST_NAME", playlist.name)
                }
                startActivity(intent)
            },
            onDelete = { playlist ->
                showDeleteConfirmation(playlist)
            }
        )

        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = playlistAdapter
        }
    }

    private fun setupListeners() {
        // Botão de adicionar (+) que definimos no XML
        binding.fabAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            val list = repository.getAllPlaylists()
            playlistAdapter.updateList(list)
            
            // Gerencia o estado vazio (Empty State)
            binding.emptyStatePlaylists.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this)
        input.hint = "Nome da playlist"
        
        AlertDialog.Builder(this)
            .setTitle("Nova Playlist")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.createPlaylist(name)
                        loadPlaylists()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmation(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Playlist")
            .setMessage("Tem certeza que deseja excluir '${playlist.name}'?")
            .setPositiveButton("Sim") { _, _ ->
                lifecycleScope.launch {
                    repository.deletePlaylist(playlist)
                    loadPlaylists()
                }
            }
            .setNegativeButton("Não", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Recarrega caso o usuário tenha voltado de uma tela onde adicionou algo
        loadPlaylists()
    }
}
