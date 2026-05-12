package com.maxrave.exampleApp.repository

import android.content.Context
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        try {
            // URL sem 'www' conforme corrigido
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyAO_SshR2Y7l9SceM8m3Tlp5S3pE"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            // User agent genérico de Android
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            // PAYLOAD ATUALIZADO 2026: Usando ANDROID_TESTSUITE para evitar bloqueios de streamingData
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_TESTSUITE")
                        put("clientVersion", "1.9.3")
                        put("hl", "pt-BR")
                        put("gl", "BR")
                    })
                })
                // O segredo está aqui: Forçar o YouTube a achar que é uma requisição interna de teste
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 20000) 
                    })
                })
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            // DEBUG para ver se o JSON trouxe os detalhes do vídeo
            val videoDetails = json.optJSONObject("videoDetails")
            if (videoDetails != null) {
                Log.d(TAG, "Detalhes do vídeo obtidos: ${videoDetails.optString("title")}")
            }

            val streamingData = json.optJSONObject("streamingData")
            if (streamingData != null) {
                // Priorizamos adaptiveFormats (onde ficam os áudios puros em alta qualidade)
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        val mimeType = format.optString("mimeType")
                        
                        // Buscamos apenas o fluxo de áudio
                        if (mimeType.contains("audio/mp4") || mimeType.contains("audio/webm")) {
                            val audioUrl = format.optString("url")
                            if (audioUrl.isNotEmpty()) {
                                Log.d(TAG, "Sucesso! Link de áudio extraído.")
                                return@withContext OnlineSong(
                                    videoId = videoId,
                                    title = videoDetails?.optString("title") ?: "Unknown",
                                    author = videoDetails?.optString("author") ?: "Unknown",
                                    streamUrl = audioUrl,
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                )
                            }
                        }
                    }
                }
            } else {
                // Se cair aqui, o YouTube mandou um "playabilityStatus" de erro
                val status = json.optJSONObject("playabilityStatus")?.optString("status")
                val reason = json.optJSONObject("playabilityStatus")?.optString("reason")
                Log.e(TAG, "YouTube negou o streamingData. Status: $status, Motivo: $reason")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal no Repository: ${e.message}")
        }
        return@withContext null
    }
}
