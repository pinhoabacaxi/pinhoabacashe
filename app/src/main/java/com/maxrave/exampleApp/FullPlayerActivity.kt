package com.maxrave.exampleApp

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.exampleApp.Room.SongEntity
import jp.wasabeef.glide.transformations.BlurTransformation
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.launch

class FullPlayerActivity : AppCompatActivity() {

    private lateinit var ivAlbumArt: ImageView
    private lateinit var ivBackgroundBlur: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnOptions: ImageButton // Novo botão de opções
    
    private lateinit var repository: PlaylistRepository
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_player)

        repository = PlaylistRepository(this)
        initViews()
        setupListeners()
        observePlayer()
    }

    private fun initViews() {
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        ivBackgroundBlur = findViewById(R.id.ivBackgroundBlur)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnOptions = findViewById(R.id.btnMoreOptions) // Certifique-se que este ID existe no XML
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        
        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
        
        btnShuffle.setOnClickListener {
            LocalPlayerManager.isShuffle = !LocalPlayerManager.isShuffle
            btnShuffle.alpha = if (LocalPlayerManager.isShuffle) 1.0f else 0.5f
        }
        btnRepeat.setOnClickListener {
            val newMode = LocalPlayerManager.toggleRepeatMode()
            updateRepeatButtonUI(newMode)
        }
        // 2. Lógica do Botão Favorito (Supondo que você tenha um btnFavorite no XML)
        btnFavorite.setOnClickListener {
            val currentTrack = LocalPlayerManager.getCurrentTrack() ?: return@setOnClickListener
            val trackId = if (currentTrack is Song) currentTrack.id.toString() else (currentTrack as OnlineSong).videoId
            
            lifecycleScope.launch {
                val isFav = repository.isFavorite(trackId)
                if (isFav) {
                    repository.removeFavorite(trackId)
                    btnFavorite.setImageResource(R.drawable.ic_heart_outline)
                } else {
                    repository.addFavorite(trackId)
                    btnFavorite.setImageResource(R.drawable.ic_heart_filled)
                }
            }
        }
        // 3. Volume Interno (Adicione um SeekBar de volume no seu XML)
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    LocalPlayerManager.setVolume(volume)
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        btnOptions.setOnClickListener {
            val currentTrack = LocalPlayerManager.getCurrentTrack() ?: return@setOnClickListener
            val bottomSheet = OptionsBottomSheet(currentTrack) { action ->
                when(action) {
                    "PLAY_NEXT" -> LocalPlayerManager.playNext(currentTrack)
                    "ADD_QUEUE" -> LocalPlayerManager.addToEnd(currentTrack)
                    "DOWNLOAD_MP3" -> { /* Chamar WorkManager de download aqui */ }
                    "ADD_PLAYLIST" -> showPlaylistSelection(currentTrack)
                }
            }
            bottomSheet.show(supportFragmentManager, "Options")
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
    // Dentro da FullPlayerActivity.kt
    private fun openOptionsMenu() {
        val currentTrack = LocalPlayerManager.getCurrentTrack() ?: return
        
        val bottomSheet = OptionsBottomSheet(currentTrack) { action ->
            when(action) {
                "PLAY_NEXT" -> LocalPlayerManager.playNext(currentTrack)
                "ADD_QUEUE" -> LocalPlayerManager.addToEnd(currentTrack)
                "DOWNLOAD_MP3" -> {
                    if (currentTrack is OnlineSong) {
                        startDownload(currentTrack, "mp3") // Chame sua função do WorkManager aqui
                    }
                }
                "ADD_PLAYLIST" -> showPlaylistSelection(currentTrack) // Abra o diálogo do Room aqui
            }
        }
        bottomSheet.show(supportFragmentManager, "Options")
    }
    private fun showPlaylistSelection(track: Any) {
        lifecycleScope.launch {
            val playlists = repository.getAllPlaylists()
            val names = playlists.map { it.name }.toTypedArray()

            if (names.isEmpty()) {
                Toast.makeText(this@FullPlayerActivity, "Crie uma playlist primeiro!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            android.app.AlertDialog.Builder(this@FullPlayerActivity)
                .setTitle("Adicionar à Playlist")
                .setItems(names) { _, which ->
                    val selected = playlists[which]
                    saveTrackToPlaylist(selected.id, track)
                }.show()
        }
    }

    private fun saveTrackToPlaylist(playlistId: Long, track: Any) {
        lifecycleScope.launch {
            val entity = when(track) {
                is Song -> SongEntity(track.id.toString(), track.title, track.artist, track.path, null, false)
                is OnlineSong -> SongEntity(track.videoId, track.title, track.author, track.url, track.thumbnailUrl, true)
                else -> return@launch
            }
            repository.addSongToPlaylist(playlistId, entity)
            Toast.makeText(this@FullPlayerActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observePlayer() {
        LocalPlayerManager.onTrackChanged = { track -> updateUI(track) }
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
        LocalPlayerManager.getCurrentTrack()?.let { updateUI(it) }
        handler.post(updateProgressAction)
    }

    private fun updateRepeatButtonUI(mode: LocalPlayerManager.RepeatMode) {
        when (mode) {
            LocalPlayerManager.RepeatMode.NONE -> {
                btnRepeat.setImageResource(R.id.ic_repeat)
                btnRepeat.alpha = 0.5f // Desativado
            }
            LocalPlayerManager.RepeatMode.ALL -> {
                btnRepeat.setImageResource(R.id.ic_repeat)
                btnRepeat.alpha = 1.0f // Repetir tudo
            }
            LocalPlayerManager.RepeatMode.ONE -> {
                btnRepeat.setImageResource(R.id.ic_repeat_one)
                btnRepeat.alpha = 1.0f // Repetir uma
            }
        }
    }
    private fun updateUI(track: Any) {
        val title = if (track is Song) track.title else (track as OnlineSong).title
        val artist = if (track is Song) track.artist else (track as OnlineSong).author
        val art = if (track is Song) track.albumArtUri else (track as OnlineSong).thumbnailUrl

        tvTitle.text = title
        tvArtist.text = artist

        Glide.with(this).load(art).placeholder(R.drawable.ic_default_art).into(ivAlbumArt)
        Glide.with(this).load(art).apply(RequestOptions.bitmapTransform(BlurTransformation(25, 3)))
            .into(ivBackgroundBlur)
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateProgress() {
        val current = LocalPlayerManager.getCurrentPosition()
        val total = LocalPlayerManager.getDuration()
        if (total > 0) {
            seekBar.max = total
            seekBar.progress = current
            tvCurrentTime.text = formatTime(current)
            tvTotalTime.text = formatTime(total)
        }
    }

    private fun formatTime(ms: Int): String {
        val minutes = (ms / 1000) / 60
        val seconds = (ms / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
    }
}
