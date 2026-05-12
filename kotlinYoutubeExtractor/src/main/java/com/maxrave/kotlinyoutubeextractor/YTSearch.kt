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

// Singleton para manter a sessão ativa entre busca e extração
object YouTubeSession {
    var visitorData: String? = null
    var cookies: String? = null
}

class YTSearch(private val context: Context) {
    private val LOG_TAG = "YTSearch"
    
    private val CLIENT_NAME = "WEB"
    private val CLIENT_VERSION = "2.20231017.00.00"

    suspend fun search(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val searchResults = mutableListOf<VideoMeta>()
        
        // Verificação de Conectividade
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting) {
            Log.e(LOG_TAG, "Dispositivo sem conexão de rede.")
            return@withContext searchResults
        }

        try {
            Log.d(LOG_TAG, "Iniciando busca para: $query")
            
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // BYPASS 4 & 5: TLS, Host e Origin para Consistência de Contexto
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            conn.setRequestProperty("Host", "youtubei.googleapis.com")
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            
            // BYPASS 2: Enviar cookies se já existirem
            YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }

            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                        // Envia VisitorData se disponível
                        YouTubeSession.visitorData?.let { put("visitorData", it) }
                    })
                })
                put("query", query)
                put("params", "EgWQAQ%3D%3D") 
            }

            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            // BYPASS 2: Capturar novos Cookies
            val cookieHeader = conn.headerFields["Set-Cookie"]
            if (cookieHeader != null) {
                YouTubeSession.cookies = cookieHeader.joinToString("; ")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            // Salva VisitorData para uso posterior no Repository
            val responseContext = jsonResponse.optJSONObject("responseContext")
            val vData = responseContext?.optString("visitorData")
            if (!vData.isNullOrEmpty()) {
                YouTubeSession.visitorData = vData
                Log.d(LOG_TAG, "Bypass 2: VisitorData capturado: $vData")
            }

            // Lógica de Mapeamento do JSON (Restaurada)
            val contentsObj = jsonResponse.optJSONObject("contents")
            
            var itemsArray: JSONArray? = contentsObj
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (itemsArray == null) {
                itemsArray = contentsObj
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
            }

            if (itemsArray != null) {
                Log.d(LOG_TAG, "Resultados encontrados: ${itemsArray.length()}")
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
            } else {
                Log.e(LOG_TAG, "Caminho de 'contents' não mapeado no JSON.")
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro crítico na busca: ${e.message}")
            e.printStackTrace()
        }
        
        return@withContext searchResults
    }
}
