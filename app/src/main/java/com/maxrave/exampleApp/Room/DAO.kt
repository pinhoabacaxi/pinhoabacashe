package com.maxrave.exampleApp.Room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // --- PLAYLISTS ---
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>> // Reativo

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
    fun getSongsFromPlaylistFlow(playlistId: Long): Flow<List<PlaylistWithSongs>> // Reativo

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    // --- FAVORITOS ---
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)")
    suspend fun isFavorite(id: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    
    @Delete
    suspend fun removeFavorite(favorite: FavoriteEntity)

    @Query("SELECT DISTINCT artist FROM songs") 
    suspend fun getUniqueArtists(): List<String>
}
