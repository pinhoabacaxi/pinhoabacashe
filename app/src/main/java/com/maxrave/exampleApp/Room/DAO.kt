package com.maxrave.exampleApp.Room

import androidx.room.*

@Dao
interface MusicDao {
    // --- PLAYLISTS ---
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<Playlist>

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // --- MÚSICAS E RELAÇÕES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getSongsFromPlaylist(playlistId: Long): List<PlaylistWithSongs>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    // --- FILTROS ---
    @Query("SELECT DISTINCT artist FROM songs") 
    suspend fun getUniqueArtists(): List<String>
    
    // Nota: Para filtrar por álbum, a SongEntity precisaria do campo album. 
    // Por enquanto, usaremos a ordenação na lista da MainActivity.
  
    // --- FAVORITOS ---
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)")
    suspend fun isFavorite(id: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    
    @Delete
    suspend fun removeFavorite(favorite: FavoriteEntity)
}
