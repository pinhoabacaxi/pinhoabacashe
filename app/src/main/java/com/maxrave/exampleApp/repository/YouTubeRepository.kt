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
            val response = if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            response?.let {
                val json = JSONObject(it)
                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")?.optJSONObject(0)
                    ?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                
                contents?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optJSONObject("videoRenderer")?.let { video ->
                            videoResults.add(parseVideoRenderer(video))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro Vídeos: ${e.message}") }
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
            val response = if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            response?.let {
                val json = JSONObject(it)
                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")?.optJSONObject(0)
                    ?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")

                contents?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optJSONObject("playlistRenderer")?.let { playlist ->
                            playlistResults.add(parsePlaylistRenderer(playlist))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro Playlist: ${e.message}") }
        return@withContext playlistResults
    }

    private fun parseVideoRenderer(obj: JSONObject): VideoMeta {
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        return VideoMeta(
            videoId = obj.optString("videoId"),
            title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "",
            author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "",
            thumbnailUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: "",
            duration = 0L, viewCount = 0L, isLiveStream = false, description = "", channelId = ""
        )
    }

    private fun parsePlaylistRenderer(obj: JSONObject): YouTubePlaylist {
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: obj.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")
        
        return YouTubePlaylist(
            playlistId = obj.optString("playlistId"),
            title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: obj.optJSONObject("title")?.optString("simpleText") ?: "",
            thumbnailUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: "",
            videoCount = obj.optString("videoCount") ?: "0",
            author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
        )
    }

    private fun createPostConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        // User-Agent real do Android para evitar bloqueios
        conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 11)")
        conn.doOutput = true
        return conn
    }

    private fun sendPayload(conn: HttpURLConnection, payload: JSONObject) {
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun createInnerTubeContext() = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "ANDROID")
            put("clientVersion", "19.05.36")
            put("hl", "pt-BR")
            put("gl", "BR")
        })
    }

    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/player")
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", createInnerTubeContext())
            }
            sendPayload(conn, payload)
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val streamingData = json.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("adaptiveFormats")
            for (i in 0 until (formats?.length() ?: 0)) {
                val f = formats!!.getJSONObject(i)
                if (f.optString("mimeType").contains("audio")) {
                    return OnlineSong(
                        videoId, videoDetails.optString("title"), videoDetails.optString("author"),
                        "", f.optString("url"), videoDetails.optString("lengthSeconds")
                    )
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Fetch falhou: ${e.message}") }
        return null
    }
}
