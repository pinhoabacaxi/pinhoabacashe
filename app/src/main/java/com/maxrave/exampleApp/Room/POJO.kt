package com.maxrave.exampleApp.Room

import androidx.room.*

data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id", // ID da Playlist
        entityColumn = "id", // ID da Música
        associateBy = Junction(
            PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)
