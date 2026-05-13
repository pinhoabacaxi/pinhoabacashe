package com.maxrave.kotlinyoutubeextractor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.exampleApp.repository.YouTubeRepository
import com.maxrave.exampleApp.repository.YouTubePlaylist
import com.maxrave.kotlinyoutubeextractor.SearchState 
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    // Agora utilizamos o YouTubeRepository para as funções avançadas (Playlists)
    private val youtubeRepository = YouTubeRepository(context)
    
    // Mantemos o YTSearch para buscas rápidas de vídeos se preferir, 
    // ou podemos usar apenas o Repository.
    private val ytSearch = YTSearch(context)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    /**
     * Busca padrão de VÍDEOS (Existente)
     */
    fun performSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            Log.d("SearchVM", "Solicitando busca de vídeos para: $query")
            _searchState.value = SearchState.Loading
            
            try {
                val results = ytSearch.search(query)
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão com o YouTube")
            }
        }
    }

    /**
     * NOVA FUNCIONALIDADE: Busca de PLAYLISTS
     * Esta função utiliza a lógica de filtragem que adicionamos ao Repository.
     */
    fun performPlaylistSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            Log.d("SearchVM", "Solicitando busca de Playlists para: $query")
            _searchState.value = SearchState.Loading
            
            try {
                val playlists = youtubeRepository.searchPlaylists(query)
                // Se o seu SearchState.Success aceitar uma lista genérica, usamos ele.
                // Caso contrário, você pode precisar adicionar 'is SearchState.PlaylistSuccess' no seu sealed class.
                _searchState.value = SearchState.Success(playlists) 
                
                Log.d("SearchVM", "Playlists encontradas: ${playlists.size}")
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca de playlists: ${e.message}")
                _searchState.value = SearchState.Error("Não foi possível encontrar playlists.")
            }
        }
    }

    /**
     * Função auxiliar para obter os vídeos de uma playlist antes do download
     */
    fun getVideosFromPlaylist(playlistId: String, onResult: (List<Any>) -> Unit) {
        viewModelScope.launch {
            try {
                val songs = youtubeRepository.getPlaylistVideos(playlistId)
                onResult(songs)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro ao carregar vídeos da playlist: ${e.message}")
            }
        }
    }
}
