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
            // URL Corrigida (Sem WWW)
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            // User Agent de uma Smart TV para simplificar a resposta
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            conn.doOutput = true
    
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        // TVHTML5 é um dos clientes mais estáveis para extração direta
                        put("clientName", "TVHTML5") 
                        put("clientVersion", "7.20230405.08.01")
                        put("hl", "pt-BR")
                        put("gl", "BR")
                    })
                })
                // Adicionamos o playbackContext para evitar erro de "bot detectado"
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 19500)
                    })
                })
            }
    
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Erro na API: Código $responseCode")
                return null
            }
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails")
    
            if (streamingData != null) {
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        // Buscamos o primeiro formato de áudio disponível
                        if (format.optString("mimeType").contains("audio")) {
                            val url = format.optString("url")
                            if (url.isNotEmpty()) {
                                Log.d(TAG, "StreamingData obtido com sucesso!")
                                return OnlineSong(
                                    videoId = videoId,
                                    title = videoDetails?.optString("title") ?: "Música Online",
                                    author = videoDetails?.optString("author") ?: "YouTube",
                                    streamUrl = url,
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                )
                            }
                        }
                    }
                }
            } else {
                val status = json.optJSONObject("playabilityStatus")?.optString("status")
                Log.e(TAG, "streamingData nulo. Status do YouTube: $status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico no Repository: ${e.message}")
        }
        return null
    }
    
