package com.maxrave.exampleApp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        setupObservers()
        startProgressUpdate()
    }

    private fun setupUI() {
        // Inicializa as informações da música e estados dos botões
        updateSongInfo()
        updateShuffleUI()
        updateRepeatUI()
        updatePlayPauseButton(LocalPlayerManager.isPlaying())

        // Botão Shuffle
        binding.btnShuffle.setOnClickListener {
            LocalPlayerManager.toggleShuffle()
            updateShuffleUI()
        }

        // Botão Repeat
        binding.btnRepeat.setOnClickListener {
            LocalPlayerManager.toggleRepeat()
            updateRepeatUI()
        }

        // Configuração Volume
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
        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {
                p0?.let { LocalPlayerManager.seekTo(it.progress) }
            }
        })

        binding.btnPlayPause.setOnClickListener {
            LocalPlayerManager.togglePlayPause(this)
            // O observer atualizará o ícone automaticamente
        }

        binding.btnNext.setOnClickListener { 
            LocalPlayerManager.next(this)
        }

        binding.btnPrev.setOnClickListener { 
            LocalPlayerManager.previous(this)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupObservers() {
        // Atualiza a tela quando a música mudar (ex: fim da faixa ou clique em Next)
        LocalPlayerManager.onTrackChanged = {
            runOnUiThread { updateSongInfo() }
        }
        // Atualiza o ícone de Play/Pause quando o estado mudar
        LocalPlayerManager.onPlaybackStatusChanged = { isPlaying ->
            runOnUiThread { updatePlayPauseButton(isPlaying) }
        }
    }

    private fun updateSongInfo() {
        LocalPlayerManager.currentSong?.let { song ->
            binding.tvFullTitle.text = song.title
            binding.tvFullArtist.text = song.artist
            binding.seekProgress.max = LocalPlayerManager.getDuration()
            
            // Caso tenha um ícone de fallback ou carregamento de capa
            binding.imgFullAlbum.setImageResource(R.drawable.ic_default_album)
        }
    }

    private fun updateShuffleUI() {
        // Altera a cor do ícone para indicar se está ativo
        val colorRes = if (LocalPlayerManager.isShuffle) R.color.purple_500 else R.color.black
        binding.btnShuffle.setColorFilter(ContextCompat.getColor(this, colorRes))
        binding.btnShuffle.setImageResource(R.drawable.ic_shuffle)
    }

    private fun updateRepeatUI() {
        // Altera o ícone baseado no modo de repetição
        val iconRes = when(LocalPlayerManager.repeatMode) {
            LocalPlayerManager.RepeatMode.ALL -> R.drawable.ic_repeat_all
            LocalPlayerManager.RepeatMode.ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat_off
        }
        binding.btnRepeat.setImageResource(iconRes)
        
        // Opcional: mudar a cor se não estiver em modo "OFF"
        val colorRes = if (LocalPlayerManager.repeatMode != LocalPlayerManager.RepeatMode.NONE) 
            R.color.purple_500 else R.color.black
        binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, colorRes))
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
    }

    private fun startProgressUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                val currentPos = LocalPlayerManager.getCurrentPosition()
                val duration = LocalPlayerManager.getDuration()
                
                binding.seekProgress.progress = currentPos
                binding.tvCurrentTime.text = formatTime(currentPos.toLong())
                binding.tvTotalTime.text = formatTime(duration.toLong())
                
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Evita vazamento de memória limpando os callbacks
        LocalPlayerManager.onTrackChanged = null
        LocalPlayerManager.onPlaybackStatusChanged = null
    }
}
