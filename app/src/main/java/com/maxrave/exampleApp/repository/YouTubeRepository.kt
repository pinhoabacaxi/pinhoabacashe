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
        try {
            if (query.contains("youtube.com") || query.contains("youtu.be")) {
                val videoId = extractVideoId(query)
                val info = extractMusicInfo(videoId)
                if (info != null) results.add(info)
                return@withContext results
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://pipedapi.kavin.rocks/search?q=$encodedQuery&filter=music_songs")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonObject = JSONObject(response.toString())
                val items = jsonObject.optJSONArray("items") ?: JSONArray()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    if (item != null && item.optString("type") == "stream") {
                        val urlPath = item.optString("url")
                        val videoId = urlPath.replace("/watch?v=", "")
                        val title = item.optString("title")
                        val uploader = item.optString("uploaderName")
                        val thumbnail = item.optString("thumbnail")

                        results.add(
                            OnlineSong(
                                videoId = videoId,
                                title = title,
                                author = uploader,
                                thumbnailUrl = thumbnail,
                                streamUrl = null
                            )
                        )
                        
                        if (results.size >= 20) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    private fun extractVideoId(url: String): String {
        return if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?")
        } else {
            url.substringAfter("v=").substringBefore("&")
        }
    }
}
