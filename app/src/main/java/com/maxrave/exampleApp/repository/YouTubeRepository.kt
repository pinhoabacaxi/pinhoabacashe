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
    // Sua chave de API integrada
    private val youtubeApiKey = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" // Cole sua nova chave aqui

    suspend fun downloadMusic(videoId: String): Unit = withContext(Dispatchers.IO) {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
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
            } else {
                Log.e("YouTubeRepo", "Metadados não encontrados para download")
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro no download: ${e.message}")
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        try {
            extractor.extract(url)
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()

            if (meta != null && ytFiles != null) {
                val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url
                OnlineSong(
                    videoId = videoId,
                    title = meta.title ?: "Sem título",
                    author = meta.author ?: "Artista desconhecido",
                    thumbnailUrl = meta.maxResImageUrl,
                    streamUrl = bestAudio
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro na extração: ${e.message}")
            null
        }
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        // 1. Prioridade: Se for link direto
        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val vId = extractVideoId(trimmedQuery)
            val info = extractMusicInfo(vId)
            if (info != null) results.add(info)
            return@withContext results
        }

        // 2. Tentar APIs Públicas (Piped)
        val apiInstances = arrayOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.victr.me",
            "https://piped-api.privacydev.net"
        )

        for (baseUrl in apiInstances) {
            try {
                val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")
                val url = URL("$baseUrl/search?q=$encoded&filter=music_songs")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONObject(response).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null && item.optString("type") == "stream") {
                            val id = item.optString("url").substringAfter("v=", "")
                            if (id.isNotEmpty()) {
                                results.add(OnlineSong(id, item.optString("title"), item.optString("uploaderName"), item.optString("thumbnail"), null))
                            }
                        }
                    }
                    if (results.isNotEmpty()) return@withContext results
                }
            } catch (e: Exception) {
                Log.e("YouTubeRepo", "Falha na instância $baseUrl")
            }
        }

        // 3. Fallback Final: YouTube API v3 (Google)
        if (results.isEmpty()) {
            Log.d("YouTubeRepo", "Usando Google API Fallback...")
            return@withContext searchViaYoutubeOfficial(trimmedQuery)
        }

        results
    }

        private suspend fun searchViaYoutubeOfficial(query: String, apiKey: String): List<OnlineSong> = withContext(Dispatchers.IO) {
            val officialResults = mutableListOf<OnlineSong>()
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=$encoded&type=video&videoCategoryId=10&maxResults=15&key=$apiKey")
            
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
            
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val items = json.optJSONArray("items") ?: JSONArray()
                
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.getJSONObject("id").getString("videoId")
                        val snippet = item.getJSONObject("snippet")
                        officialResults.add(OnlineSong(
                            videoId = id,
                            title = snippet.getString("title"),
                            author = snippet.getString("channelTitle"),
                            thumbnailUrl = snippet.getJSONObject("thumbnails").getJSONObject("high").getString("url"),
                            streamUrl = null
                        ))
                    }
                } else {
                    Log.e("YouTubeRepo", "Erro na API Google (Status ${conn.responseCode}). Provavelmente cota esgotada.")
                }
            } catch (e: Exception) {
                Log.e("YouTubeRepo", "Erro na busca oficial: ${e.message}")
            }
            officialResults
        }


    private fun extractVideoId(url: String): String {
        return try {
            when {
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").split("/").last()
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&").split("v=").last().split("&").first()
                else -> url
            }
        } catch (e: Exception) { url }
    }
}
