package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.Room.*

class PlaylistRepository(context: Context) {
    
    // Instancia o banco de dados
    private val dao = AppDatabase.getDatabase(context).musicDao()

    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(Playlist(name = name))
    }

    suspend fun getAllPlaylists(): List<Playlist> {
        return dao.getAllPlaylists()
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: SongEntity) {
        // 1. Insere a música na tabela geral (ignora se já existir)
        dao.insertSong(song)
        // 2. Cria o vínculo entre a música e a playlist
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
    }

    suspend fun getSongsFromPlaylist(playlistId: Long): List<PlaylistWithSongs> {
        return dao.getSongsFromPlaylist(playlistId)
    }
}
