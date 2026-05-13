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
    private val ytSearch = YTSearch(context)
    
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    fun performSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                // A biblioteca faz a busca padrão de vídeos
                val results = ytSearch.search(query)
                _searchState.value = SearchState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Erro na busca: ${e.message}")
                _searchState.value = SearchState.Error(e.localizedMessage ?: "Erro na conexão")
            }
        }
    }
}
