package com.maxrave.kotlinyoutubeextractor

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YTSearch(private val context: Context) {
    private val LOG_TAG = "YTSearch"
    
    // Mudança CRUCIAL: Usamos cliente WEB porque o JSON é mais previsível e estável
    private val CLIENT_NAME = "WEB"
    private val CLIENT_VERSION = "2.20231017.00.00"

    suspend fun search(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val searchResults = mutableListOf<VideoMeta>()
        
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting) {
            Log.e(LOG_TAG, "Dispositivo sem conexão de rede.")
            return@withContext searchResults
        }

        try {
            Log.d(LOG_TAG, "Iniciando busca para: $query")
            
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // Fingimos ser um Chrome Desktop para forçar o Google a mandar a versão WEB padrão
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            conn.doOutput = true

            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20231017.00.00")
                    })
                })
                put("query", query)
                // Adicionar isso ajuda a evitar que o YT mande apenas "shelfs" ou destaques
                put("params", "EgWQAQ%3D%3D") 
            }
            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            Log.d(LOG_TAG, "Resposta JSON recebida. Tamanho: ${response.length}")

            val contentsObj = jsonResponse.optJSONObject("contents")
            
            // Caminho 1: Tenta o padrão WEB de mesa (O mais comum)
            var itemsArray: JSONArray? = contentsObj
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            // Caminho 2 (Fallback): Tenta o padrão Mobile caso a API altere o formato
            if (itemsArray == null) {
                itemsArray = contentsObj
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
            }

            if (itemsArray == null) {
                Log.e(LOG_TAG, "ERRO: Caminho 'contents' não encontrado. A estrutura do JSON é diferente das mapeadas.")
            } else {
                Log.d(LOG_TAG, "Itens encontrados no JSON: ${itemsArray.length()}")
                
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(i)
                    
                    val videoRenderer = item?.optJSONObject("videoRenderer") 
                        ?: item?.optJSONObject("musicVideoRenderer")
                    
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.getString("videoId")
                        
                        val title = videoRenderer.optJSONObject("title")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Sem título"
                            
                        val author = videoRenderer.optJSONObject("longBylineText")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
                            ?: videoRenderer.optJSONObject("shortBylineText")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Desconhecido"
                        
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
