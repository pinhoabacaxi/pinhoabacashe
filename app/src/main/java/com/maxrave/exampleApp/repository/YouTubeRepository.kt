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
    
    // Suas duas chaves de API para fallback
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 

    suspend fun downloadMusic(videoId: String): Unit = withContext(Dispatchers.IO) {
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
            Log.e("YouTubeRepo", "Erro no download: ${e.message}")
        }
    }

    suspend fun extractMusicInfo(input: String): OnlineSong? = withContext(Dispatchers.IO) {
        // Se o input já for uma URL, usa ela. Se for só o ID, monta a URL.
        val url = if (input.contains("http")) input else "https://www.youtube.com/watch?v=$input"
        try {
            extractor.extract(url)
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()

            if (meta != null && ytFiles != null) {
                val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url
                val vId = if (input.contains("http")) extractVideoId(input) else input
                
                OnlineSong(
                    videoId = vId,
                    title = meta.title ?: "Sem título",
                    author = meta.author ?: "Artista",
                    thumbnailUrl = meta.maxResImageUrl,
                    streamUrl = bestAudio
                )
            } else null
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro na extração: ${e.message}")
            null
        }
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        // 1. Prioridade: Links diretos (Processa e já retorna)
        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val info = extractMusicInfo(trimmedQuery)
            if (info != null) results.add(info)
            return@withContext results
        }

        // 2. Fallback: Piped (APIs Públicas)
        val apiInstances = arrayOf("https://pipedapi.kavin.rocks", "https://api.piped.victr.me")
        for (baseUrl in apiInstances) {
            try {
                val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")
                val url = URL("$baseUrl/search?q=$encoded&filter=music_songs")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                if (conn.responseCode == 200) {
                    val items = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("items") ?: JSONArray()
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
            } catch (e: Exception) { Log.e("YouTubeRepo", "Piped falhou") }
        }

        // 3. Fallback Oficial: Chave 1
        if (results.isEmpty()) {
            results.addAll(searchViaYoutubeOfficial(trimmedQuery, youtubeApiKey1))
        }

        // 4. Fallback Oficial: Chave 2 (se a 1 falhar ou acabar a cota)
        if (results.isEmpty() && youtubeApiKey2 != "COLE_AQUI_SUA_SEGUNDA_CHAVE") {
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
                val items = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getJSONObject("id").getString("videoId")
                    val snippet = item.getJSONObject("snippet")
                    list.add(OnlineSong(id, snippet.getString("title"), snippet.getString("channelTitle"), snippet.getString("thumbnails").getJSONObject("high").getString("url"), null))
                }
            }
        } catch (e: Exception) { Log.e("YouTubeRepo", "Google API falhou") }
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
