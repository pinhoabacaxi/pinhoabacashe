package com.maxrave.kotlinyoutubeextractor

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.kotlinyoutubeextractor.SearchState
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import com.maxrave.kotlinyoutubeextractor.YTSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<VideoMeta>) : SearchState()
    data class Error(val message: String) : SearchState()
}
class SearchViewModel : ViewModel() {

    private val ytSearch = YTSearch()

    // Usamos StateFlow para gerir o estado da UI de forma reativa
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> get() = _searchState

    fun performSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val results = ytSearch.search(query)
                if (results.isEmpty()) {
                    _searchState.value = SearchState.Error("Nenhum resultado encontrado.")
                } else {
                    _searchState.value = SearchState.Success(results)
                }
            } catch (e: Exception) {
                _searchState.value = SearchState.Error("Erro na ligação: ${e.message}")
            }
        }
    }
}
class YTSearch {
    private val LOG_TAG = "YTSearch"
    private val CLIENT_NAME = "ANDROID_MUSIC"
    private val CLIENT_VERSION = "6.45.52"

    /**
     * Realiza a busca no YouTube e retorna uma lista de metadados.
     */
    suspend fun search(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val searchResults = mutableListOf<VideoMeta>()

        try {
            val apiUrl = "https://www.youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            // Corpo da requisição focado em busca
            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                    })
                })
                put("query", query)
            }

            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            // Navegação no JSON da InnerTube (Caminho: contents -> sectionListRenderer -> contents -> itemSectionRenderer -> contents)
            val contents = jsonResponse.getJSONObject("contents")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents")
                .getJSONObject(0)
                .getJSONObject("itemSectionRenderer")
                .getJSONArray("contents")

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i)
                val videoRenderer = item?.optJSONObject("videoRenderer") ?: item?.optJSONObject("musicVideoRenderer")
                
                if (videoRenderer != null) {
                    val videoId = videoRenderer.getString("videoId")
                    val title = videoRenderer.getJSONObject("title").getJSONArray("runs").getJSONObject(0).getString("text")
                    
                    // Autor (Canal)
                    val author = videoRenderer.optJSONObject("longBylineText")
                        ?.getJSONArray("runs")?.getJSONObject(0)?.getString("text") ?: "Desconhecido"
                    
                    // Extração da Thumbnail (Pegamos a última da lista, que costuma ser a de maior resolução)
                    val thumbnailArray = videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails")
                    val thumbUrl = thumbnailArray.getJSONObject(thumbnailArray.length() - 1).getString("url")
                    
                    val durationText = videoRenderer.optJSONObject("lengthText")
                        ?.getJSONArray("runs")?.getJSONObject(0)?.getString("text") ?: "0:00"

                    searchResults.add(VideoMeta(
                        videoId = videoId,
                        title = title,
                        author = author,
                        channelId = "",
                        duration = 0L, 
                        viewCount = 0L,
                        isLiveStream = false,
                        description = durationText,
                        thumbnailUrl = thumbUrl // Atribuindo a URL da imagem
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro na busca InnerTube: ${e.message}")
        }

        return@withContext searchResults
    }
}
