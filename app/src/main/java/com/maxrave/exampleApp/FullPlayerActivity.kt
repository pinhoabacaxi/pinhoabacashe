package com.maxrave.exampleApp

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.databinding.ActivityFullPlayerBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager

class FullPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
        observePlayer()
    }

    private fun setupUI() {
        LocalPlayerManager.currentSong?.let { updateSongInfo(it) }
        updateShuffleUI()
        updateRepeatUI()
        
        val isPlaying = LocalPlayerManager.isPlaying()
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause 
            else android.R.drawable.ic_media_play
        )
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
    
        binding.btnPlayPause.setOnClickListener { 
            LocalPlayerManager.togglePlayPause(this) 
        }
    
        binding.btnNext.setOnClickListener { LocalPlayerManager.next(this) }
        binding.btnPrev.setOnClickListener { LocalPlayerManager.previous(this) }
    
        binding.btnShuffle.setOnClickListener {
            LocalPlayerManager.toggleShuffle()
            updateShuffleUI()
        }
    
        binding.btnRepeat.setOnClickListener {
            LocalPlayerManager.toggleRepeat()
            updateRepeatUI()
        }

    // Correção: Alterado de seekProgress para seekBar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observePlayer() {
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
        }

        LocalPlayerManager.onTrackChanged = { song ->
            updateSongInfo(song)
        }

        LocalPlayerManager.onProgressChanged = { current, total ->
        // Correção: Alterado de seekProgress para seekBar
            binding.seekBar.max = total
            binding.seekBar.progress = current
            binding.tvCurrentTime.text = formatTime(current)
            binding.tvTotalTime.text = formatTime(total)
        }
    }


    private fun updateSongInfo(song: Song) {
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist
    
        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )
    
        Glide.with(this)
            .load(albumArtUri)
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .into(binding.ivAlbumArt)
    }

    private fun updateShuffleUI() {
        // Usa transparência para indicar estado ativo/inativo
        binding.btnShuffle.alpha = if (LocalPlayerManager.isShuffle) 1.0f else 0.4f
    }

    private fun updateRepeatUI() {
        val icon = when (LocalPlayerManager.repeatMode) {
            LocalPlayerManager.RepeatMode.ALL -> android.R.drawable.ic_menu_revert
            LocalPlayerManager.RepeatMode.ONE -> android.R.drawable.ic_menu_today
            else -> android.R.drawable.ic_menu_close_clear_cancel
        }
        binding.btnRepeat.setImageResource(icon)
        binding.btnRepeat.alpha = if (LocalPlayerManager.repeatMode == LocalPlayerManager.RepeatMode.NONE) 0.4f else 1.0f
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
