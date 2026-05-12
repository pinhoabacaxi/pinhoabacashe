package com.maxrave.exampleApp.repository

import android.content.Context
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeRepository(private val context: Context) {

    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true) [cite: 1]
    
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun downloadMusic(videoId: String): Unit = withContext(Dispatchers.IO) {
        val youtubeUrl = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId" [cite: 1]
        try {
            extractor.extract(youtubeUrl) [cite: 1]
            val ytFiles = extractor.getYTFiles() [cite: 2]
            
            // Verificação de segurança para evitar o erro de streamingData
            if (ytFiles == null || ytFiles.isEmpty()) {
                Log.e("YouTubeRepo", "Falha ao obter streamingData do YouTube")
                return@withContext
            }

            val meta = extractor.getVideoMeta() [cite: 1]
            val bestAudio = ytFiles.getAudioOnly().firstOrNull() [cite: 2]

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context) [cite: 2]
                downloader.startDownload(
                    title = meta.title ?: "Som", [cite: 3]
                    artist = meta.author ?: "Desconhecido", [cite: 3]
                    url = bestAudio.url!!
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro no download: ${e.message}") [cite: 4]
        }
    }

    suspend fun extractMusicInfo(input: String): OnlineSong? = withContext(Dispatchers.IO) { [cite: 5]
        val url = if (input.contains("http")) input else "https://www.youtube.com/watch?v=$input" [cite: 5]
        try {
            extractor.extract(url) [cite: 5]
            val ytFiles = extractor.getYTFiles() [cite: 5]
            
            // Previne o erro JSONException: No value for streamingData
            if (ytFiles == null || ytFiles.isEmpty()) {
                Log.e("YouTubeRepo", "streamingData não disponível para este vídeo")
                return@withContext null
            }

            val meta = extractor.getVideoMeta() [cite: 5]
            val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url [cite: 6]

            if (meta != null) {
                OnlineSong(
                    videoId = if (input.contains("http")) extractVideoId(input) else input, [cite: 6]
                    title = meta.title ?: "Sem título", [cite: 7]
                    author = meta.author ?: "Artista", [cite: 7]
                    thumbnailUrl = meta.maxResImageUrl, [cite: 7]
                    streamUrl = bestAudio [cite: 7]
                )
            } else null
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro na extração: ${e.message}") [cite: 8]
            null
        }
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val info = extractMusicInfo(trimmedQuery)
            if (info != null) results.add(info)
            return@withContext results
        }

        val apiInstances = arrayOf("https://pipedapi.kavin.rocks", "https://api.piped.victr.me") [cite: 10]
        for (baseUrl in apiInstances) {
            try {
                val encoded = URLEncoder.encode(trimmedQuery, "UTF-8") [cite: 11]
                val url = URL("$baseUrl/search?q=$encoded&filter=music_songs") [cite: 11]
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null && item.optString("type") == "stream") {
                            val id = item.optString("url").substringAfter("v=", "") [cite: 14]
                            results.add(OnlineSong(id, item.optString("title"), item.optString("uploaderName"), item.optString("thumbnail"), null)) [cite: 15, 16]
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

        results [cite: 19]
    }

    private suspend fun searchViaYoutubeOfficial(query: String, apiKey: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OnlineSong>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=$encoded&type=video&videoCategoryId=10&maxResults=15&key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
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
            if (url.contains("youtu.be/")) url.substringAfter("youtu.be/").substringBefore("?").split("/").last() [cite: 20]
            else if (url.contains("v=")) url.substringAfter("v=").substringBefore("&") [cite: 20]
            else url [cite: 20]
        } catch (e: Exception) { url }
    }
}
