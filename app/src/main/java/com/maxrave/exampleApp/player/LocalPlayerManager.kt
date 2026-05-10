package com.maxrave.exampleApp.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.maxrave.exampleApp.model.Song

object LocalPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentQueue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null

    fun playList(songs: List<Song>, startIndex: Int, context: Context) {
        currentQueue = songs
        currentIndex = startIndex
        playCurrent(context)
    }

    private fun updateService(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
        } else {
        context.startService(intent)
        }
    }

    private fun playCurrent(context: Context) {
        if (currentIndex !in currentQueue.indices) return

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
            setDataSource(context, song.uri)
            prepareAsync()
            setOnPreparedListener { 
                start()
                onPlaybackStatusChanged?.invoke(true)
            }
            setOnCompletionListener { next(context) }
        }
        onTrackChanged?.invoke(song)
        private fun playCurrent(context: Context) {
        if (currentIndex !in currentQueue.indices) return

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
            setDataSource(context, song.uri)
            prepareAsync()
            setOnPreparedListener { 
                start()
                onPlaybackStatusChanged?.invoke(true)
            // CHAMADA AQUI: Notifica o serviço que a música começou
                updateService(context) 
        }
        setOnCompletionListener { next(context) }
    }
    onTrackChanged?.invoke(song)
}
    }

    fun togglePlayPause() {
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

    fun next(context: Context) {
        if (currentQueue.isEmpty()) return
        currentIndex = (currentIndex + 1) % currentQueue.size
        playCurrent(context)
    }

    fun previous(context: Context) {
        if (currentQueue.isEmpty()) return
        currentIndex = if (currentIndex <= 0) currentQueue.size - 1 else currentIndex - 1
        playCurrent(context)
    }

    fun isPlaying() = mediaPlayer?.isPlaying ?: false
    
    fun getCurrentSong(): Song? = if (currentIndex in currentQueue.indices) currentQueue[currentIndex] else null
}
