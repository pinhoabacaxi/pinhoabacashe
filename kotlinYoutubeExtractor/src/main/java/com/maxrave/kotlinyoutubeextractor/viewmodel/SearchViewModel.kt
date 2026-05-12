package com.maxrave.kotlinyoutubeextractor.viewmodel

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.kotlinyoutubeextractor.SearchState 
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    // Inicializamos o YTSearch
    private val ytSearch = YTSearch(application.applicationContext)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    /**
     * Função de busca - Agora referenciada corretamente na OnlineSearchActivity
     */
    fun performSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            Log.d("SearchVM", "Solicitando busca para: $query")
            _searchState.value = SearchState.Loading
            
            try {
                // Chama a lógica de extração/busca
                val results = ytSearch.search(query)
                Log.d("SearchVM", "Resultados obtidos: ${results.size}")
                
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão com o YouTube")
            }
        }
    }
}
