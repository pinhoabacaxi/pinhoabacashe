package com.maxrave.exampleApp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.kotlinyoutubeextractor.SearchState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = YouTubeRepository(application.applicationContext)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    /**
     * Realiza a busca unificada de vídeos e playlists em paralelo.
     */
    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                // DISPARO EM PARALELO: Iniciamos as duas buscas ao mesmo tempo
                val videosDeferred = async { repository.searchVideos(query) }
                val playlistsDeferred = async { repository.searchPlaylists(query) }

                // AGUARDAR RESULTADOS: Esperamos ambas terminarem
                val videos = videosDeferred.await()
                val playlists = playlistsDeferred.await()

                // UNIFICAÇÃO: Criamos uma lista mista
                val combinedResults = mutableListOf<Any>()
                combinedResults.addAll(playlists) // Adicionamos playlists primeiro (opcional)
                combinedResults.addAll(videos)

                _searchState.value = SearchState.Success(combinedResults)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca unificada: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão")
            }
        }
    }

    /**
     * Carrega os vídeos de dentro de uma playlist selecionada.
     */
    fun loadPlaylistVideos(playlistId: String) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val videos = repository.getPlaylistVideos(playlistId)
                _searchState.value = SearchState.Success(videos)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro ao carregar playlist: ${e.message}")
                _searchState.value = SearchState.Error("Erro ao carregar vídeos da playlist.")
            }
        }
    }

    fun resetSearch() {
        _searchState.value = SearchState.Idle
    }
}
