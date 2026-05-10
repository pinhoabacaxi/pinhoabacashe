package com.maxrave.exampleApp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.data.MusicLoader
import com.maxrave.exampleApp.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var songAdapter: SongAdapter
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        setupFab()
        checkPermissionsAndLoad()
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        songAdapter = SongAdapter(emptyList()) { song ->
            Toast.makeText(this, "Selecionado: ${song.title}", Toast.LENGTH_SHORT).show()
            // Player será implementado na Fase 2
        }
        rv.adapter = songAdapter
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabScan).setOnClickListener {
            checkPermissionsAndLoad()
        }
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) {
                MusicLoader.fetchLocalSongs(this@MainActivity)
            }
            songAdapter.updateSongs(songs)
            if (songs.isEmpty()) {
                Toast.makeText(this@MainActivity, "Nenhuma música encontrada", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            Toast.makeText(this, "Permissão negada para ler músicas", Toast.LENGTH_SHORT).show()
        }
    }
}
