package com.maxrave.exampleApp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxrave.exampleApp.player.LocalPlayerManager

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_PLAY_PAUSE" -> LocalPlayerManager.togglePlayPause()
            "ACTION_NEXT" -> LocalPlayerManager.next(context)
            "ACTION_PREVIOUS" -> LocalPlayerManager.previous(context)
            "ACTION_STOP" -> {
                // Implementar parada total se necessário
            }
        }
    }
}
