package com.maxrave.exampleApp.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YouTubeSession // Importando a sessão da busca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val song = fetchFromInnerTube(videoId)
        if (song != null && song.streamUrl != null) {
            Log.d(TAG, "Bypass 6: Link de download validado.")
            // Lógica de download aqui
        }
    }

    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            // MUDANÇA 1: Usamos a URL base sem parâmetros de consulta fixos para evitar o 404
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // MUDANÇA 2: User-Agent mais moderno e condizente com um app Android real
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL})")
            
            // BYPASS 2 & 5: Cookies e VisitorData da sessão (Essencial!)
            YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }
            
            conn.doOutput = true
    
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        // MUDANÇA 3: ANDROID_VR é o cliente que melhor entrega streamingData hoje
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.50.41")
                        put("osName", "Android")
                        put("osVersion", Build.VERSION.RELEASE)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                        // Envia o visitorData capturado na busca para evitar o 404/403
                        YouTubeSession.visitorData?.let { put("visitorData", it) }
                    })
                })
                // MUDANÇA 4: Adição de parâmetros de reprodução para validar a requisição
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 20000)
                    })
                })
            }
    
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                // Se der 404 ou 400, o log vai nos dizer exatamente o que o servidor respondeu
                val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Erro na API: $responseCode - Detalhes: $errorResponse")
                return null
            }
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Verificação de Status de Reprodução
            val playabilityStatus = json.optJSONObject("playabilityStatus")
            if (playabilityStatus?.optString("status") != "OK") {
                Log.e(TAG, "Vídeo não reproduzível: ${playabilityStatus?.optString("reason")}")
                return null
            }
    
            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails")
    
            if (streamingData != null) {
                // Buscamos nos formatos adaptativos (geralmente áudio puro de alta qualidade)
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        if (format.optString("mimeType").contains("audio")) {
                            val streamUrl = format.optString("url")
                            if (streamUrl.isNotEmpty()) {
                                Log.d(TAG, "Sucesso: streamingData extraído para $videoId")
                                return OnlineSong(
                                    videoId = videoId,
                                    title = videoDetails?.optString("title") ?: "Música Online",
                                    author = videoDetails?.optString("author") ?: "YouTube",
                                    streamUrl = streamUrl,
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica no fetch: ${e.message}")
        }
        return null
    }
