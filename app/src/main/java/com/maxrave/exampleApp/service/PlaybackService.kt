package com.maxrave.exampleApp.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.maxrave.exampleApp.MainActivity
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.receiver.NotificationReceiver

class PlaybackService : Service() {

    private val CHANNEL_ID = "music_player_channel"
    private val NOTIFICATION_ID = 101

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (LocalPlayerManager.isPlaying()) {
                    LocalPlayerManager.togglePlayPause(this@PlaybackService)
                    // Atualiza a notificação com o que estiver tocando no momento
                    updateGeneralNotification()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
    
        // 1. REGRA DE OURO ANDROID 12+: Chame startForeground IMEDIATAMENTE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
            NOTIFICATION_ID, 
            notification, 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    } else {
        startForeground(NOTIFICATION_ID, notification)
    }

        // Iniciamos com um placeholder e atualizamos logo em seguida.
        val placeholder = createPlaceholderNotification()
        startForegroundServiceSafe(placeholder)
    
        when (action) {
            "ACTION_STOP" -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            
            "ACTION_PREPARE_ONLINE" -> {
                Log.d("PlaybackService", "Preparando áudio online...")
                // Mantém o placeholder visível
            }
    
            "ACTION_PLAY", "ACTION_PAUSE", "ACTION_UPDATE_NOTIFICATION" -> {
                updateGeneralNotification()
            }
        }
    
        return START_NOT_STICKY
    }

    /**
     * Identifica automaticamente se a música é local ou online e chama a função correta
     */
    private fun updateGeneralNotification() {
        val currentTrack = LocalPlayerManager.getCurrentTrack()
        val isPlaying = LocalPlayerManager.isPlaying()
        
        val notification = when (currentTrack) {
            is Song -> buildNotification(currentTrack.title, currentTrack.artist, currentTrack.path, isPlaying)
            is OnlineSong -> buildNotification(currentTrack.title, currentTrack.author, currentTrack.thumbnailUrl, isPlaying)
            else -> createPlaceholderNotification()
        }
        
        startForegroundServiceSafe(notification)
    }
    private fun buildNotification(title: String, artist: String, artPath: Any?, isPlaying: Boolean): Notification {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note) // Substitua pelo seu ícone de nota
            .setOngoing(isPlaying)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Anterior", getPendingAction("ACTION_PREVIOUS"))
            .addAction(playPauseIcon, "Play/Pause", getPendingAction("ACTION_PLAY_PAUSE"))
            .addAction(android.R.drawable.ic_media_next, "Próxima", getPendingAction("ACTION_NEXT"))
            .setContentIntent(getMainContentIntent())
            .build()
    }

    private fun createPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Carregando música...")
            .setContentText("Aguarde um momento")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showOnlineNotification(onlineSong: OnlineSong) {
        val isPlaying = LocalPlayerManager.isPlaying()
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(onlineSong.title)
            .setContentText(onlineSong.author)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Anterior", getPendingAction("ACTION_PREVIOUS"))
            .addAction(playPauseIcon, "Play/Pause", getPendingAction("ACTION_PLAY_PAUSE"))
            .addAction(android.R.drawable.ic_media_next, "Próxima", getPendingAction("ACTION_NEXT"))
            .setContentIntent(getMainContentIntent())
            .build()
    
        startForegroundServiceSafe(notification)
    }

    private fun showNotification(song: Song) {
        val isPlaying = LocalPlayerManager.isPlaying()
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setContentIntent(getMainContentIntent())
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Anterior", getPendingAction("ACTION_PREVIOUS"))
            .addAction(playPauseIcon, "Play/Pause", getPendingAction("ACTION_PLAY_PAUSE"))
            .addAction(android.R.drawable.ic_media_next, "Próxima", getPendingAction("ACTION_NEXT"))
            .build()

        startForegroundServiceSafe(notification)
    }

    private fun startForegroundServiceSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getMainContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPendingAction(action: String): PendingIntent {
        val intent = Intent(this, NotificationReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reprodução de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply { 
                description = "Controles do player de música"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) { }
    }
}
