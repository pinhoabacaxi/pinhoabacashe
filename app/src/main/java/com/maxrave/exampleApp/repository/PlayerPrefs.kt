package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences
import com.maxrave.exampleApp.player.LocalPlayerManager

class PlayerPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    var isShuffle: Boolean
        get() = prefs.getBoolean("is_shuffle", false)
        set(value) = prefs.edit().putBoolean("is_shuffle", value).apply()

    var repeatMode: LocalPlayerManager.RepeatMode
        get() {
            val name = prefs.getString("repeat_mode", LocalPlayerManager.RepeatMode.NONE.name)
            return try { LocalPlayerManager.RepeatMode.valueOf(name!!) } catch (e: Exception) { LocalPlayerManager.RepeatMode.NONE }
        }
        set(value) = prefs.edit().putString("repeat_mode", value.name).apply()
}
