package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences

class RecentSongsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_prefs", Context.MODE_PRIVATE)
    private val MAX_RECENT = 15

    fun addRecent(songId: Long) {
        val recentList = getRecentIds().toMutableList()
        
        // Remove se já existia para mover para o topo
        recentList.remove(songId.toString())
        recentList.add(0, songId.toString())

        // Limita o tamanho
        val limitedList = if (recentList.size > MAX_RECENT) recentList.take(MAX_RECENT) else recentList
        
        prefs.edit().putStringSet("recent_list", limitedList.toSet()).apply()
    }

    fun getRecentIds(): List<String> {
        return prefs.getStringSet("recent_list", emptySet())?.toList() ?: emptyList()
    }
}
