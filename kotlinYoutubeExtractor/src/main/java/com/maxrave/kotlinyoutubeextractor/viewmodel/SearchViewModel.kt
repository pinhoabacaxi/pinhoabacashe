package com.maxrave.kotlinyoutubeextractor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.kotlinyoutubeextractor.SearchState 
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    // Mantemos apenas o YTSearch que pertence a este módulo
    private val ytSearch = YTSearch(context)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    /**
     * Busca padrão de VÍDEOS
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
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão")
            }
        }
    }

    /**
     * Busca de PLAYLISTS
     * Movida a lógica para usar o ytSearch se ele suportar, 
     * ou processar o resultado na Activity para evitar o import circular.
     */
    fun performPlaylistSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                 Log.d("SearchVM", "Playlists encontradas: ${playlists.size}").
                val results = ytSearch.search(query) 
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca de playlists: ${e.message}")
                _searchState.value = SearchState.Error("Não foi possível encontrar playlists.")
            }
        }
    }
}
