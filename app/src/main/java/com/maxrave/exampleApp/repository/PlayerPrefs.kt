package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences
import com.maxrave.exampleApp.player.LocalPlayerManager

class PlayerPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)

    fun savePlayerState(isShuffle: Boolean, repeatMode: Int, volume: Float, lastSongId: Long) {
        prefs.edit().apply {
            putBoolean("shuffle", isShuffle)
            putInt("repeat", repeatMode)
            putFloat("volume", volume)
            putLong("last_song_id", lastSongId)
            apply()
        }
    }

    fun getShuffle(): Boolean = prefs.getBoolean("shuffle", false)
    fun getRepeatMode(): Int = prefs.getInt("repeat", 0) // 0 = NONE
    fun getVolume(): Float = prefs.getFloat("volume", 1.0f)
    fun getLastSongId(): Long = prefs.getLong("last_song_id", -1L)
}
