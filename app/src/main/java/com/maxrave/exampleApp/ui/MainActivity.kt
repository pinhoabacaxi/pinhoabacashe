package com.maxrave.exampleApp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.data.LocalPlayerManager
import com.maxrave.exampleApp.data.MusicLoader
import com.maxrave.exampleApp.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var songAdapter: SongAdapter
    private val PERMISSION_REQUEST_CODE = 100

    // Mini Player Views
    private lateinit var miniPlayerCard: MaterialCardView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnPlayPause: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupPlayerListeners()
        setupFab()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        miniPlayerCard = findViewById(R.id.miniPlayerCard)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayback() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { LocalPlayerManager.playNext(this) }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { LocalPlayerManager.playPrevious(this) }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        songAdapter = SongAdapter(emptyList()) { song ->
            LocalPlayerManager.play(this, song)
        }
        rv.adapter = songAdapter
    }

    private fun setupPlayerListeners() {
        LocalPlayerManager.onTrackChanged = { song ->
            miniPlayerCard.visibility = View.VISIBLE
            tvMiniTitle.text = song.title
            tvMiniArtist.text = song.artist
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(icon)
        }
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
            // Atualiza a fila do player
            if (songs.isNotEmpty()) {
                // Passamos a lista atual para o gerenciador de fila
                // Sem tocar automaticamente
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nesta fase, o player morre com a Activity. 
        // No passo 3 (Service), isso será resolvido.
        if (isFinishing) {
            LocalPlayerManager.release()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }
}
