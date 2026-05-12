package com.maxrave.kotlinyoutubeextractor.viewmodel

import android.app.Application // ESTA LINHA É ESSENCIAL
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.kotlinyoutubeextractor.SearchState 
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    // Inicializamos o YTSearch passando o contexto da aplicação para evitar memory leaks
    private val ytSearch = YTSearch(application.applicationContext)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    fun performSearch(query: String) {
        viewModelScope.launch {
            Log.d("SearchVM", "Solicitando busca para: $query")
            _searchState.value = SearchState.Loading
            try {
                val results = ytSearch.search(query)
                Log.d("SearchVM", "Resultados obtidos: ${results.size}")
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro no ViewModel: ${e.message}")
                _searchState.value = SearchState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
}
