package com.maxrave.kotlinyoutubeextractor

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YTSearch {
    private val LOG_TAG = "YTSearch"
    private val CLIENT_NAME = "ANDROID_MUSIC"
    private val CLIENT_VERSION = "6.45.52"

    // Mantenha como suspend function. Ela será chamada pelo ViewModel.
    suspend fun search(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val searchResults = mutableListOf<VideoMeta>()
        try {
            val apiUrl = "https://www.youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

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

            // CORREÇÃO: Usando a variável jsonResponse para extrair os vídeos
            // O caminho no JSON da InnerTube é longo: contents -> sectionListRenderer -> ... -> contents
            val contents = jsonResponse.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
                    // Procuramos pelo vídeo ou música
                    val videoRenderer = item?.optJSONObject("videoRenderer") 
                        ?: item?.optJSONObject("musicVideoRenderer")
                    
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.getString("videoId")
                        val title = videoRenderer.getJSONObject("title")
                            .getJSONArray("runs").getJSONObject(0).getString("text")
                        val author = videoRenderer.optJSONObject("longBylineText")
                            ?.getJSONArray("runs")?.getJSONObject(0)?.getString("text") ?: "Desconhecido"
                        
                        // Extração da melhor Thumbnail
                        val thumbnailArray = videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails")
                        val thumbUrl = thumbnailArray.getJSONObject(thumbnailArray.length() - 1).getString("url")

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
        }
    }
}
