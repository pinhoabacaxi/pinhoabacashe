package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.service.PlaybackService
import com.maxrave.exampleApp.repository.RecentSongsManager
import com.maxrave.exampleApp.repository.PlayerPrefs

object LocalPlayerManager {
    private var recentManager: RecentSongsManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var originalList: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var currentVolume: Float = 1.0f

    // Propriedades para Audio Focus
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeLoss = false
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }

    var currentSong: Song? = null
        private set

    // Callbacks para a UI
    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

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

    // 1. Listener de Foco de Áudio
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeLoss) {
                    mediaPlayer?.setVolume(currentVolume, currentVolume)
                    mediaPlayer?.start()
                    onPlaybackStatusChanged?.invoke(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perda total: pausamos e não voltamos automaticamente
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perda temporária: pausamos para voltar depois
                wasPlayingBeforeLoss = mediaPlayer?.isPlaying ?: false
                mediaPlayer?.pause()
                onPlaybackStatusChanged?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Diminuir volume (notificações)
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    private fun saveCurrentState() {
        prefs?.savePlayerState(
            isShuffle, 
            repeatMode.ordinal, 
            currentVolume, 
            currentSong?.id ?: -1L
        )
    }
   fun init(context: Context) {
        if (prefs == null) {
            prefs = PlayerPrefs(context)
            isShuffle = prefs!!.getShuffle()
            repeatMode = RepeatMode.values()[prefs!!.getRepeatMode()]
            currentVolume = prefs!!.getVolume()
        }
    } 
   
    fun initRecentManager(context: Context) {
        recentManager = RecentSongsManager(context)
        handler.post(updateProgressRunnable)
    }

    // 2. Solicitação de Foco
    private fun requestAudioFocus(context: Context): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioManager?.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    // 3. Liberação de Foco
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(position: Int) { mediaPlayer?.seekTo(position) }
    
    fun setVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer?.setVolume(volume, volume)
    }

    fun getVolume(): Float = currentVolume

    fun startPlaying(context: Context, playlist: List<Song>, index: Int) {
        this.songList = playlist
        this.originalList = playlist.toList()
        this.currentIndex = index
        play(context)
    }

    private fun play(context: Context) {
        if (songList.isEmpty() || currentIndex !in songList.indices) return
    
        // Solicita o foco antes de iniciar o som
        if (!requestAudioFocus(context)) return

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
        saveCurrentState()
    }


    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                abandonAudioFocus()
            } else {
                if (requestAudioFocus(context)) {
                    it.start()
                }
            }
            onPlaybackStatusChanged?.invoke(it.isPlaying)
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
        }
    }

    private fun pauseInternal() {
        mediaPlayer?.pause()
        onPlaybackStatusChanged?.invoke(false)
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
        saveCurrentState()
    }

    
    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
            saveCurrentState()
        }
    }

    private fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
