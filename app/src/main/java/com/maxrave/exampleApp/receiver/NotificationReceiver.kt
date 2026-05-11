package com.maxrave.exampleApp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxrave.exampleApp.player.LocalPlayerManager
import com.maxrave.exampleApp.service.PlaybackService

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
            "ACTION_STOP" -> {
                val stopIntent = Intent(context, PlaybackService::class.java).apply {
                    this.action = "ACTION_STOP"
                }
                context.startService(stopIntent)
            }
        }
        
        // Garante que a notificação seja atualizada para refletir o novo estado (Play/Pause ou nova música)
        // O LocalPlayerManager já dispara essa atualização internamente em suas funções, 
        // mas reforçar aqui evita atrasos na UI da notificação.
    }
}
