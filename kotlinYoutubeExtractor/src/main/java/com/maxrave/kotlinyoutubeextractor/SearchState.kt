package com.maxrave.kotlinyoutubeextractor

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<VideoMeta>) : SearchState()
    data class Error(val message: String) : SearchState()
}
