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
        
        // 1. Verificação de Conexão
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting) {
            Log.e(LOG_TAG, "Dispositivo sem conexão de rede.")
            return@withContext searchResults
        }

        try {
            Log.d(LOG_TAG, "Iniciando busca para: $query")
            
            // Endpoint sem 'www.' para evitar problemas de DNS em certas redes
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            // 2. Montagem do corpo da requisição JSON (Contexto InnerTube)
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

            // DEBUG: Ver se o JSON básico chegou
            Log.d(LOG_TAG, "Resposta JSON recebida. Tamanho: ${response.length}")

            // 3. Navegação profunda no JSON da InnerTube
            val contents = jsonResponse.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (contents == null) {
                Log.e(LOG_TAG, "Caminho 'contents' não encontrado no JSON. Estrutura pode ter mudado.")
            } else {
                Log.d(LOG_TAG, "Itens encontrados no JSON: ${contents.length()}")
                
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
                    
                    // Tenta encontrar o renderer de vídeo ou música
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
                        
                        // Extração da Thumbnail
                        val thumbnailArray = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbnailArray != null && thumbnailArray.length() > 0) {
                            thumbnailArray.optJSONObject(thumbnailArray.length() - 1)?.optString("url") ?: ""
                        } else ""

                        Log.d(LOG_TAG, "Mapeado: $title [ID: $videoId]")

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
            Log.e(LOG_TAG, "Erro crítico na busca: ${e.message}")
            e.printStackTrace()
        }
        
        Log.d(LOG_TAG, "Total de resultados retornados: ${searchResults.size}")
        return@withContext searchResults
    }
}
