package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.service.PlaybackService
import java.util.*

object LocalPlayerManager {
    private var recentManager: RecentSongsManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var originalList: List<Song> = emptyList() 
    private var currentIndex: Int = -1
    private var currentVolume: Float = 1.0f

    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }
    
    var currentSong: Song? = null
        private set

    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
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
        // Atualiza o índice para a posição da música atual na nova lista
        currentIndex = songList.indexOf(current)
    }

    fun initRecentManager(context: Context) {
        recentManager = RecentSongsManager(context)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer?.setVolume(volume, volume)
    }

    fun getVolume(): Float = currentVolume

    fun playList(songs: List<Song>, index: Int, context: Context) {
        songList = songs
        currentIndex = index
        play(context)
    }

    private fun play(context: Context) {
        if (currentIndex !in songList.indices) return
        val song = songList[currentIndex]
        currentSong = song // Atualiza a música atual

        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(context, Uri.parse(song.uri))
        mediaPlayer?.setVolume(currentVolume, currentVolume)
        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            // Lógica de repetição corrigida
            when(repeatMode) {
                RepeatMode.ONE -> play(context) // Repete a mesma
                else -> next(context) // Vai para a próxima (ALL ou NONE)
            }
        }

        recentManager?.addRecent(song.id)
        onTrackChanged?.invoke(song)
        onPlaybackStatusChanged?.invoke(true)
        
        updateService(context, "ACTION_UPDATE_NOTIFICATION")
    } // FECHAMENTO DA FUNÇÃO PLAY QUE ESTAVA FALTANDO

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onPlaybackStatusChanged?.invoke(false)
            } else {
                it.start()
                onPlaybackStatusChanged?.invoke(true)
            }
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
        }
    }

    fun next(context: Context) {
        if (songList.isEmpty()) return
        
        // Se for RepeatMode.NONE e estiver na última música, para a reprodução
        if (repeatMode == RepeatMode.NONE && currentIndex == songList.size - 1) {
            mediaPlayer?.stop()
            onPlaybackStatusChanged?.invoke(false)
            return
        }

        currentIndex = (currentIndex + 1) % songList.size
        play(context)
    }

    fun previous(context: Context) {
        if (songList.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else songList.size - 1
        play(context)
    }

    private fun updateService(context: Context, action: String) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            this.action = action
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
