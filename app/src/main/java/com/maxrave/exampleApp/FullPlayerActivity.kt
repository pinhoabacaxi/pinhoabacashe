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
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import jp.wasabeef.glide.transformations.BlurTransformation
import com.bumptech.glide.request.RequestOptions

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
        setupObservers()
        setupListeners()
        
        // Inicia a atualização da barra de progresso
        handler.post(updateProgressAction)
    }

    private fun initViews() {
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        ivBackgroundBlur = findViewById(R.id.ivBackgroundBlur)
        tvTitle = findViewById(R.id.tvSongTitle)
        tvArtist = findViewById(R.id.tvSongArtist)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        
        // Ativa o efeito Marquee para títulos longos
        tvTitle.isSelected = true 
    }

    private fun setupObservers() {
        // Observa a música atual
        LocalPlayerManager.onTrackChanged = { item ->
            updateUI(item)
        }

        // Observa o estado de reprodução (Play/Pause)
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(icon)
        }

        // Força a atualização inicial se já houver algo tocando
        val current = LocalPlayerManager.getCurrentTrack() // Crie este getter no Manager se não houver
        current?.let { updateUI(it) }
    }

    private fun updateUI(item: Any) {
        val title: String
        val artist: String
        val artSource: Any

        when (item) {
            is Song -> {
                title = item.title
                artist = item.artist
                artSource = item.uri ?: android.R.drawable.ic_media_play
            }
            is OnlineSong -> {
                title = item.title
                artist = item.author
                artSource = item.thumbnailUrl
            }
            else -> return
        }

        tvTitle.text = title
        tvArtist.text = artist

        // 1. Carrega a capa principal com cantos arredondados (via CardView no XML)
        Glide.with(this)
            .load(artSource)
            .placeholder(android.R.drawable.ic_media_play)
            .into(ivAlbumArt)

        // 2. Efeito Premium: Fundo com Desfoque (Blur)
        // Requer a biblioteca: implementation 'jp.wasabeef:glide-transformations:4.3.0'
        Glide.with(this)
            .load(artSource)
            .apply(RequestOptions.bitmapTransform(BlurTransformation(25, 3)))
            .into(ivBackgroundBlur)
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { LocalPlayerManager.togglePlayPause(this) }
        btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
        
        btnShuffle.setOnClickListener {
            LocalPlayerManager.isShuffle = !LocalPlayerManager.isShuffle
            btnShuffle.alpha = if (LocalPlayerManager.isShuffle) 1.0f else 0.5f
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
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
