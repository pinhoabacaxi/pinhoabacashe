package com.maxrave.exampleApp.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.maxrave.exampleApp.model.Song

object LocalPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentQueue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    // Callbacks para a UI
    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null

    fun setQueue(songs: List<Song>, startIndex: Int) {
        currentQueue = songs
        playAt(startIndex)
    }

    private fun playAt(index: Int) {
        if (index !in currentQueue.indices) return
        
        currentIndex = index
        val song = currentQueue[currentIndex]

        mediaPlayer?.stop()
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            // O contexto será passado pela função que chama play
        }
        onTrackChanged?.invoke(song)
    }

    fun play(context: Context, song: Song) {
        // Se já for a música atual, apenas alterna play/pause
        if (currentIndex != -1 && currentQueue[currentIndex].id == song.id) {
            togglePlayback()
            return
        }

        // Se for nova, reseta e toca
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, song.uri)
            prepare()
            start()
            setOnCompletionListener { playNext(context) }
        }
        
        // Atualiza o índice na fila se ela existir
        currentIndex = currentQueue.indexOfFirst { it.id == song.id }
        onTrackChanged?.invoke(song)
        onPlaybackStatusChanged?.invoke(true)
    }

    fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onPlaybackStatusChanged?.invoke(false)
            } else {
                it.start()
                onPlaybackStatusChanged?.invoke(true)
            }
        }
    }

    fun playNext(context: Context) {
        if (currentQueue.isEmpty()) return
        val nextIndex = (currentIndex + 1) % currentQueue.size
        play(context, currentQueue[nextIndex])
    }

    fun playPrevious(context: Context) {
        if (currentQueue.isEmpty()) return
        var prevIndex = currentIndex - 1
        if (prevIndex < 0) prevIndex = currentQueue.size - 1
        play(context, currentQueue[prevIndex])
    }

    fun isPlaying() = mediaPlayer?.isPlaying ?: false
    
    fun getCurrentSong(): Song? = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else null

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
