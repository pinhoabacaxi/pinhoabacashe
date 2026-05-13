package com.maxrave.kotlinyoutubeextractor.viewmodel

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
    
    private val context = application.applicationContext
    // Usamos o repositório que atualizamos no passo anterior
    private val repository = YouTubeRepository(context)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    /**
     * Realiza a busca UNIFICADA de vídeos e playlists em paralelo
     */
    fun performSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                // Dispara as duas buscas ao mesmo tempo para ganhar velocidade
                val videosDeferred = async { repository.searchVideos(query) }
                val playlistsDeferred = async { repository.searchPlaylists(query) }

                val videos = videosDeferred.await()
                val playlists = playlistsDeferred.await()

                // Combina os resultados em uma única lista
                // Colocamos as Playlists primeiro para dar destaque, seguidas pelos vídeos
                val combinedResults = mutableListOf<Any>()
                combinedResults.addAll(playlists)
                combinedResults.addAll(videos)

                _searchState.value = SearchState.Success(combinedResults)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca unificada: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão")
            }
        }
    }

    /**
     * Nova função: Carrega os vídeos de dentro de uma playlist selecionada
     */
    fun loadPlaylistVideos(playlistId: String) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val videos = repository.getPlaylistVideos(playlistId)
                // Atualiza o estado com a lista de vídeos da playlist
                _searchState.value = SearchState.Success(videos)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro ao abrir playlist: ${e.message}")
                _searchState.value = SearchState.Error("Não foi possível carregar os vídeos desta playlist.")
            }
        }
    }

    /**
     * Função para resetar o estado se necessário (ex: ao fechar a visualização de uma playlist)
     */
    fun resetSearch() {
        _searchState.value = SearchState.Idle
    }
}
