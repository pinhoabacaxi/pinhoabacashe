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
    // === 1. BUSCA DE VÍDEOS E PLAYLISTS ===
    suspend fun searchVideos(query: String): List<VideoMeta> = searchYouTube(query, isPlaylist = false) as List<VideoMeta>
    
    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> = searchYouTube(query, isPlaylist = true) as List<YouTubePlaylist>

    private suspend fun searchYouTube(query: String, isPlaylist: Boolean): List<Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Any>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/search")
            val payload = JSONObject().apply {
                put("context", createInnerTubeContext())
                put("query", query)
                // Se for playlist, adiciona o parâmetro específico
                if (isPlaylist) put("params", "EgIUAQ%3D%3D") 
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
                    val item = it.optJSONObject(i)
                    if (isPlaylist) {
                        item?.optJSONObject("playlistRenderer")?.let { results.add(parsePlaylist(it)) }
                    } else {
                        item?.optJSONObject("videoRenderer")?.let { results.add(parseVideo(it)) }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro na busca: ${e.message}") }
        return@withContext results
    }

    // === 2. OBTER VÍDEOS DE UMA PLAYLIST ===
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
                        videoResults.add(parseVideo(videoRenderer))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar vídeos da playlist: ${e.message}")
        }
        return@withContext videoResults
    }

    // === 3. EXTRAÇÃO DE LINK DE ÁUDIO (Streaming e Download) ===
    suspend fun extractAudioLink(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/player")
            val payload = JSONObject().apply {
                put("context", createInnerTubeContext())
                put("videoId", videoId)
            }
            sendPayload(conn, payload)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            
            val streamingData = json.optJSONObject("streamingData") ?: return@withContext null
            val formats = streamingData.optJSONArray("adaptiveFormats")
            
            for (i in 0 until (formats?.length() ?: 0)) {
                val fmt = formats!!.getJSONObject(i)
                if (fmt.optString("mimeType").contains("audio")) {
                    return@withContext OnlineSong(
                        videoId, 
                        json.optJSONObject("videoDetails")?.optString("title") ?: "Música",
                        json.optJSONObject("videoDetails")?.optString("author") ?: "",
                        "", fmt.optString("url"), ""
                    )
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Erro extração: ${e.message}") }
        null
    }

    // === 4. FUNÇÕES DE PARSING E CONEXÃO ===
    private fun parseVideo(obj: JSONObject): VideoMeta {
        val videoId = obj.optString("videoId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        
        // Passando 0L para evitar o erro de 'Integer Literal' do Kotlin
        return VideoMeta(videoId, title, author, thumbUrl, 0L, 0L, false, "", "")
    }

    private fun parsePlaylist(obj: JSONObject): YouTubePlaylist {
        return YouTubePlaylist(
            playlistId = obj.optString("playlistId"),
            title = obj.optJSONObject("title")?.optString("simpleText") ?: "Sem título",
            thumbnailUrl = obj.optJSONObject("thumbnails")?.optJSONArray(0)?.optJSONObject(0)?.optString("url") ?: "",
            videoCount = obj.optString("videoCount"),
            author = obj.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
        )
    }

    private fun createPostConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            doOutput = true
        }
    }

    private fun sendPayload(conn: HttpURLConnection, payload: JSONObject) {
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    }

    private fun createInnerTubeContext() = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", "2.20240101.01.00")
            put("hl", "pt-BR")
            put("gl", "BR")
        })
    }
}
