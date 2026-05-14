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

    /**
     * Busca o ID de uma playlist pelo nome. 
     * Essencial para o Worker vincular músicas baixadas sem saber o ID.
     */
    suspend fun getPlaylistIdByName(name: String): Long? {
        return dao.getAllPlaylists().find { it.name.equals(name, ignoreCase = true) }?.id
    }

    // --- GERENCIAMENTO DE MÚSICAS ---
    
    /**
     * Insere ou atualiza uma música e a vincula a uma playlist.
     * @param playlistId O ID da playlist no Room.
     * @param item Pode ser Song, OnlineSong, VideoMeta ou SongEntity.
     * @param localPath Opcional: O caminho do arquivo .mp3 no armazenamento (usado pelo Worker).
     */
    suspend fun addSongToPlaylist(playlistId: Long, item: Any, localPath: String? = null) {
        val songEntity = when (item) {
            is Song -> SongEntity(
                id = item.id.toString(),
                title = item.title,
                artist = item.artist,
                sourcePath = localPath ?: item.path,
                thumbnailUrl = null,
                isOnline = false
            )
            is OnlineSong -> SongEntity(
                id = item.videoId,
                title = item.title,
                artist = item.author,
                sourcePath = localPath ?: item.url,
                thumbnailUrl = item.thumbnailUrl,
                isOnline = localPath == null // Se tem path local, não é mais "apenas online"
            )
            is VideoMeta -> SongEntity(
                id = item.videoId,
                title = item.title,
                artist = item.author,
                sourcePath = localPath ?: "", 
                thumbnailUrl = item.thumbnailUrl,
                isOnline = localPath == null
            )
            is SongEntity -> item
            else -> return
        }

        dao.insertSong(songEntity)
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songEntity.id))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun getSongsFromPlaylist(playlistId: Long): List<PlaylistWithSongs> {
        return dao.getSongsFromPlaylist(playlistId)
    }

    // --- FAVORITOS ---
    suspend fun isFavorite(songId: String): Boolean = dao.isFavorite(songId)

    suspend fun addFavorite(songId: String) = dao.addFavorite(FavoriteEntity(songId))

    suspend fun removeFavorite(songId: String) = dao.removeFavorite(FavoriteEntity(songId))
}
