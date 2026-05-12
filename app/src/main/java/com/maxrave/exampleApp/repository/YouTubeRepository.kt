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
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    // Função que a Activity usa para o Stream
    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }

    // Função que a Activity está tentando chamar para o Download
    // Se você ainda não tem a lógica de download pronta, vamos extrair o link primeiro
    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val song = fetchFromInnerTube(videoId)
        if (song != null && song.streamUrl != null) {
            Log.d(TAG, "Link para download obtido: ${song.streamUrl}")
            // Aqui você chamaria o seu DownloadManager ou lógica de salvar arquivo
            // Por enquanto, vamos apenas logar para evitar o erro de compilação
        } else {
            Log.e(TAG, "Falha ao obter link para download")
        }
    }

    // Lógica privada unificada para evitar repetição
    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyAO_SshR2Y7l9SceM8m3Tlp5S3pE"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_TESTSUITE")
                        put("clientVersion", "1.9.3")
                        put("hl", "pt-BR")
                    })
                })
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails")

            val formats = streamingData?.optJSONArray("adaptiveFormats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.getJSONObject(i)
                    if (format.optString("mimeType").contains("audio")) {
                        return OnlineSong(
                            videoId = videoId,
                            title = videoDetails?.optString("title") ?: "Unknown",
                            author = videoDetails?.optString("author") ?: "Unknown",
                            streamUrl = format.optString("url"),
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro InnerTube: ${e.message}")
        }
        return null
    }
}
