package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.Room.*
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.exampleApp.model.Song
import com.maxrave.kotlinyoutubeextractor.VideoMeta

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

    // --- GERENCIAMENTO DE MÚSICAS (ATUALIZADO) ---
    
    /**
     * Esta função agora aceita 'Any' para que os Adapters possam passar
     * qualquer tipo de música (Local, Online ou Meta) sem erro de tipo.
     */
    suspend fun addSongToPlaylist(playlistId: Long, item: Any) {
        // Converte o objeto recebido para a entidade do banco de dados (SongEntity)
        val songEntity = when (item) {
            is Song -> SongEntity(
                id = item.id.toString(),
                title = item.title,
                artist = item.artist,
                path = item.path,
                thumbnailUrl = null,
                isOnline = false
            )
            is OnlineSong -> SongEntity(
                id = item.videoId,
                title = item.title,
                artist = item.author,
                path = item.url,
                thumbnailUrl = item.thumbnailUrl,
                isOnline = true
            )
            is VideoMeta -> SongEntity(
                id = item.videoId,
                title = item.title,
                artist = item.author,
                path = "", // URL ainda não extraída na busca
                thumbnailUrl = item.thumbnailUrl,
                isOnline = true
            )
            is SongEntity -> item
            else -> return
        }

        // Insere a música no banco (se não existir) e cria o vínculo com a playlist
        dao.insertSong(songEntity)
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songEntity.id))
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
