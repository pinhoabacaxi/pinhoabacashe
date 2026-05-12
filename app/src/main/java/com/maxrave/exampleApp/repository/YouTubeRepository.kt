package com.maxrave.exampleApp.repository

import android.content.Context
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import com.maxrave.kotlinyoutubeextractor.bestQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeRepository(private val context: Context) {

    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)
    
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun downloadMusic(videoId: String): Unit = withContext(Dispatchers.IO) {
        val youtubeUrl = if (videoId.startsWith("http")) videoId else "https://youtube.com/watch?v=$videoId"
        try {
            extractor.extract(youtubeUrl)
            val ytFiles = extractor.ytFiles
            
            // CORREÇÃO: Verificando o tamanho com size() em vez de isEmpty()
            if (ytFiles == null || ytFiles.size() <= 0) {
                Log.e("YouTubeRepo", "Falha ao obter streamingData do YouTube")
                return@withContext
            }

            val meta = extractor.videoMeta
            val bestAudio = ytFiles.getAudioOnly().firstOrNull()

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context)
                downloader.startDownload(
                    title = meta.title ?: "Som",
                    artist = meta.author ?: "Desconhecido",
                    url = bestAudio.url!!
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro no download: ${e.message}")
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        try {
            // 1. URL SEM WWW
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyAO_SshR2Y7l9SceM8m3Tlp5S3pE"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:115.0) Gecko/115.0 Firefox/115.0")
            conn.doOutput = true
    
            // 2. Payload específico para liberar streamingData (ANDROID_VR é o mais aberto)
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.50.41")
                        put("hl", "pt-BR")
                    })
                })
                // Campo crucial para habilitar links de áudio
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 19500) // Timestamp genérico funcional
                    })
                })
            }
    
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
    
            // 3. Pegar os dados de streaming
            val streamingData = json.optJSONObject("streamingData")
            if (streamingData != null) {
                val formats = streamingData.optJSONArray("adaptiveFormats")
                // Procurar o formato que seja apenas áudio (audio/mp4 ou audio/webm)
                for (i in 0 until (formats?.length() ?: 0)) {
                    val format = formats!!.getJSONObject(i)
                    if (format.getString("mimeType").contains("audio")) {
                        val audioUrl = format.getString("url")
                        
                        // Retorna o objeto com o link real
                        return@withContext OnlineSong(
                            videoId = videoId,
                            title = json.getJSONObject("videoDetails").getString("title"),
                            author = json.getJSONObject("videoDetails").getString("author"),
                            streamUrl = audioUrl,
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        )
                    }
                }
            } else {
                Log.e("YouTubeRepo", "streamingData ainda veio nulo. Verifique o payload.")
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro na extração: ${e.message}")
        }
        return@withContext null
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val info = extractMusicInfo(trimmedQuery)
            if (info != null) results.add(info)
            return@withContext results
        }

        val apiInstances = arrayOf("https://pipedapi.kavin.rocks", "https://api.piped.victr.me")
        for (baseUrl in apiInstances) {
            try {
                val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")
                val url = URL("$baseUrl/search?q=$encoded&filter=music_songs")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null && item.optString("type") == "stream") {
                            val id = item.optString("url").substringAfter("v=", "")
                            results.add(OnlineSong(id, item.optString("title"), item.optString("uploaderName"), item.optString("thumbnail"), null))
                        }
                    }
                    if (results.isNotEmpty()) return@withContext results
                }
            } catch (e: Exception) { }
        }

        if (results.isEmpty()) {
            results.addAll(searchViaYoutubeOfficial(trimmedQuery, youtubeApiKey1))
        }

        if (results.isEmpty() && youtubeApiKey2.length > 10) {
            results.addAll(searchViaYoutubeOfficial(trimmedQuery, youtubeApiKey2))
        }

        results
    }

    private suspend fun searchViaYoutubeOfficial(query: String, apiKey: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OnlineSong>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://googleapis.com/youtube/v3/search?part=snippet&q=$encoded&type=video&videoCategoryId=10&maxResults=15&key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val vId = item.getJSONObject("id").getString("videoId")
                    val snippet = item.getJSONObject("snippet")
                    val thumbUrl = snippet.getJSONObject("thumbnails").getJSONObject("high").getString("url")
                    list.add(OnlineSong(vId, snippet.getString("title"), snippet.getString("channelTitle"), thumbUrl, null))
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro Google API: ${e.message}")
        }
        list
    }

    private fun extractVideoId(url: String): String {
        return try {
            if (url.contains("youtu.be/")) url.substringAfter("youtu.be/").substringBefore("?").split("/").last()
            else if (url.contains("v=")) url.substringAfter("v=").substringBefore("&")
            else url
        } catch (e: Exception) { url }
    }
}
