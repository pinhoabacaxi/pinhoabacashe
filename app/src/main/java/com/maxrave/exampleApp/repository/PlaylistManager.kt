package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences

class PlaylistManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)

    // Retorna todos os nomes de playlists criadas
    fun getPlaylistNames(): MutableSet<String> {
        return prefs.getStringSet("all_playlists", mutableSetOf()) ?: mutableSetOf()
    }

    fun createPlaylist(name: String) {
        val names = getPlaylistNames()
        names.add(name)
        prefs.edit().putStringSet("all_playlists", names).apply()
    }

    fun addSongToPlaylist(playlistName: String, songId: Long) {
        val songIds = getSongIdsFromPlaylist(playlistName).toMutableSet()
        songIds.add(songId.toString())
        prefs.edit().putStringSet("playlist_$playlistName", songIds).apply()
    }

    fun getSongIdsFromPlaylist(playlistName: String): Set<String> {
        return prefs.getStringSet("playlist_$playlistName", emptySet()) ?: emptySet()
    }

    fun removePlaylist(name: String) {
        val names = getPlaylistNames()
        names.remove(name)
        prefs.edit().apply {
            putStringSet("all_playlists", names)
            remove("playlist_$name")
        }.apply()
    }
}
