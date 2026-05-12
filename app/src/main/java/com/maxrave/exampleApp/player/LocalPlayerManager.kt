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
    
    // Lista Híbrida Única: Pode conter Song ou OnlineSong
    private var playlistQueue = mutableListOf<Any>()
    private var originalQueue = mutableListOf<Any>()
    
    var currentIndex: Int = -1
        private set
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }

    // Callbacks para atualizar a UI
    var onTrackChanged: ((Any) -> Unit)? = null // Retorna Song ou OnlineSong
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
    }

    // Tocar uma lista (Ex: ao clicar numa música da biblioteca ou playlist)
    fun setQueueAndPlay(list: List<Any>, index: Int, context: Context) {
        originalQueue = list.toMutableList()
        playlistQueue = if (isShuffle) list.shuffled().toMutableList() else list.toMutableList()
        
        // Encontrar o novo índice se estiver em modo shuffle
        currentIndex = if (isShuffle) {
            playlistQueue.indexOf(list[index])
        } else {
            index
        }
        play(context)
    }

    // Tocar música específica do YouTube
    fun playOnline(onlineSong: OnlineSong, context: Context) {
        // Se a música já está na fila, apenas pula para ela. Se não, adiciona após a atual.
        val existingIndex = playlistQueue.indexOfFirst { it is OnlineSong && it.id == onlineSong.id }
        if (existingIndex != -1) {
            currentIndex = existingIndex
        } else {
            playlistQueue.add(currentIndex + 1, onlineSong)
            currentIndex++
        }
        play(context)
    }

    fun play(context: Context) {
        if (currentIndex !in playlistQueue.indices) return
        
        val currentItem = playlistQueue[currentIndex]
        val path = when (currentItem) {
            is Song -> currentItem.path
            is OnlineSong -> currentItem.streamingUrl ?: return
            else -> return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
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
            onTrackChanged?.invoke(currentItem)
        } catch (e: Exception) {
            Log.e("PlayerManager", "Erro ao tocar: ${e.message}")
        }
    }

    // LÓGICA DO SWIPE: Remover da fila
    fun removeFromQueue(position: Int) {
        if (position in playlistQueue.indices) {
            playlistQueue.removeAt(position)
            
            if (position == currentIndex) {
                // Se removeu a que está tocando, para ou pula
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

    private fun handleCompletion(context: Context) {
        when (repeatMode) {
            RepeatMode.ONE -> play(context)
            RepeatMode.ALL -> next(context)
            RepeatMode.NONE -> {
                if (currentIndex < playlistQueue.size - 1) next(context)
                else onPlaybackStatusChanged?.invoke(false)
            }
        }
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

    fun isPlaying() = mediaPlayer?.isPlaying ?: false

    fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }
}
