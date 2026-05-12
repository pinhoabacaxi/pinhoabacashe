package com.maxrave.exampleApp.Room

import androidx.room.*
import android.content.Context

@Dao
interface MusicDao {
    // Playlists
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<Playlist>

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Músicas e Relações
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getSongsFromPlaylist(playlistId: Long): List<PlaylistWithSongs>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("SELECT DISTINCT artist FROM songs") 
    suspend fun getUniqueArtists(): List<String>
    
    @Query("SELECT DISTINCT album FROM songs") 
    suspend fun getUniqueAlbums(): List<String>
  
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)")
    suspend fun isFavorite(id: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    
    @Delete
    suspend fun removeFavorite(favorite: FavoriteEntity)
}
