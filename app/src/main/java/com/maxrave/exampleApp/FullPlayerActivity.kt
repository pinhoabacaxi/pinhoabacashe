package com.maxrave.exampleApp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.maxrave.exampleApp.databinding.ActivityFullPlayerBinding
import com.maxrave.exampleApp.player.LocalPlayerManager
import java.util.concurrent.TimeUnit

class FullPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullPlayerBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        startProgressUpdate()
    }

    private fun setupUI() {
        // No setupUI() ou initListeners():

    binding.btnShuffle.setOnClickListener {
        LocalPlayerManager.toggleShuffle()
        val tint = if (LocalPlayerManager.isShuffle) R.color.purple_500 else R.color.black
        binding.btnShuffle.setColorFilter(ContextCompat.getColor(this, tint))
    }

    binding.btnRepeat.setOnClickListener {
        LocalPlayerManager.toggleRepeat()
    // Atualizar ícone baseado no RepeatMode (NONE, ONE, ALL)
        when(LocalPlayerManager.repeatMode) {
            LocalPlayerManager.RepeatMode.ALL -> binding.btnRepeat.setImageResource(R.drawable.ic_repeat_all)
            LocalPlayerManager.RepeatMode.ONE -> binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
            else -> binding.btnRepeat.setImageResource(R.drawable.ic_repeat_off)
        }
    }

        val song = LocalPlayerManager.currentSong ?: return
        binding.tvFullTitle.text = song.title
        binding.tvFullArtist.text = song.artist
        
        updatePlayPauseButton(LocalPlayerManager.isPlaying())

        // Configuração Volume (0 a 100 no Seek, convertido para 0.0 a 1.0 no Player)
        binding.seekVolume.progress = (LocalPlayerManager.getVolume() * 100).toInt()
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    LocalPlayerManager.setVolume(volume)
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        // Configuração Progresso
        binding.seekProgress.max = LocalPlayerManager.getDuration()
        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) LocalPlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            updatePlayPauseButton(LocalPlayerManager.isPlaying())
        }

        binding.btnNext.setOnClickListener { 
            LocalPlayerManager.next(this)
            updateSongInfo()
        }

        binding.btnPrev.setOnClickListener { 
            LocalPlayerManager.previous(this)
            updateSongInfo()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun updateSongInfo() {
        LocalPlayerManager.currentSong?.let {
            binding.tvFullTitle.text = it.title
            binding.tvFullArtist.text = it.artist
            binding.seekProgress.max = LocalPlayerManager.getDuration()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun startProgressUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                val currentPos = LocalPlayerManager.getCurrentPosition()
                binding.seekProgress.progress = currentPos
                binding.tvCurrentTime.text = formatTime(currentPos.toLong())
                binding.tvTotalTime.text = formatTime(LocalPlayerManager.getDuration().toLong())
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatTime(millis: Long): String {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
