package com.maxrave.exampleApp

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
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
        val currentSong = LocalPlayerManager.currentSong
        currentSong?.let { updateSongInfo(it) }
        
        // Configura ícones iniciais usando recursos nativos do Android para garantir o build
        binding.btnPlayPause.setImageResource(
            if (LocalPlayerManager.isPlaying()) android.R.drawable.ic_media_pause 
            else android.R.drawable.ic_media_play
        )
    }

    private fun setupListeners() {
        binding.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
        }

        binding.btnNext.setOnClickListener {
            LocalPlayerManager.next(this)
        }

        binding.btnPrev.setOnClickListener {
            LocalPlayerManager.previous(this)
        }

        binding.btnShuffle.setOnClickListener {
            LocalPlayerManager.toggleShuffle()
            updateShuffleUI()
        }

        binding.btnRepeat.setOnClickListener {
            LocalPlayerManager.toggleRepeat()
            updateRepeatUI()
        }

        // Correção do erro "Variable expected": acessando a propriedade .progress
        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    LocalPlayerManager.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    LocalPlayerManager.setVolume(volume)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observePlayer() {
        LocalPlayerManager.onTrackChanged = { song ->
            updateSongInfo(song)
        }

        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)
        }

        LocalPlayerManager.onProgressChanged = { current, total ->
            binding.seekProgress.max = total
            binding.seekProgress.progress = current // Correção: .progress em vez de atribuição direta ao objeto
            binding.tvCurrentTime.text = formatTime(current)
            binding.tvTotalTime.text = formatTime(total)
        }
    }

    private fun updateSongInfo(song: Song) {
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist
        // Aqui você pode usar o Glide ou similar para carregar a capa se desejar
        binding.ivAlbumArt.setImageResource(android.R.drawable.ic_menu_report_image)
    }

    }
    

    private fun updateShuffleUI() {
    // Corrigido de isShuffleEnabled para isShuffle
        val color = if (LocalPlayerManager.isShuffle) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt()
        binding.btnShuffle.setColorFilter(color)
    }

    private fun updateRepeatUI() {
        val icon = when (LocalPlayerManager.repeatMode) {
            LocalPlayerManager.RepeatMode.ALL -> android.R.drawable.ic_menu_revert
            LocalPlayerManager.RepeatMode.ONE -> android.R.drawable.ic_menu_today
            LocalPlayerManager.RepeatMode.NONE -> android.R.drawable.ic_menu_close_clear_cancel 
        }
        binding.btnRepeat.setImageResource(icon)
    
    // Feedback visual de cor
        val color = if (LocalPlayerManager.repeatMode == LocalPlayerManager.RepeatMode.NONE) 
            0xFFFFFFFF.toInt() else 0xFF00FF00.toInt()
        binding.btnRepeat.setColorFilter(color)
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
