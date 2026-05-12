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
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            
            // BYPASS 4: TLS/UA - Usamos um UA Mobile real baseado no dispositivo
            val userAgent = "com.google.android.youtube/${Build.VERSION.RELEASE} (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID})"
            conn.setRequestProperty("User-Agent", userAgent)
            
            // BYPASS 2 & 5: Persistência de Cookies da busca
            YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }
            
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        // BYPASS 1: Usamos ANDROID_TESTSUITE para evitar o Signature Cipher (URL limpa)
                        put("clientName", "ANDROID_TESTSUITE")
                        put("clientVersion", "1.9.3")
                        
                        // BYPASS 3: Dados dinâmicos para passar na Integridade em qualquer celular
                        put("osName", "Android")
                        put("osVersion", Build.VERSION.RELEASE)
                        put("androidSdkVersion", Build.VERSION.SDK_INT)
                        put("platform", "MOBILE")
                        
                        put("hl", "pt-BR")
                        put("gl", "BR")
                        
                        // BYPASS 5: O VisitorData colhido no YTSearch diz ao YT que somos o mesmo humano
                        YouTubeSession.visitorData?.let { put("visitorData", it) }
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        // Timestamp fixo que o TESTSUITE aceita sem validar PoW complexo
                        put("signatureTimestamp", 20000)
                    })
                })
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            if (conn.responseCode != 200) {
                Log.e(TAG, "Erro na API: ${conn.responseCode}")
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Verificação de Status (Bypass 1 & 3)
            val playability = json.optJSONObject("playabilityStatus")
            if (playability?.optString("status") != "OK") {
                Log.e(TAG, "Bloqueio detectado: ${playability?.optString("reason")}")
                return null
            }

            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails")

            if (streamingData != null) {
                // Adaptive Formats costumam ter o áudio de melhor qualidade (M4A/Opus)
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        val mimeType = format.optString("mimeType")
                        
                        if (mimeType.contains("audio")) {
                            // O ANDROID_TESTSUITE retorna "url" direta, sem precisar de Cipher Decryptor
                            val url = format.optString("url")
                            if (url.isNotEmpty()) {
                                return OnlineSong(
                                    videoId = videoId,
                                    title = videoDetails?.optString("title") ?: "Música",
                                    author = videoDetails?.optString("author") ?: "YouTube",
                                    streamUrl = url,
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no Bypass do Repository: ${e.message}")
        }
        return null
    }
}
