package com.maxrave.exampleApp.repository

import android.content.Context
import android.provider.MediaStore
import com.maxrave.exampleApp.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicLoader(private val context: Context) {

    suspend fun loadLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durationCol)
                    val songPath = cursor.getString(dataCol)

                    // Só adiciona se a música tiver mais de 5 segundos e o caminho não for nulo
                    if (duration > 5000 && !songPath.isNullOrEmpty()) {
                        songList.add(
                            Song(
                                id = cursor.getLong(idCol),
                                title = cursor.getString(titleCol) ?: "Desconhecido",
                                artist = cursor.getString(artistCol) ?: "Artista Desconhecido",
                                album = cursor.getString(albumCol) ?: "Álbum Desconhecido",
                                duration = duration,
                                uri = songPath,
                                albumId = cursor.getLong(albumIdCol)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songList
    }
}
