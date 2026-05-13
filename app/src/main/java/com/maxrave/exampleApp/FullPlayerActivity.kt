package com.maxrave.exampleApp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
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
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var btnShuffle: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    
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
        
        // Inicializa a UI com a música atual, se houver
        LocalPlayerManager.getCurrentTrack()?.let { updateUI(it) }
    }

    private fun initViews() {
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        ivBackgroundBlur = findViewById(R.id.ivBackgroundBlur)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        seekBar = findViewById(R.id.seekBar)
        seekBarVolume = findViewById(R.id.seekBarVolume)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnDownload = findViewById(R.id.btnDownload)

        // Sincroniza o volume inicial
        seekBarVolume.progress = (LocalPlayerManager.getVolume() * 100).toInt()
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
        
        btnRepeat.setOnClickListener {
            val mode = LocalPlayerManager.toggleRepeatMode()
            updateRepeatButtonUI(mode)
        }

        btnDownload.setOnClickListener {
            val track = LocalPlayerManager.getCurrentTrack()
            if (track is OnlineSong) {
                // Envia VIDEO_ID e FILE_NAME para o Worker de download
                val data = workDataOf(
                    "VIDEO_ID" to track.videoId, 
                    "FILE_NAME" to "${track.title}.mp3"
                )
                val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                    .setInputData(data)
                    .build()
                
                WorkManager.getInstance(this).enqueue(request)
                Toast.makeText(this, "Download iniciado...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Esta música já é local", Toast.LENGTH_SHORT).show()
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
                tvTitle.text = item.title
                tvArtist.text = item.artist
                ivAlbumArt.setImageResource(android.R.drawable.ic_media_play)
                applyBlur(null)
            }
            is OnlineSong -> {
                tvTitle.text = item.title
                tvArtist.text = item.author
                Glide.with(this).load(item.thumbnailUrl).into(ivAlbumArt)
                applyBlur(item.thumbnailUrl)
            }
        }
        updateRepeatButtonUI(LocalPlayerManager.repeatMode)
    }

    private fun applyBlur(url: String?) {
        val options = RequestOptions.bitmapTransform(BlurTransformation(25, 3))
        // Carrega o placeholder se a URL for nula
        Glide.with(this)
            .load(url ?: android.R.drawable.ic_media_play)
            .apply(options)
            .into(ivBackgroundBlur)
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

    private fun updateRepeatButtonUI(mode: LocalPlayerManager.RepeatMode) {
        btnRepeat.alpha = if (mode == LocalPlayerManager.RepeatMode.NONE) 0.5f else 1.0f
        val icon = if (mode == LocalPlayerManager.RepeatMode.ONE) 
            android.R.drawable.ic_menu_today 
        else 
            android.R.drawable.ic_menu_revert
        btnRepeat.setImageResource(icon)
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
        LocalPlayerManager.unsubscribe(this)
    }
}
