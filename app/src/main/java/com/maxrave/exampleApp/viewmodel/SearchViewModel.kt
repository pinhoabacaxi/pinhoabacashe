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

    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                // Inicia as buscas em paralelo para otimizar o tempo
                val videosDeferred = async { repository.searchVideos(query) }
                val playlistsDeferred = async { repository.searchPlaylists(query) }

                val videos = videosDeferred.await()
                val playlists = playlistsDeferred.await()

                val combinedResults = mutableListOf<Any>()
                combinedResults.addAll(playlists)
                combinedResults.addAll(videos)

                _searchState.value = SearchState.Success(combinedResults)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão")
            }
        }
    }

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
