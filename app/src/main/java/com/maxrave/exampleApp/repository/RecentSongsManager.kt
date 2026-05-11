package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences
import com.maxrave.exampleApp.model.Song

class RecentSongsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_songs_prefs", Context.MODE_PRIVATE)
    private val MAX_RECENT_COUNT = 50

    fun addSongToRecent(songId: Long) { // Alterado para Long
        val currentRecent = getRecentIds().toMutableList()
        currentRecent.remove(songId.toString())
        currentRecent.add(0, songId.toString())

        if (currentRecent.size > MAX_RECENT_COUNT) {
            saveIds(currentRecent.take(MAX_RECENT_COUNT))
        } else {
            saveIds(currentRecent)
        }
    }

    private fun getRecentIds(): List<String> {
        val rawData = prefs.getString("recent_ids", "") ?: ""
        return if (rawData.isEmpty()) emptyList() else rawData.split(",")
    }

    private fun saveIds(ids: List<String>) {
        prefs.edit().putString("recent_ids", ids.joinToString(",")).apply()
    }

    fun getRecentSongs(allSongs: List<Song>): List<Song> {
        val recentIds = getRecentIds()
        // Especificando explicitamente o tipo Long para o mapa
        val songsMap: Map<Long, Song> = allSongs.associateBy { it.id }
        
        return recentIds.mapNotNull { idStr -> 
            val idLong = idStr.toLongOrNull()
            if (idLong != null) songsMap[idLong] else null
        }
    }
}
