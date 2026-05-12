package com.maxrave.exampleApp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.exampleApp.Room.SongEntity
import com.maxrave.exampleApp.Room.FavoriteEntity
import jp.wasabeef.glide.transformations.BlurTransformation
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.launch
import com.maxrave.exampleApp.service.MusicDownloadWorker

class FullPlayerActivity : AppCompatActivity() {

    private lateinit var ivAlbumArt: ImageView
    private lateinit var ivBackgroundBlur: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnOptions: ImageButton
    private lateinit var btnFavorite: ImageButton
    
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
        tvTitle = findViewById(R.id.tvSongTitle)
        tvArtist = findViewById(R.id.tvSongArtist)
        seekBar = findViewById(R.id.seekBar)
        seekBarVolume = findViewById(R.id.seekBarVolume)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnOptions = findViewById(R.id.btnMoreOptions)
        btnFavorite = findViewById(R.id.btnFavorite)

        // Configuração inicial da UI
        tvTitle.isSelected = true // Ativa Marquee
        updateRepeatButtonUI(LocalPlayerManager.repeatMode)
        seekBarVolume.progress = (LocalPlayerManager.getVolume() * 100).toInt()
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

        btnFavorite.setOnClickListener { toggleFavorite() }

        btnOptions.setOnClickListener { showFullPlayerOptions() } // Corrigido para chamar a função renomeada

        // Barra de Progresso da Música
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Barra de Volume Interno
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    LocalPlayerManager.setVolume(volume)
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    // Altere o nome da função e a chamada no clique do botão
private fun showFullPlayerOptions() {
    val currentTrack = LocalPlayerManager.getCurrentTrack() ?: return
    val bottomSheet = OptionsBottomSheet(currentTrack) { action ->
        when(action) {
            "PLAY_NEXT" -> LocalPlayerManager.playNext(currentTrack)
            "ADD_QUEUE" -> LocalPlayerManager.addToEnd(currentTrack)
            "DOWNLOAD_MP3" -> {
                if (currentTrack is OnlineSong) {
                    val data = androidx.work.workDataOf("URL" to currentTrack.url, "FILE_NAME" to "${currentTrack.title}.mp3")
                    val request = androidx.work.OneTimeWorkRequestBuilder<MusicDownloadWorker>().setInputData(data).build()
                    androidx.work.WorkManager.getInstance(this).enqueue(request)
                    Toast.makeText(this, "Download iniciado...", Toast.LENGTH_SHORT).show()
                }
            }
            "ADD_PLAYLIST" -> showPlaylistSelection(currentTrack)
        }
    }
    bottomSheet.show(supportFragmentManager, "Options")
}

    private fun toggleFavorite() {
        val track = LocalPlayerManager.getCurrentTrack() ?: return
        val trackId = if (track is Song) track.id.toString() else (track as OnlineSong).videoId
        
        lifecycleScope.launch {
            val isFav = repository.isFavorite(trackId)
            if (isFav) {
                repository.removeFavorite(trackId)
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_off)
                Toast.makeText(this@FullPlayerActivity, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            } else {
                repository.addFavorite(trackId)
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_on)
                Toast.makeText(this@FullPlayerActivity, "Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkIsFavorite(trackId: String) {
        lifecycleScope.launch {
            val isFav = repository.isFavorite(trackId)
            btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )
        }
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
                    saveTrackToPlaylist(playlists[which].id, track)
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
            Toast.makeText(this@FullPlayerActivity, "Salvo na playlist!", Toast.LENGTH_SHORT).show()
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

    private fun updateUI(track: Any) {
        val title: String
        val artist: String
        val artSource: Any?
        val trackId: String

        when (track) {
            is Song -> {
                title = track.title
                artist = track.artist
                artSource = track.path
                trackId = track.id.toString()
            }
            is OnlineSong -> {
                title = track.title
                artist = track.author
                artSource = track.thumbnailUrl
                trackId = track.videoId
            }
            else -> return
        }

        tvTitle.text = title
        tvArtist.text = artist
        checkIsFavorite(trackId)

        Glide.with(this).load(artSource).placeholder(android.R.drawable.ic_media_play).into(ivAlbumArt)
        Glide.with(this).load(artSource)
            .apply(RequestOptions.bitmapTransform(BlurTransformation(25, 3)))
            .into(ivBackgroundBlur)
    }

    private fun updateRepeatButtonUI(mode: LocalPlayerManager.RepeatMode) {
        when (mode) {
            LocalPlayerManager.RepeatMode.NONE -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_revert)
                btnRepeat.alpha = 0.5f
            }
            LocalPlayerManager.RepeatMode.ALL -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_revert)
                btnRepeat.alpha = 1.0f
            }
            LocalPlayerManager.RepeatMode.ONE -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_today) // Ícone alternativo para "um"
                btnRepeat.alpha = 1.0f
            }
        }
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
