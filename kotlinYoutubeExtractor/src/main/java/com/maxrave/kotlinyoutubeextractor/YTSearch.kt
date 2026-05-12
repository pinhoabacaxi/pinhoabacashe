package com.maxrave.kotlinyoutubeextractor

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class YTSearch {
    private val LOG_TAG = "YTSearch"
    private val CLIENT_NAME = "ANDROID_MUSIC"
    private val CLIENT_VERSION = "6.45.52"

    /**
     * Realiza a busca no YouTube via InnerTube API.
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

            // Montagem do corpo da requisição JSON
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

            // Navegação segura no JSON da InnerTube
            val contents = jsonResponse.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
                    
                    // Suporta tanto vídeos normais quanto resultados do YouTube Music
                    val videoRenderer = item?.optJSONObject("videoRenderer") 
                        ?: item?.optJSONObject("musicVideoRenderer")
                    
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.getString("videoId")
                        
                        // Extração do Título
                        val title = videoRenderer.optJSONObject("title")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Sem título"
                            
                        // Extração do Autor/Canal
                        val author = videoRenderer.optJSONObject("longBylineText")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
                            ?: videoRenderer.optJSONObject("shortBylineText")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Desconhecido"
                        
                        // Extração da Thumbnail de melhor resolução
                        val thumbnailArray = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbnailArray != null && thumbnailArray.length() > 0) {
                            thumbnailArray.optJSONObject(thumbnailArray.length() - 1)?.optString("url") ?: ""
                        } else ""

                        searchResults.add(VideoMeta(
                            videoId = videoId,
                            title = title,
                            author = author,
                            channelId = "",
                            duration = 0L,
                            viewCount = 0L,
                            isLiveStream = false,
                            description = "",
                            thumbnailUrl = thumbUrl
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro na busca InnerTube: ${e.message}")
        }
        
        // Retorno obrigatório da lista (vazia ou preenchida)
        return@withContext searchResults
    }
}
