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
        
        // Mapeamento das colunas do sistema
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, // Este é o 'path' (caminho do arquivo)
            MediaStore.Audio.Media.ALBUM_ID
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            // Pegando os índices das colunas com segurança
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                // AGORA definimos as variáveis que estavam dando erro
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Desconhecido"
                val artist = cursor.getString(artistColumn) ?: "Artista Desconhecido"
                val album = cursor.getString(albumColumn) ?: "Álbum Desconhecido"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(pathColumn) ?: ""
                val albumId = cursor.getLong(albumIdColumn)

                // Criamos o objeto Song com os parâmetros na ordem EXATA do seu modelo corrigido
                songList.add(Song(id, title, artist, album, duration, path, albumId))
            }
        }
        return@withContext songList
    }
}
