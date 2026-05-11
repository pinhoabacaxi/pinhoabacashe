package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeRepository(private val context: Context) {

    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        
        try {
            extractor.extract(url) 
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()

            if (meta != null && ytFiles != null) {
                val audioFiles = ytFiles.getAudioOnly()
                val bestAudio = audioFiles.firstOrNull()?.url

                return@withContext OnlineSong(
                    videoId = videoId,
                    title = meta.title ?: "Sem título",
                    author = meta.author ?: "Artista desconhecido",
                    thumbnailUrl = meta.maxResImageUrl,
                    streamUrl = bestAudio
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        
        // CORREÇÃO: Se for um link, extrai direto sem usar a API de busca
        if (query.contains("youtube.com") || query.contains("youtu.be")) {
            try {
                val videoId = extractVideoId(query)
                val info = extractMusicInfo(videoId)
                if (info != null) results.add(info)
                return@withContext results
            } catch (e: Exception) {
                return@withContext emptyList()
            }
        }

        // Se for pesquisa por texto, tenta instâncias diferentes (Fallback)
        val instances = arrayOf(
            "https://piped-api.privacydev.net",
            "https://pipedapi.kavin.rocks",
            "https://api.piped.victr.me"
        )

        for (baseUrl in instances) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = URL("$baseUrl/search?q=$encodedQuery&filter=music_songs")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val items = jsonObject.optJSONArray("items") ?: JSONArray()

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null && item.optString("type") == "stream") {
                            val urlPath = item.optString("url")
                            val videoId = urlPath.replace("/watch?v=", "")
                            
                            results.add(OnlineSong(
                                videoId = videoId,
                                title = item.optString("title") ?: "Sem título",
                                author = item.optString("uploaderName") ?: "Desconhecido",
                                thumbnailUrl = item.optString("thumbnail"),
                                streamUrl = null
                            ))
                        }
                        if (results.size >= 15) break
                    }
                    if (results.isNotEmpty()) break // Se conseguiu resultados, para de tentar outras instâncias
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeRepo", "Falha na instância $baseUrl: ${e.message}")
                // Continua para a próxima instância do loop
            }
        }
        results
    }

    private fun extractVideoId(url: String): String {
        return try {
            if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            } else if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore("&").substringBefore("/")
            } else {
                url // Assume que já é o ID
            }
        } catch (e: Exception) { url }
    }
}
