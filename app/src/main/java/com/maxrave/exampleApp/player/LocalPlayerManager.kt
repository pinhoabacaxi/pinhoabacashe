package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.service.PlaybackService
import com.maxrave.exampleApp.repository.RecentSongsManager

object LocalPlayerManager {
    private var recentManager: RecentSongsManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var originalList: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var currentVolume: Float = 1.0f
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
            // Recuperou o foco (ex: a chamada terminou)
                if (wasPlayingBeforeLoss) {
                    mediaPlayer?.setVolume(currentVolume, currentVolume)
                    mediaPlayer?.start()
                    onPlaybackStatusChanged?.invoke(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
            // Perda permanente (ex: outro app de música começou a tocar)
                pause(contextForFocus) // Pausa e não volta sozinho
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // Perda temporária (ex: toque de notificação curto)
                wasPlayingBeforeLoss = mediaPlayer?.isPlaying ?: false
                mediaPlayer?.pause()
                onPlaybackStatusChanged?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            // "Ducking": baixar o volume enquanto outro som toca ao fundo
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                }
            }
        }
    }

    private var wasPlayingBeforeLoss = false
    private lateinit var contextForFocus: Context

    var isShuffle: Boolean = false // Nome correto usado na Activity
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }

    var currentSong: Song? = null
        private set

    // Callbacks para a UI
    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

    // Handler para atualizar o progresso continuamente
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    onProgressChanged?.invoke(it.currentPosition, it.duration)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun initRecentManager(context: Context) {
        recentManager = RecentSongsManager(context)
        handler.post(updateProgressRunnable) // Inicia a atualização de progresso
    }
    private fun requestAudioFocus(context: Context): Boolean {
        contextForFocus = context
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
        // Armazene o focusRequest para poder abandonar depois
            currentFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun seekTo(position: Int) { mediaPlayer?.seekTo(position) }
    fun setVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer?.setVolume(volume, volume)
    }
    fun startPlaying(context: Context, playlist: List<Song>, index: Int) {
        this.songList = playlist
        this.originalList = playlist.toList()
        this.currentIndex = index
        play(context)
    }
    fun toggleShuffle() {
        isShuffle = !isShuffle
        val current = currentSong
        if (isShuffle) {
            originalList = songList.toList()
            songList = songList.shuffled()
        } else {
            songList = originalList
        }
        currentIndex = songList.indexOf(current)
    }
    
    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    private fun play(context: Context) {
        if (songList.isEmpty() || currentIndex !in songList.indices) return
    
    // Solicita o foco antes de começar
        if (!requestAudioFocus(context)) {
            return // Se não conseguir o foco, não toca
        }

        val song = songList[currentIndex]
        currentSong = song

        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(context, Uri.parse(song.uri))
        mediaPlayer?.setVolume(currentVolume, currentVolume)
        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            when(repeatMode) {
                RepeatMode.ONE -> play(context)
                else -> next(context)
            }
        }

        recentManager?.addRecent(song.id)
        onTrackChanged?.invoke(song)
        onPlaybackStatusChanged?.invoke(true)
        updateService(context, "ACTION_UPDATE_NOTIFICATION")
    }

    fun next(context: Context) {
        if (songList.isEmpty()) return
        currentIndex = (currentIndex + 1) % songList.size
        play(context)
    }

    fun previous(context: Context) {
        if (songList.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else songList.size - 1
        play(context)
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            onPlaybackStatusChanged?.invoke(it.isPlaying)
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
        }
    }
    fun abandonAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
