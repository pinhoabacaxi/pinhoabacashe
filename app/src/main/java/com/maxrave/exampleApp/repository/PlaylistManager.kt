package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences

class PlaylistManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)

    // Retorna todos os nomes de playlists criadas de forma mutável
    fun getPlaylistNames(): MutableSet<String> {
        val names = prefs.getStringSet("all_playlists", null)
        return names?.toMutableSet() ?: mutableSetOf()
    }

    fun createPlaylist(name: String) {
        val names = getPlaylistNames()
        if (!names.contains(name)) {
            names.add(name)
            prefs.edit().putStringSet("all_playlists", names).apply()
        }
    }

    fun addSongToPlaylist(playlistName: String, songId: Long) {
        val songIds = getSongIdsFromPlaylist(playlistName)
        songIds.add(songId.toString())
        prefs.edit().putStringSet("playlist_$playlistName", songIds).apply()
    }

    fun getSongIdsFromPlaylist(playlistName: String): MutableSet<String> {
        val ids = prefs.getStringSet("playlist_$playlistName", null)
        return ids?.toMutableSet() ?: mutableSetOf()
    }

    fun removePlaylist(name: String) {
        val names = getPlaylistNames()
        if (names.contains(name)) {
            names.remove(name)
            prefs.edit().apply {
                putStringSet("all_playlists", names)
                remove("playlist_$name")
            }.apply()
        }
    }
}
