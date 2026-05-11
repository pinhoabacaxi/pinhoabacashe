package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
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
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var originalList: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var currentVolume: Float = 1.0f
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }

    var currentSong: Song? = null
        private set

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

    fun init(context: Context) {
        recentManager = RecentSongsManager(context)
        prefs = PlayerPrefs(context)
        loadState()
        handler.post(updateProgressRunnable)
    }

    private fun loadState() {
        prefs?.let {
            isShuffle = it.isShuffle
            repeatMode = it.repeatMode
        }
    }

    private fun saveCurrentState() {
        prefs?.let {
            it.isShuffle = isShuffle
            it.repeatMode = repeatMode
        }
    }

    fun setList(list: List<Song>) {
        originalList = list
        songList = if (isShuffle) list.shuffled() else list
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun play(context: Context, song: Song) {
        val index = songList.indexOf(song)
        if (index != -1) {
            currentIndex = index
            play(context)
        }
    }

    fun play(context: Context) {
        if (currentIndex !in songList.indices) return
        
        val song = songList[currentIndex]
        currentSong = song

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, Uri.parse(song.uri))
        mediaPlayer?.setVolume(currentVolume, currentVolume)
        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            when (repeatMode) {
                RepeatMode.ONE -> play(context)
                else -> next(context)
            }
        }

        recentManager?.addRecent(song.id)
        onTrackChanged?.invoke(song)
        onPlaybackStatusChanged?.invoke(true)
        updateService(context, "ACTION_UPDATE_NOTIFICATION")
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
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
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
        current?.let { currentIndex = songList.indexOf(it) }
        saveCurrentState()
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        saveCurrentState()
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
