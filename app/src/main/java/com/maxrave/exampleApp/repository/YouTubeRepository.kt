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

// Modelo para representar uma Playlist na busca
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

    // --- BUSCA UNIFICADA (VÍDEOS E PLAYLISTS) ---

    suspend fun searchVideos(query: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search"
            val conn = createPostConnection(apiUrl)
            
            // Payload para buscar apenas vídeos (params: EgIQAQ%3D%3D)
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
            
            // Payload para buscar apenas playlists (params: EgIUAQ%3D%3D)
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

    // --- NOVO: OBTER VÍDEOS DE UMA PLAYLIST ---

    suspend fun getPlaylistVideos(playlistId: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/browse"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("browseId", "VL$playlistId") // VL é o prefixo para playlists no InnerTube
                put("context", createInnerTubeContext())
            }

            sendPayload(conn, payload)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            // Navega no JSON específico de exibição de playlist
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

    // --- MÉTODOS AUXILIARES DE PARSING ---

    private fun parseVideoRenderer(obj: JSONObject): VideoMeta {
        val videoId = obj.optString("videoId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
        return VideoMeta(videoId, title, author, thumbUrl, "")
    }

    private fun parsePlaylistRenderer(obj: JSONObject): YouTubePlaylist {
        val id = obj.optString("playlistId")
        val title = obj.optJSONObject("title")?.optString("simpleText") ?: ""
        val count = obj.optString("videoCount")
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
        val thumbnails = obj.optJSONObject("thumbnails")?.optJSONArray(0)?.optJSONArray("thumbnails")
        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
        return YouTubePlaylist(id, title, thumbUrl, count, author)
    }

    // --- CONFIGURAÇÃO DE REDE (INNERTUBE) ---

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
            put("clientName", "WEB")
            put("clientVersion", "2.20230522.01.00")
        })
    }
}
