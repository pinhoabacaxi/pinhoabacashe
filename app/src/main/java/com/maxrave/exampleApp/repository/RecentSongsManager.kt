package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences
import com.maxrave.exampleApp.model.Song

class RecentSongsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_songs_prefs", Context.MODE_PRIVATE)
    private val MAX_RECENT_COUNT = 50 // Limite de músicas no histórico

    // Salva o ID de uma música quando ela é tocada
    fun addSongToRecent(songId: String) {
        val currentRecent = getRecentIds().toMutableList()
        
        // Remove se já existir para mover para o topo (início da lista)
        currentRecent.remove(songId)
        currentRecent.add(0, songId)

        // Mantém apenas as últimas X músicas
        if (currentRecent.size > MAX_RECENT_COUNT) {
            val trimmedList = currentRecent.take(MAX_RECENT_COUNT)
            saveIds(trimmedList)
        } else {
            saveIds(currentRecent)
        }
    }

    // Retorna a lista de IDs salvos
    private fun getRecentIds(): List<String> {
        val rawData = prefs.getString("recent_ids", "") ?: ""
        if (rawData.isEmpty()) return emptyList()
        return rawData.split(",")
    }

    private fun saveIds(ids: List<String>) {
        prefs.edit().putString("recent_ids", ids.joinToString(",")).apply()
    }

    /**
     * Esta é a função que a MainActivity está chamando.
     * Ela recebe a lista de todas as músicas e retorna apenas as recentes,
     * mantendo a ordem de reprodução (da mais nova para a mais antiga).
     */
    fun getRecentSongs(allSongs: List<Song>): List<Song> {
        val recentIds = getRecentIds()
        // Cria um mapa para busca rápida por ID
        val songsMap = allSongs.associateBy { it.id }
        
        // Mapeia os IDs para os objetos Song reais, ignorando IDs que não existem mais
        return recentIds.mapNotNull { id -> songsMap[id] }
    }
}
