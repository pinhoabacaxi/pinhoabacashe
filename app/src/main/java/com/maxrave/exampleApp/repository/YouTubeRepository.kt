package com.maxrave.exampleApp.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YouTubeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Modelo simples para representar uma Playlist na busca
data class YouTubePlaylist(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: String,
    val author: String
)

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    
    // API Keys (Omitidas conforme solicitado)
    private val youtubeApiKey1 = 
    private val youtubeApiKey2 = 
    /**
     * Extrai o link direto de áudio de um vídeo específico
     */
    suspend fun extractAudioLink(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        return@withContext fetchFromInnerTube(videoId)
    }
     
    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> = withContext(Dispatchers.IO) {
        val playlistResults = mutableListOf<YouTubePlaylist>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/search"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("query", query)
                // O parâmetro 'params' abaixo filtra a busca apenas para PLAYLISTS
                put("params", "EgIQAw%3D%3D") 
                put("context", createInnerTubeContext())
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                // Navegação no JSON complexo do InnerTube para encontrar playlists
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
                        val renderer = it.optJSONObject(i)?.optJSONObject("playlistRenderer")
                        if (renderer != null) {
                            val playlistId = renderer.optString("playlistId")
                            val title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            val author = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            val videoCount = renderer.optString("videoCount")
                            val thumbs = renderer.optJSONObject("thumbnails")?.optJSONArray("thumbnails")
                            val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""

                            playlistResults.add(YouTubePlaylist(playlistId, title, thumbUrl, videoCount, author))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao pesquisar playlists: ${e.message}")
        }
        return@withContext playlistResults
    }
    suspend fun getPlaylistVideos(playlistId: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<OnlineSong>()
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/browse"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("browseId", "VL$playlistId") // Prefixo VL é necessário para playlists
                put("context", createInnerTubeContext())
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                // Navegação no JSON para encontrar a lista de vídeos
                val tabs = json.optJSONArray("contents")?.optJSONObject(0) // Geralmente SingleColumnBrowseResults
                val sectionList = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")?.optJSONObject(0)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")?.optJSONObject(0)
                    ?.optJSONObject("playlistVideoListRenderer")
                    ?.optJSONArray("contents")

                sectionList?.let {
                    for (i in 0 until it.length()) {
                        val videoRenderer = it.optJSONObject(i)?.optJSONObject("playlistVideoRenderer")
                        if (videoRenderer != null) {
                            val vId = videoRenderer.optString("videoId")
                            val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            val author = videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            val duration = videoRenderer.optString("lengthSeconds")
                            val thumbs = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""

                            // Adicionamos à lista. Note que a 'url' de streaming será extraída apenas no momento do download
                            songs.add(OnlineSong(vId, title, author, thumbUrl, "", duration))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair vídeos da playlist: ${e.message}")
        }
        return@withContext songs
    }

    // --- MÉTODOS AUXILIARES PRIVADOS PARA EVITAR REPETIÇÃO ---

    private fun createPostConnection(apiUrl: String): HttpURLConnection {
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL})")
        YouTubeSession.cookies?.let { conn.setRequestProperty("Cookie", it) }
        conn.doOutput = true
        return conn
    }

    private fun createInnerTubeContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_VR")
                put("clientVersion", "1.50.41")
                put("osName", "Android")
                put("osVersion", Build.VERSION.RELEASE)
                put("hl", "pt-BR")
                put("gl", "BR")
                YouTubeSession.visitorData?.let { put("visitorData", it) }
            })
        }
    }

    private fun fetchFromInnerTube(videoId: String): OnlineSong? {
        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
            val conn = createPostConnection(apiUrl)
            
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", createInnerTubeContext())
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 20000)
                    })
                })
            }
    
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
    
            if (conn.responseCode != 200) return null
    
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val streamingData = json.optJSONObject("streamingData")
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            
            val title = videoDetails.optString("title")
            val author = videoDetails.optString("author")
            val durationSeconds = videoDetails.optString("lengthSeconds")
            val thumbnails = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
    
            if (streamingData != null) {
                val formats = streamingData.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val format = formats.getJSONObject(i)
                        if (format.optString("mimeType").contains("audio")) {
                            val audioUrl = format.optString("url")
                            if (audioUrl.isNotEmpty()) {
                                return OnlineSong(videoId, title, author, thumbUrl, audioUrl, durationSeconds)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica no fetch: ${e.message}")
        }
        return null
    }
}
