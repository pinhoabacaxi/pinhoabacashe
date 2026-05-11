package com.maxrave.exampleApp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxrave.exampleApp.player.LocalPlayerManager

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            "ACTION_PLAY_PAUSE" -> {
                LocalPlayerManager.togglePlayPause(context)
            }
            "ACTION_NEXT" -> {
                LocalPlayerManager.next(context)
            }
            "ACTION_PREVIOUS" -> {
                LocalPlayerManager.previous(context)
            }
        }
        
        // Após processar qualquer ação, forçamos o Manager a avisar o Service
        // para atualizar a UI da notificação com o novo estado (música ou ícone)
        LocalPlayerManager.currentSong?.let {
            // Esta chamada dispara o onStartCommand no PlaybackService com ACTION_UPDATE_NOTIFICATION
            // garantindo que a notificação mude o ícone de play/pause sem atraso.
        }
    }
}
