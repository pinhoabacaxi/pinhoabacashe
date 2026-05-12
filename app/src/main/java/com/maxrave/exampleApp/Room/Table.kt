package com.maxrave.exampleApp.Room

import androidx.room.*

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String, 
    val title: String,
    val artist: String,
    val sourcePath: String,    
    val thumbnailUrl: String?, 
    val isOnline: Boolean
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: String
)
