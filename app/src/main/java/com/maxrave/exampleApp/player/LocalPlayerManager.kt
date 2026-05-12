package com.maxrave.exampleApp.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.service.PlaybackService
import com.maxrave.exampleApp.repository.RecentSongsManager
import com.maxrave.exampleApp.repository.PlayerPrefs

object LocalPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var originalList: List<Song> = emptyList()
    var currentIndex: Int = -1
        private set
    
    private var recentManager: RecentSongsManager? = null
    private var prefs: PlayerPrefs? = null
    
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.NONE

    enum class RepeatMode { NONE, ONE, ALL }

    var currentSong: Song? = null
        private set
    var currentOnlineSong: OnlineSong? = null
        private set

    var onTrackChanged: ((Song) -> Unit)? = null
    var onOnlineTrackChanged: ((OnlineSong) -> Unit)? = null
    var onPlaybackStatusChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((current: Int, total: Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    try {
                        onProgressChanged?.invoke(it.currentPosition, it.duration)
                    } catch (e: Exception) { }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun init(context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            recentManager = RecentSongsManager(context)
            prefs = PlayerPrefs(context)
            handler.post(updateProgressRunnable)
        }
    }

    /**
     * Toca uma música vinda da busca online (YouTube)
     */
    fun playOnline(onlineSong: OnlineSong, context: Context) {
        val url = onlineSong.streamUrl ?: return
        
        currentSong = null
        currentOnlineSong = onlineSong

        // CORREÇÃO CRUCIAL: Iniciar o serviço IMEDIATAMENTE antes do buffering
        // Isso evita o crash de 1 minuto pois o serviço já nasce com uma notificação.
        updateService(context, "ACTION_PREPARE_ONLINE")

        try {
            mediaPlayer?.apply {
                reset()
                setDataSource(url)
                
                // Usamos Async para não travar a UI enquanto o YouTube responde
                prepareAsync() 
                
                setOnPreparedListener {
                    it.start()
                    onPlaybackStatusChanged?.invoke(true)
                    onOnlineTrackChanged?.invoke(onlineSong)
                    // Atualiza a notificação de "Carregando" para o nome real da música
                    updateService(context, "ACTION_UPDATE_NOTIFICATION")
                }

                setOnCompletionListener {
                    onPlaybackStatusChanged?.invoke(false)
                    handleCompletion(context)
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerManager", "Erro no stream: $what, $extra")
                    onPlaybackStatusChanged?.invoke(false)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Falha ao configurar DataSource: ${e.message}")
        }
    }

    fun startPlaying(context: Context, list: List<Song>, position: Int) {
        setList(list)
        currentIndex = position
        if (currentIndex in songList.indices) {
            play(context, songList[currentIndex])
        }
    }

    private fun setList(list: List<Song>) {
        this.originalList = list
        this.songList = if (isShuffle) list.shuffled() else list
    }

    fun play(context: Context, song: Song? = null) {
        val targetSong = song ?: if (currentIndex in songList.indices) songList[currentIndex] else return
        
        currentIndex = songList.indexOfFirst { it.id == targetSong.id }
        currentSong = targetSong
        currentOnlineSong = null

        try {
            mediaPlayer?.apply {
                reset()
                val trackUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    targetSong.id
                )
                setDataSource(context, trackUri)
                prepare()
                start()
                
                setOnCompletionListener {
                    handleCompletion(context)
                }
            }
            
            recentManager?.addSongToRecent(targetSong.id)
            onTrackChanged?.invoke(targetSong)
            onPlaybackStatusChanged?.invoke(true)
            
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
            
        } catch (e: Exception) {
            Log.e("PlayerManager", "Erro ao tocar local: ${e.message}")
            next(context)
        }
    }

    private fun handleCompletion(context: Context) {
        // Se for online, apenas paramos (ou você pode implementar autoplay similar aqui)
        if (currentOnlineSong != null) {
            onPlaybackStatusChanged?.invoke(false)
            return
        }

        when (repeatMode) {
            RepeatMode.ONE -> play(context, currentSong)
            RepeatMode.ALL -> next(context)
            RepeatMode.NONE -> {
                if (currentIndex < songList.size - 1) next(context)
                else onPlaybackStatusChanged?.invoke(false)
            }
        }
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            onPlaybackStatusChanged?.invoke(it.isPlaying)
            updateService(context, "ACTION_UPDATE_NOTIFICATION")
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

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
        songList = if (isShuffle) originalList.shuffled() else originalList
        current?.let { song ->
            currentIndex = songList.indexOfFirst { it.id == song.id }
        }
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    /**
     * Gerencia a chamada do serviço de forma segura para evitar crashes
     */
    fun updateService(context: Context, action: String) {
        try {
            val intent = Intent(context, PlaybackService::class.java).apply { 
                this.action = action 
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Erro ao iniciar serviço: ${e.message}")
        }
    }
}
