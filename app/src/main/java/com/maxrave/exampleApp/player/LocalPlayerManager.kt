package com.maxrave.exampleApp.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.maxrave.exampleApp.model.Song

object LocalPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var currentIndex: Int = -1

    // Propriedades que a MainActivity está tentando acessar
    var currentSong: Song? = null
        private set

    var onTrackChanged: ((Song) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun playList(songs: List<Song>, index: Int, context: Context) {
        songList = songs
        currentIndex = index
        play(context)
    }

    private fun play(context: Context) {
        if (currentIndex !in songList.indices) return
        
        val song = songList[currentIndex]
        currentSong = song

        mediaPlayer?.stop()
        mediaPlayer?.release()

        // CORREÇÃO AQUI: Convertendo String para Uri.parse()
        mediaPlayer = MediaPlayer.create(context, Uri.parse(song.uri))
        mediaPlayer?.start()

        onTrackChanged?.invoke(song)
        onPlaybackStatusChanged?.invoke(true)

        mediaPlayer?.setOnCompletionListener {
            next(context)
        }
    }

    fun togglePlayPause(context: Context) {
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
        if (songList.isEmpty()) return
        currentIndex = (currentIndex + 1) % songList.size
        play(context)
    }

    fun previous(context: Context) {
        if (songList.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else songList.size - 1
        play(context)
    }
}
