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

    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)
    
    // Suas chaves de API
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun downloadMusic(videoId: String): Unit = withContext(Dispatchers.IO) {
        // Se for um link completo, usa ele, senão monta a URL
        val youtubeUrl = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId"
        try {
            extractor.extract(youtubeUrl)
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()
            val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull()

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context)
                downloader.startDownload(
                    title = meta.title ?: "Som",
                    artist = meta.author ?: "Desconhecido",
                    url = bestAudio.url!!
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro download: ${e.message}")
        }
    }

    suspend fun extractMusicInfo(input: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = if (input.contains("http")) input else "https://www.youtube.com/watch?v=$input"
        try {
            extractor.extract(url)
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()
        
        // Verificação de segurança: Se ytFiles for nulo, a extração falhou internamente
            if (ytFiles == null || ytFiles.isEmpty()) {
                Log.e("YouTubeRepo", "streamingData não encontrado ou vazio")
                return@withContext null
            }

            val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url

            if (meta != null) {
                OnlineSong(
                    videoId = if (input.contains("http")) extractVideoId(input) else input,
                    title = meta.title ?: "Sem título",
                    author = meta.author ?: "Artista",
                    thumbnailUrl = meta.maxResImageUrl,
                    streamUrl = bestAudio
                )
            } else null
        } catch (e: Exception) {
            // Captura o JSONException: No value for streamingData aqui
            Log.e("YouTubeRepo", "Falha crítica na extração: ${e.message}")
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

        // 1. Tentar Piped
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

        // 2. Fallback Google Chave 1
        if (results.isEmpty()) {
            results.addAll(searchViaYoutubeOfficial(trimmedQuery, youtubeApiKey1))
        }

        // 3. Fallback Google Chave 2
        if (results.isEmpty() && youtubeApiKey2.length > 10) {
            results.addAll(searchViaYoutubeOfficial(trimmedQuery, youtubeApiKey2))
        }

        results
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
                    val videoId = item.getJSONObject("id").getString("videoId")
                    val snippet = item.getJSONObject("snippet")
                    
                    // Extração de imagem corrigida para evitar erro de referência
                    val thumbnails = snippet.getJSONObject("thumbnails")
                    val highRes = thumbnails.getJSONObject("high")
                    val thumbUrl = highRes.getString("url")

                    list.add(OnlineSong(videoId, snippet.getString("title"), snippet.getString("channelTitle"), thumbUrl, null))
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro API Oficial: ${e.message}")
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
