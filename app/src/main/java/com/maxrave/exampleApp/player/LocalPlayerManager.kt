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
    
    // CORREÇÃO: Apenas uma declaração de currentIndex
    var currentIndex: Int = -1
        private set
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    
    enum class RepeatMode { NONE, ONE, ALL }
    var repeatMode: RepeatMode = RepeatMode.NONE

    // Callbacks unificados
    var onTrackChanged: ((Any) -> Unit)? = null 
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
    }

    // --- LÓGICA DE FILA ---
    fun playNext(item: Any) {
        if (playlistQueue.isEmpty()) {
            playlistQueue.add(item)
            currentIndex = 0 
        } else {
            playlistQueue.add(currentIndex + 1, item)
        }
    }
    fun playPrevious(context: Context) {
        if (playlist.isEmpty()) return
    
        currentTrackIndex = if (currentTrackIndex > 0) {
            currentTrackIndex - 1
        } else {
            playlist.size - 1 // Volta para a última se estiver na primeira
        }
        play(context)
    }
    fun addToEnd(item: Any) {
        playlistQueue.add(item)
        if (playlistQueue.size == 1) {
            currentIndex = 0 
        }
    }

    fun getCurrentQueue() = playlistQueue

    // --- GETTERS E SETTERS ---
    fun getCurrentTrack(): Any? = if (currentIndex in playlistQueue.indices) playlistQueue[currentIndex] else null
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun isPlaying() = mediaPlayer?.isPlaying ?: false

    fun seekTo(pos: Int) {
        mediaPlayer?.seekTo(pos)
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer?.setVolume(volume, volume)
    }

    fun getVolume() = currentVolume

    fun toggleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        return repeatMode
    }

    // --- REPRODUÇÃO ---
    fun play(context: Context) {
        val track = getCurrentTrack() ?: return
        val dataSource = when (track) {
            is Song -> track.path 
            is OnlineSong -> track.url 
            else -> return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(dataSource)
                setVolume(currentVolume, currentVolume) // Aplica volume interno
                prepareAsync()
                setOnPreparedListener { 
                    start() 
                    onPlaybackStatusChanged?.invoke(true)
                    updateService(context, "ACTION_PLAY")
                }
                setOnCompletionListener { 
                    handleCompletion(context) 
                }
            }
            onTrackChanged?.invoke(track)
        } catch (e: Exception) { 
            Log.e("PlayerManager", "Erro ao tocar: ${e.message}")
        }
    }

    private fun handleCompletion(context: Context) {
        when (repeatMode) {
            RepeatMode.ONE -> play(context) 
            RepeatMode.ALL -> next(context) 
            RepeatMode.NONE -> {
                if (currentIndex < playlistQueue.size - 1) next(context) else stop()
            }
        }
    }

    fun setQueueAndPlay(list: List<Any>, index: Int, context: Context) {
        originalQueue = list.toMutableList()
        playlistQueue = if (isShuffle) list.shuffled().toMutableList() else list.toMutableList()
        currentIndex = if (isShuffle) playlistQueue.indexOf(list[index]) else index
        play(context)
    }

    fun playOnline(onlineSong: OnlineSong, context: Context) {
        val existingIndex = playlistQueue.indexOfFirst { 
            it is OnlineSong && it.videoId == onlineSong.videoId 
        }
        if (existingIndex != -1) {
            currentIndex = existingIndex
        } else {
            playlistQueue.add(currentIndex + 1, onlineSong)
            currentIndex++
        }
        play(context)
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onPlaybackStatusChanged?.invoke(false)
                updateService(context, "ACTION_PAUSE")
            } else {
                it.start()
                onPlaybackStatusChanged?.invoke(true)
                updateService(context, "ACTION_PLAY")
            }
        }
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
        onPlaybackStatusChanged?.invoke(false)
    }

    fun removeFromQueue(position: Int) {
        if (position in playlistQueue.indices) {
            val removedIsCurrent = (position == currentIndex)
            playlistQueue.removeAt(position)
            if (removedIsCurrent) stop() else if (position < currentIndex) currentIndex--
        }
    }

    fun restoreToQueue(position: Int, item: Any) {
        playlistQueue.add(position, item)
        if (position <= currentIndex) currentIndex++
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
}
