package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences

class RecentSongsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_prefs", Context.MODE_PRIVATE)
    private val MAX_RECENT = 15

    fun addRecent(songId: Long) {
        val recentList = getRecentIds().toMutableList()
        val idStr = songId.toString()
        
        recentList.remove(idStr)
        recentList.add(0, idStr)

        val limitedList = if (recentList.size > MAX_RECENT) recentList.take(MAX_RECENT) else recentList
        
        // Salva como String única para preservar a ordem
        prefs.edit().putString("recent_list_ordered", limitedList.joinToString(",")).apply()
    }

    fun getRecentIds(): List<String> {
        val savedString = prefs.getString("recent_list_ordered", "") ?: ""
        return if (savedString.isEmpty()) emptyList() else savedString.split(",")
    }
}
