package com.maxrave.exampleApp.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YouTubeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    
    // Substitua pelas suas chaves reais se necessário para outras funções da API
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    /**
     * Função principal chamada pela OnlineSearchActivity para obter o link de áudio
     */
    suspend fun extractAudioLink(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val song = fetchFromInnerTube(videoId)
        if (song != null) {
            Log.d(TAG, "Bypass 6: Link de download validado para: ${song.title}")
            // A lógica de download (DownloadManager) deve ser implementada aqui
        }
    }

    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // User-Agent simulando o app oficial do YouTube para evitar bloqueios
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL})")
            
            // BYPASS: Cookies da sessão para manter a integridade da requisição
            YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }
            
            conn.doOutput = true
    
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.50.41")
                        put("osName", "Android")
                        put("osVersion", Build.VERSION.RELEASE)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                        // Envia o visitorData capturado na busca
                        YouTubeSession.visitorData?.let { put("visitorData", it) }
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 20000)
                    })
                })
            }
    
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Erro na API: $responseCode - Detalhes: $errorResponse")
                return null
            }
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Verificação de Status
            val playabilityStatus = json.optJSONObject("playabilityStatus")
            if (playabilityStatus?.optString("status") != "OK") {
                Log.e(TAG, "Vídeo não reproduzível: ${playabilityStatus?.optString("reason")}")
                return null
            }
    
            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            
            // CAPTURA DE METADADOS (Importante para o OnlineSong)
            val title = videoDetails.optString("title")
            val author = videoDetails.optString("author")
            val durationSeconds = videoDetails.optString("lengthSeconds")
            val thumbnails = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
    
            if (streamingData != null) {
                // Buscamos nos formatos adaptativos (áudio puro)
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        // Filtrar apenas por fluxos de áudio
                        if (format.optString("mimeType").contains("audio")) {
                            val audioUrl = format.optString("url")
                            if (audioUrl.isNotEmpty()) {
                                Log.d(TAG, "Sucesso: Link de áudio extraído para $videoId")
                                
                                // Retorna o objeto OnlineSong mapeado corretamente
                                return OnlineSong(
                                    videoId = videoId,
                                    title = title,
                                    author = author,
                                    thumbnailUrl = thumbUrl,
                                    url = audioUrl, // URL real do streaming
                                    duration = durationSeconds // String de segundos
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
}
