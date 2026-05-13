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

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun subscribe(listener: PlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        getCurrentTrack()?.let { listener.onTrackChanged(it) }
        listener.onStatusChanged(isPlaying())
    }

    fun unsubscribe(listener: PlayerListener) {
        listeners.remove(listener)
    }

    fun getCurrentTrack(): Any? {
        return if (currentIndex in playlistQueue.indices) playlistQueue[currentIndex] else null
    }

    fun playOnline(song: OnlineSong, context: Context) {
        playlistQueue.add(song)
        currentIndex = playlistQueue.size - 1
        play(context)
    }

    fun getVolume(): Float = currentVolume

    fun setVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer?.setVolume(volume, volume)
    }

    // --- ENFILEIRAMENTO ---
    fun playNext(item: Any) {
        if (playlistQueue.isEmpty()) {
            // Se não houver fila, adiciona e já não inicia automaticamente a menos que chamem o play()
            playlistQueue.add(item)
            currentIndex = 0
        } else {
            // Adiciona logo após a música atual sem interromper o que está tocando
            playlistQueue.add(currentIndex + 1, item)
        }
    }

    fun addToEnd(item: Any) {
        if (playlistQueue.isEmpty()) {
            playlistQueue.add(item)
            currentIndex = 0
        } else {
            // Adiciona diretamente ao final da fila atual
            playlistQueue.add(item)
        }
    }

    fun toggleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        return repeatMode
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
                setVolume(currentVolume, currentVolume)
                prepareAsync()
                setOnPreparedListener { 
                    start()
                    notifyStatusChanged(true)
                    notifyTrackChanged(track)
                    updateService(context, "ACTION_PLAY")
                }
                setOnCompletionListener {
                    if (repeatMode == RepeatMode.ONE) play(context)
                    else next(context)
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
        listeners.forEach { it.onStatusChanged(playing) }
    }

    private fun notifyTrackChanged(item: Any) {
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

    fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) { }
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) = mediaPlayer?.seekTo(pos)
}
