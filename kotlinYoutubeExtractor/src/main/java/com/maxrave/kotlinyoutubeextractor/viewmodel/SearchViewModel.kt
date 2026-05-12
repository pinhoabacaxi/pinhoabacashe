package com.maxrave.kotlinyoutubeextractor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Agora sim, aqui ele funciona
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val ytSearch = YTSearch()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    fun performSearch(query: String) {
        // O viewModelScope gerencia a Coroutine e chama a suspend function search()
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val results = ytSearch.search(query) // Chamada segura
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
}
