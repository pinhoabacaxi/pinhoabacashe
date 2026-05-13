package com.maxrave.exampleApp.repository

import android.content.Context
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class YouTubePlaylist(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: String,
    val author: String
)

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU" 
    suspend fun extractAudioLink(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }

    suspend fun searchVideos(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("query", query)
                put("params", "EgIQAQ%3D%3D") 
                put("context", createInnerTubeContext())
            }

            sendPayload(conn, payload)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            contents?.let {
                for (i in 0 until it.length()) {
                    val videoRenderer = it.optJSONObject(i)?.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        videoResults.add(parseVideoRenderer(videoRenderer))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar vídeos: ${e.message}")
        }
        return@withContext videoResults
    }

    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> = withContext(Dispatchers.IO) {
        val playlistResults = mutableListOf<YouTubePlaylist>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("query", query)
                put("params", "EgIUAQ%3D%3D")
                put("context", createInnerTubeContext())
            }

            sendPayload(conn, payload)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            contents?.let {
                for (i in 0 until it.length()) {
                    val playlistRenderer = it.optJSONObject(i)?.optJSONObject("playlistRenderer")
                    if (playlistRenderer != null) {
                        playlistResults.add(parsePlaylistRenderer(playlistRenderer))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar playlists: ${e.message}")
        }
        return@withContext playlistResults
    }

    suspend fun getPlaylistVideos(playlistId: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/browse"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("browseId", "VL$playlistId")
                put("context", createInnerTubeContext())
            }

            sendPayload(conn, payload)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val tabs = json.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
            val section = tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.optJSONObject(0)
                ?.optJSONObject("playlistVideoListRenderer")?.optJSONArray("contents")

            section?.let {
                for (i in 0 until it.length()) {
                    val videoRenderer = it.optJSONObject(i)?.optJSONObject("playlistVideoRenderer")
                    if (videoRenderer != null) {
                        videoResults.add(parseVideoRenderer(videoRenderer))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar vídeos da playlist: ${e.message}")
        }
        return@withContext videoResults
    }

    private fun parseVideoRenderer(obj: JSONObject): VideoMeta {
        val videoId = obj.optString("videoId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
        
        return VideoMeta(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbUrl,
            duration = 0L,
            viewCount = 0L,
            isLiveStream = false,
            description = "",
            channelId = "" 
        )
    }
    
    private fun parsePlaylistRenderer(obj: JSONObject): YouTubePlaylist {
        val id = obj.optString("playlistId")
        
        // Tenta pegar o título de diferentes estruturas possíveis do JSON do YouTube
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
            ?: obj.optJSONObject("title")?.optString("simpleText") ?: ""
        
        // Pega a contagem de vídeos como String
        val count = obj.optString("videoCount") 
        
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
            ?: "YouTube"
        
        // Tenta pegar a melhor thumbnail disponível
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails") 
            ?: obj.optJSONObject("thumbnails")?.optJSONArray("thumbnails")?.optJSONArray(0)
        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
        
        
        // RETORNO COM PARÂMETROS NOMEADOS:
        // Isso garante que cada variável entre no campo correto da data class
        return YouTubePlaylist(
            playlistId = id,
            title = title,
            thumbnailUrl = thumbUrl,
            videoCount = count, // Passando o valor como String
            author = author
        )
    }
    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", createInnerTubeContext())
            }
    
            sendPayload(conn, payload)
            if (conn.responseCode != 200) return null
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val streamingData = json.optJSONObject("streamingData") ?: return null
            
            val title = videoDetails.optString("title")
            val author = videoDetails.optString("author")
            val duration = videoDetails.optString("lengthSeconds")
            val thumbnails = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
    
            val formats = streamingData.optJSONArray("adaptiveFormats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.getJSONObject(i)
                    if (format.optString("mimeType").contains("audio")) {
                        val audioUrl = format.optString("url")
                        if (audioUrl.isNotEmpty()) {
                            return OnlineSong(videoId, title, author, thumbUrl, audioUrl, duration)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha no fetch: ${e.message}")
        }
        return null
    }

    private fun createPostConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.doOutput = true
        return conn
    }

    private fun sendPayload(conn: HttpURLConnection, payload: JSONObject) {
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    }

    private fun createInnerTubeContext() = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "ANDROID")
            put("clientVersion", "19.05.36")
        })
    }
}
