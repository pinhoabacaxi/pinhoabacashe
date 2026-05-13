package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.service.PlaybackService
import com.maxrave.exampleApp.repository.RecentSongsManager
import com.maxrave.exampleApp.repository.PlayerPrefs

object LocalPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var playlistQueue = mutableListOf<Any>()
    private var originalQueue = mutableListOf<Any>()
    private var currentVolume = 1.0f 
    
    // Interface para os componentes (MainActivity, etc) ouvirem o player
    interface PlayerListener {
        fun onTrackChanged(item: Any)
        fun onStatusChanged(isPlaying: Boolean)
    }

    private val listeners = mutableListOf<PlayerListener>()

    var currentIndex: Int = -1
        private set
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    
    enum class RepeatMode { NONE, ONE, ALL }
    var repeatMode: RepeatMode = RepeatMode.NONE

    // Callbacks para compatibilidade simples
    var onTrackChanged: ((Any) -> Unit)? = null 
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
    }

    // --- FUNÇÃO DE STATUS (CORRIGIDA) ---
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun subscribe(listener: PlayerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        // Ao se inscrever, já recebe o estado atual
        getCurrentTrack()?.let { listener.onTrackChanged(it) }
        listener.onStatusChanged(isPlaying())
    }

    fun unsubscribe(listener: PlayerListener) {
        listeners.remove(listener)
    }

    fun getCurrentTrack(): Any? {
        return if (currentIndex in playlistQueue.indices) playlistQueue[currentIndex] else null
    }

    fun setQueueAndPlay(list: List<Any>, index: Int, context: Context) {
        playlistQueue = list.toMutableList()
        originalQueue = list.toMutableList()
        currentIndex = index
        play(context)
    }

    fun play(context: Context) {
        val track = getCurrentTrack() ?: return
        
        mediaPlayer?.stop()
        mediaPlayer?.release()

        val path = when (track) {
            is Song -> track.path
            is OnlineSong -> track.url
            else -> return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepareAsync()
                setOnPreparedListener { 
                    start()
                    notifyStatusChanged(true)
                    notifyTrackChanged(track)
                    updateService(context, "ACTION_PLAY")
                }
                setOnCompletionListener {
                    next(context)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Erro ao tocar: ${e.message}")
        }
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            val playing = if (it.isPlaying) {
                it.pause()
                updateService(context, "ACTION_PAUSE")
                false
            } else {
                it.start()
                updateService(context, "ACTION_PLAY")
                true
            }
            notifyStatusChanged(playing)
        }
    }

    private fun notifyStatusChanged(playing: Boolean) {
        onPlaybackStatusChanged?.invoke(playing)
        listeners.forEach { it.onStatusChanged(playing) }
    }

    private fun notifyTrackChanged(item: Any) {
        onTrackChanged?.invoke(item)
        listeners.forEach { it.onTrackChanged(item) }
    }

    fun next(context: Context) {
        if (playlistQueue.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlistQueue.size
        play(context)
    }

    fun previous(context: Context) {
        if (playlistQueue.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlistQueue.size - 1
        play(context)
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        notifyStatusChanged(false)
    }

    fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) {
            Log.e("PlayerManager", "Service error: ${e.message}")
        }
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) = mediaPlayer?.seekTo(pos)
}
