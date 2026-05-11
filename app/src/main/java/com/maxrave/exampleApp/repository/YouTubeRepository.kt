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

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId" [cite: 1]
        try {
            extractor.extract(youtubeUrl) [cite: 1]
            val meta = extractor.getVideoMeta() [cite: 1]
            val ytFiles = extractor.getYTFiles() [cite: 2]
            val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull() [cite: 2]

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context) [cite: 2]
                downloader.startDownload(
                    title = meta.title ?: "Som", [cite: 3]
                    artist = meta.author ?: "Desconhecido", [cite: 3]
                    url = bestAudio.url!! [cite: 3]
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro no download: ${e.message}")
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId" [cite: 5]
        try {
            extractor.extract(url) [cite: 5]
            val meta = extractor.getVideoMeta() [cite: 5]
            val ytFiles = extractor.getYTFiles() [cite: 5]

            if (meta != null && ytFiles != null) {
                val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url [cite: 6]

                return@withContext OnlineSong(
                    videoId = videoId, [cite: 6]
                    title = meta.title ?: "Sem título", [cite: 7]
                    author = meta.author ?: "Artista desconhecido", [cite: 7]
                    thumbnailUrl = meta.maxResImageUrl, [cite: 7]
                    streamUrl = bestAudio [cite: 7]
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Erro na extração: ${e.message}")
        }
        null
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        // 1. PRIORIDADE: Se for um link, não tenta DNS de busca, vai direto para o Extrator
        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val vId = extractVideoId(trimmedQuery)
            val info = extractMusicInfo(vId)
            if (info != null) results.add(info)
            return@withContext results
        }

        // 2. FALLBACK: Lista de APIs para busca por texto
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
                conn.connectTimeout = 3000 // Timeout curto para pular rápido se falhar
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONObject(response).optJSONArray("items") ?: JSONArray()

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null && item.optString("type") == "stream") {
                            val rawUrl = item.optString("url")
                            val id = rawUrl.substringAfter("v=", "")
                            if (id.isNotEmpty()) {
                                results.add(OnlineSong(
                                    videoId = id,
                                    title = item.optString("title") ?: "Sem título",
                                    author = item.optString("uploaderName") ?: "Canal",
                                    thumbnailUrl = item.optString("thumbnail"),
                                    streamUrl = null
                                ))
                            }
                        }
                    }
                    if (results.isNotEmpty()) break 
                }
            } catch (e: Exception) {
                Log.e("YouTubeRepo", "Falha na instância $baseUrl: ${e.message}")
            }
        }
        results
    }

    private fun extractVideoId(url: String): String {
        return try {
            when {
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").split("/").first()
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&").split("/").first()
                else -> url
            }
        } catch (e: Exception) { url }
    }
}
