package com.maxrave.exampleApp.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maxrave.exampleApp.MainActivity
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.receiver.NotificationReceiver

class PlaybackService : Service() {

    private val CHANNEL_ID = "music_player_channel"
    private val NOTIFICATION_ID = 101

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val song = LocalPlayerManager.currentSong
        
        if (song != null) {
            showNotification(song, LocalPlayerManager.isPlaying())
        }

        return START_STICKY
    }
    
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            // Fones desconectados, pausar a música
                LocalPlayerManager.togglePlayPause(context!!)
            }
        }
    }

// No onCreate do Service:
    unregisterReceiver(noisyReceiver)

// No onDestroy do Service:
    

    private fun showNotification(song: Song, isPlaying: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Previous", getPendingAction("ACTION_PREVIOUS"))
            .addAction(playPauseIcon, "Play/Pause", getPendingAction("ACTION_PLAY_PAUSE"))
            .addAction(android.R.drawable.ic_media_next, "Next", getPendingAction("ACTION_NEXT"))
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getPendingAction(action: String): PendingIntent {
        val intent = Intent(this, NotificationReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reprodução de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Controles do player de música" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
