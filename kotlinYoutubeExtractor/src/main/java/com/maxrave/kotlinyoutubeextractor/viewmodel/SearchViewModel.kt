package com.maxrave.kotlinyoutubeextractor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// IMPORTANTE: Verifique se este import aponta para onde você criou o SearchState
import com.maxrave.kotlinyoutubeextractor.SearchState 
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val ytSearch = YTSearch()

    // Usando explicitamente o tipo SearchState
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    fun performSearch(query: String) {
        viewModelScope.launch {
            // CORREÇÃO: Use .value para atribuir novos estados
            _searchState.value = SearchState.Loading
            try {
                val results = ytSearch.search(query)
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
}
