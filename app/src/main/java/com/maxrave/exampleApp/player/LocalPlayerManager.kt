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
    
    var currentIndex: Int = -1
        private set
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE
    
    enum class RepeatMode { NONE, ONE, ALL }
    
    // Callbacks unificados (Removida a duplicação)
    var onTrackChanged: ((Any) -> Unit)? = null 
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
    }

    // --- GETTERS PARA O FULL PLAYER ---
    fun getCurrentTrack(): Any? = if (currentIndex in playlistQueue.indices) playlistQueue[currentIndex] else null
    
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(pos: Int) {
        mediaPlayer?.seekTo(pos)
    }

    fun isPlaying() = mediaPlayer?.isPlaying ?: false

    // --- LÓGICA DE REPRODUÇÃO ---
    fun play(context: Context) {
        val track = getCurrentTrack() ?: return
        
        // Ajustado para os nomes de campos corretos do seu modelo
        val dataSource = when (track) {
            is Song -> track.path 
            is OnlineSong -> track.url // Mude para track.streamingUrl se o erro persistir
            else -> return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(dataSource)
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

    fun setQueueAndPlay(list: List<Any>, index: Int, context: Context) {
        originalQueue = list.toMutableList()
        playlistQueue = if (isShuffle) list.shuffled().toMutableList() else list.toMutableList()
        
        currentIndex = if (isShuffle) {
            playlistQueue.indexOf(list[index])
        } else {
            index
        }
        play(context)
    }

    fun playOnline(onlineSong: OnlineSong, context: Context) {
        // Verificando pelo videoId conforme seu erro de compilação anterior
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

    // --- CONTROLES ---
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

    private fun handleCompletion(context: Context) {
        when (repeatMode) {
            RepeatMode.ONE -> play(context)
            RepeatMode.ALL -> next(context)
            RepeatMode.NONE -> {
                if (currentIndex < playlistQueue.size - 1) next(context)
                else stop()
            }
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        onPlaybackStatusChanged?.invoke(false)
    }

    // --- LÓGICA DO SWIPE ---
    fun removeFromQueue(position: Int) {
        if (position in playlistQueue.indices) {
            val removedIsCurrent = (position == currentIndex)
            playlistQueue.removeAt(position)
            
            if (removedIsCurrent) {
                stop()
            } else if (position < currentIndex) {
                currentIndex--
            }
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
