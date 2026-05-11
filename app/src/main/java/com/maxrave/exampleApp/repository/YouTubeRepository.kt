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
                Log.e("YouTubeRepo", "Não foi possível encontrar metadados ou URL de áudio")
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

        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val vId = extractVideoId(trimmedQuery)
            val info = extractMusicInfo(vId)
            if (info != null) results.add(info)
            return@withContext results
        }

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
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").split("/").last()
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&").split("v=").last().split("&").first()
                else -> url
            }
        } catch (e: Exception) { url }
    }
}
