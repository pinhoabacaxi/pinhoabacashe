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
    private val youtubeApiKey1 = ""
    private val youtubeApiKey2 = "" 
    suspend fun extractAudioLink(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }

    suspend fun searchVideos(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/search")
            val payload = JSONObject().apply {
                put("query", query)
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
                    if (videoRenderer != null) videoResults.add(parseVideoRenderer(videoRenderer))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro busca vídeos: ${e.message}") }
        return@withContext videoResults
    }

    suspend fun getPlaylistVideos(playlistId: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/browse")
            val payload = JSONObject().apply {
                put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
                put("context", createInnerTubeContext())
            }
            sendPayload(conn, payload)
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Navegação no JSON do YouTube para extrair vídeos da playlist
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("playlistVideoListRenderer")
                ?.optJSONArray("contents")
    
            contents?.let {
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
    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> = withContext(Dispatchers.IO) {
        val playlistResults = mutableListOf<YouTubePlaylist>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/search")
            val payload = JSONObject().apply {
                put("query", query)
                put("params", "EgIUAQ%3D%3D") // Filtro de playlist
                put("context", createInnerTubeContext())
            }
            sendPayload(conn, payload)

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val contents = JSONObject(response).optJSONObject("contents")
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
                    if (playlistRenderer != null) playlistResults.add(parsePlaylistRenderer(playlistRenderer))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro busca playlist: ${e.message}") }
        return@withContext playlistResults
    }

    private fun parseVideoRenderer(obj: JSONObject): VideoMeta {
        val videoId = obj.optString("videoId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        return VideoMeta(videoId, title, author, thumbUrl, 0L, 0L, false, "", "")
    }

    private fun parsePlaylistRenderer(obj: JSONObject): YouTubePlaylist {
        val id = obj.optString("playlistId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val author = obj.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
        val count = obj.optString("videoCount")
        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        return YouTubePlaylist(id, title, thumbUrl, count, author)
    }

    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/player")
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", createInnerTubeContext())
            }
            sendPayload(conn, payload)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val streamingData = json.optJSONObject("streamingData") ?: return null
            
            val formats = streamingData.optJSONArray("adaptiveFormats")
            for (i in 0 until (formats?.length() ?: 0)) {
                val f = formats!!.getJSONObject(i)
                if (f.optString("mimeType").contains("audio")) {
                    return OnlineSong(
                        videoId, videoDetails.optString("title"),
                        videoDetails.optString("author"), "",
                        f.optString("url"), videoDetails.optString("lengthSeconds")
                    )
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro streaming: ${e.message}") }
        return null
    }

    private fun createPostConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            // User-Agent de navegador para aceitar o cliente WEB
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            doOutput = true
        }
    }

    private fun sendPayload(conn: HttpURLConnection, payload: JSONObject) {
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun createInnerTubeContext() = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", "2.20240210.01.00")
            put("hl", "pt-BR")
            put("gl", "BR")
        })
    }
}
