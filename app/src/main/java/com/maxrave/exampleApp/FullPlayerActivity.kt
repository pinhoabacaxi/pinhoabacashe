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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.service.MusicDownloadWorker
import jp.wasabeef.glide.transformations.BlurTransformation

class FullPlayerActivity : AppCompatActivity(), LocalPlayerManager.PlayerListener {

    private lateinit var ivAlbumArt: ImageView
    private lateinit var ivBackgroundBlur: ImageView
    private lateinit var tvSongTitle: TextView // Nome ajustado para evitar conflitos
    private lateinit var tvSongArtist: TextView // Nome ajustado para evitar conflitos
    private lateinit var seekBar: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnDownloadAction: ImageButton // Nome ajustado

    private val handler = Handler(Looper.getMainLooper())

    // Objeto que atualiza a barra de progresso a cada segundo
    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_player)

        initViews()
        setupListeners()
        observePlayer()
    }

    private fun initViews() {
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        ivBackgroundBlur = findViewById(R.id.ivBackgroundBlur)
        
        // IMPORTANTE: Verifique se no seu XML as IDs são estas. 
        // Se o erro persistir, altere o R.id.NOME para o que está no seu XML
        tvSongTitle = findViewById(R.id.tvTitle) 
        tvSongArtist = findViewById(R.id.tvArtist)
        
        seekBar = findViewById(R.id.seekBar)
        seekBarVolume = findViewById(R.id.seekBarVolume)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnDownloadAction = findViewById(R.id.btnDownload)

        // Inicializa o volume da UI com o valor atual do Player
        seekBarVolume.progress = (LocalPlayerManager.getVolume() * 100).toInt()
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
        }

        btnNext.setOnClickListener {
            LocalPlayerManager.next(this)
        }

        btnPrev.setOnClickListener {
            LocalPlayerManager.previous(this)
        }

        btnRepeat.setOnClickListener {
            val newMode = LocalPlayerManager.toggleRepeatMode()
            updateRepeatButtonUI(newMode)
        }

        btnDownloadAction.setOnClickListener {
            val track = LocalPlayerManager.getCurrentTrack()
            if (track is OnlineSong) {
                startDownloadWork(track)
            } else {
                Toast.makeText(this, "Música já disponível localmente", Toast.LENGTH_SHORT).show()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) tvCurrentTime.text = formatTime(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {
                handler.removeCallbacks(updateProgressAction)
            }
            override fun onStopTrackingTouch(s: SeekBar?) {
                s?.let { LocalPlayerManager.seekTo(it.progress) }
                handler.post(updateProgressAction)
            }
        })

        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.setVolume(p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun observePlayer() {
        LocalPlayerManager.subscribe(this)
        handler.post(updateProgressAction)
    }

    // Implementação da Interface PlayerListener
    override fun onTrackChanged(item: Any) {
        runOnUiThread { updateUI(item) }
    }

    override fun onStatusChanged(isPlaying: Boolean) {
        runOnUiThread {
            btnPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
        }
    }

    private fun updateUI(item: Any) {
        when (item) {
            is Song -> {
                tvSongTitle.text = item.title
                tvSongArtist.text = item.artist
                ivAlbumArt.setImageResource(R.drawable.ic_music_note)
                applyBlurBackground(null)
            }
            is OnlineSong -> {
                tvSongTitle.text = item.title
                tvSongArtist.text = item.author
                Glide.with(this).load(item.thumbnailUrl).into(ivAlbumArt)
                applyBlurBackground(item.thumbnailUrl)
            }
        }
        updateRepeatButtonUI(LocalPlayerManager.repeatMode)
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

    private fun applyBlurBackground(url: String?) {
        val requestOptions = RequestOptions.bitmapTransform(BlurTransformation(25, 3))
        val target = ivBackgroundBlur
        if (url != null) {
            Glide.with(this).load(url).apply(requestOptions).into(target)
        } else {
            Glide.with(this).load(R.drawable.ic_music_note).apply(requestOptions).into(target)
        }
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
                btnRepeat.setImageResource(android.R.drawable.ic_menu_today)
                btnRepeat.alpha = 1.0f
            }
        }
    }

    private fun startDownloadWork(song: OnlineSong) {
        val data = workDataOf("URL" to song.url, "FILE_NAME" to "${song.title}.mp3")
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Iniciando download...", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
        LocalPlayerManager.unsubscribe(this)
    }
}
