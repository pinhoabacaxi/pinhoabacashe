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

// Singleton simples para manter a sessão viva entre busca e extração
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
        
        // ... (verificação de conectividade mantida) ...

        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // BYPASS 4: TLS/User-Agent Consistente
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            
            // BYPASS 2: Enviar cookies se já tivermos (para parecer um retorno de usuário)
            YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }

            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                        // BYPASS 5: Se já tivermos visitorData, enviamos para manter consistência
                        YouTubeSession.visitorData?.let { put("visitorData", it) }
                    })
                })
                put("query", query)
                put("params", "EgWQAQ%3D%3D") 
            }

            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            // BYPASS 2: Capturar novos Cookies e VisitorData da resposta
            val cookieHeader = conn.headerFields["Set-Cookie"]
            if (cookieHeader != null) {
                YouTubeSession.cookies = cookieHeader.joinToString("; ")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            // SALVANDO O VISITOR DATA PARA O REPOSITORY USAR NO BYPASS DO PLAYER
            val responseContext = jsonResponse.optJSONObject("responseContext")
            val vData = responseContext?.optString("visitorData")
            if (!vData.isNullOrEmpty()) {
                YouTubeSession.visitorData = vData
                Log.d(LOG_TAG, "Bypass 2: VisitorData capturado: $vData")
            }

            // ... (resto da lógica de mapeamento de vídeo mantida igual) ...

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro crítico na busca: ${e.message}")
        }
        
        return@withContext searchResults
    }
}
