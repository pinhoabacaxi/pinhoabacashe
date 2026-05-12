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
import androidx.core.app.NotificationCompat
import com.maxrave.exampleApp.MainActivity
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.receiver.NotificationReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log // Resolve o erro do Log
import com.maxrave.exampleApp.R // Resolve o erro do R (Ajuste se o pacote for diferente)
import com.maxrave.exampleApp.model.OnlineSong // Resolve o erro do OnlineSong


class PlaybackService : Service() {

    private val CHANNEL_ID = "music_player_channel"
    private val NOTIFICATION_ID = 1001

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (LocalPlayerManager.isPlaying()) {
                    LocalPlayerManager.togglePlayPause(this@PlaybackService)
                    showNotification(LocalPlayerManager.currentSong ?: return)
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
    
        // 1. REGRA DE OURO DO ANDROID 12+: Chame startForeground IMEDIATAMENTE.
        // Isso impede que o sistema mate o app enquanto o link do YouTube é processado.
        val notification = createPlaceholderNotification()
        startForeground(NOTIFICATION_ID, notification)
    
        // 2. Lógica baseada na ação enviada pelo LocalPlayerManager
        when (action) {
            "ACTION_STOP" -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            
            "ACTION_PREPARE_ONLINE" -> {
                // Apenas mantemos a notificação de "Carregando..." ativa.
                // O LocalPlayerManager chamará ACTION_UPDATE_NOTIFICATION quando o áudio estiver pronto.
                Log.d("PlaybackService", "Serviço em foreground: Preparando áudio online.")
            }
    
            "ACTION_UPDATE_NOTIFICATION" -> {
                val song = LocalPlayerManager.currentSong
                val onlineSong = LocalPlayerManager.currentOnlineSong
    
                // 3. Verifica se deve mostrar a notificação para música local ou online
                if (song != null) {
                    showNotification(song) // Sua função existente para músicas locais
                } else if (onlineSong != null) {
                    // Se o seu showNotification só aceita 'Song', você precisará 
                    // criar uma versão para 'OnlineSong' ou adaptar a existente.
                    showOnlineNotification(onlineSong) 
                }
            }
        }
    
        return START_NOT_STICKY
    }

    private fun createPlaceholderNotification(): Notification {
        val channelId = "playback_channel"
        val channelName = "Reprodução de Áudio"
        
        // Criar o canal de notificação para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Carregando música...")
            .setContentText("Aguarde um momento")
            .setSmallIcon(android.R.drawable.ic_media_play) // Use um ícone do seu app se preferir
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Impede que o usuário descarte a notificação enquanto carrega
            .build()
    }
    private fun showOnlineNotification(onlineSong: OnlineSong) {
        // Aqui você constrói a notificação real com o título e autor da música do YouTube
        // e usa o .url do streaming que obtivemos no extractor.
        
        val notification = NotificationCompat.Builder(this, "playback_channel")
            .setContentTitle(onlineSong.title)
            .setContentText(onlineSong.author)
            .setSmallIcon(R.drawable.ic_music_note)
            // Adicione controles de Play/Pause se necessário
            .setOngoing(true)
            .build()
    
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
    private fun showNotification(song: Song) {
        val isPlaying = LocalPlayerManager.isPlaying()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(null) 
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Anterior", getPendingAction("ACTION_PREVIOUS"))
            .addAction(playPauseIcon, "Play/Pause", getPendingAction("ACTION_PLAY_PAUSE"))
            .addAction(android.R.drawable.ic_media_next, "Próxima", getPendingAction("ACTION_NEXT"))
            .build()

        // Correção Crítica para Android 14 (Target SDK 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
