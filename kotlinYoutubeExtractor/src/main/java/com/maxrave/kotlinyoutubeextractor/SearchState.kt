package com.maxrave.kotlinyoutubeextractor

// No módulo extractor, altere para aceitar Any
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<Any>) : SearchState() // Mudar de VideoMeta para Any 
    data class Error(val message: String) : SearchState()
}
