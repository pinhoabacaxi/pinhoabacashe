package com.maxrave.kotlinyoutubeextractor

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YTSearch(private val context: Context) {
    private val LOG_TAG = "YTSearch"
    private val CLIENT_NAME = "ANDROID_MUSIC"
    private val CLIENT_VERSION = "6.45.52"

    /**
     * Realiza a busca no YouTube via InnerTube API.
     */
    suspend fun search(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val searchResults = mutableListOf<VideoMeta>()
        
        // 1. Verificação de Conexão (Movida para dentro da função)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting) {
            Log.e(LOG_TAG, "Dispositivo sem conexão de rede.")
            return@withContext searchResults // Retorna lista vazia se estiver offline
        }

        try {
            Log.d(LOG_TAG, "Iniciando busca para: $query")
            
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            // 2. Montagem do corpo da requisição JSON
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

            // LOG PARA VER O JSON COMPLETO (Se for muito grande, ele corta, mas ajuda)
            Log.d(LOG_TAG, "Resposta recebida (JSON): ${response.take(500)}...")

            val contents = jsonResponse.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (contents == null) {
                Log.e(LOG_TAG, "ERRO: Não foi possível encontrar a lista de vídeos no JSON. O YouTube pode ter mudado a estrutura.")
            } else {
                Log.d(LOG_TAG, "Vídeos encontrados no JSON: ${contents.length()}")
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
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
                        // ... (resto do código de extração igual ao anterior)
                        Log.d(LOG_TAG, "Vídeo mapeado: $title")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro na busca InnerTube: ${e.message}")
        }
        
        return@withContext searchResults
    }
}

                    
               
