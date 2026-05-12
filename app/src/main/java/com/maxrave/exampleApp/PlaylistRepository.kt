package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.Room.*

class PlaylistRepository(context: Context) {
    
    private val dao = AppDatabase.getDatabase(context).musicDao()

    // --- PLAYLISTS ---
    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(Playlist(name = name))
    }

    suspend fun getAllPlaylists(): List<Playlist> {
        return dao.getAllPlaylists()
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        dao.deletePlaylist(playlist)
    }

    // --- GERENCIAMENTO DE MÚSICAS ---
    suspend fun addSongToPlaylist(playlistId: Long, song: SongEntity) {
        dao.insertSong(song)
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun getSongsFromPlaylist(playlistId: Long): List<PlaylistWithSongs> {
        return dao.getSongsFromPlaylist(playlistId)
    }

    // --- FILTROS ---
    suspend fun getAllArtists(): List<String> {
        return dao.getUniqueArtists()
    }

    // --- FAVORITOS ---
    suspend fun isFavorite(songId: String): Boolean {
        return dao.isFavorite(songId)
    }

    suspend fun addFavorite(songId: String) {
        dao.addFavorite(FavoriteEntity(songId))
    }

    suspend fun removeFavorite(songId: String) {
        dao.removeFavorite(FavoriteEntity(songId))
    }
}
