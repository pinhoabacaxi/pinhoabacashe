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

/**
 * Representa uma Playlist do YouTube nos resultados de busca.
 */
data class YouTubePlaylist(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: String,
    val author: String
)

class YouTubeRepository(private val context: Context) {
    private val TAG = "YouTubeRepo"
    
    // Chaves de API (Omitidas por privacidade)
    private val youtubeApiKey1 = "AIzaSyBiMZ0Z7TZ8sDYJEEt3Ao9jVFk7Zn8BJ5k"
    private val youtubeApiKey2 = "AIzaSyAkFEB8PV60dgxAtl604c7wn41mgiigUMU"

    // === 1. BUSCA UNIFICADA (VÍDEOS E PLAYLISTS) ===

    /**
     * Busca apenas vídeos individuais.
     */
    suspend fun searchVideos(query: String): List<VideoMeta> = 
        searchYouTube(query, isPlaylist = false) as List<VideoMeta>
    
    /**
     * Busca apenas coleções de playlists.
     */
    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> = 
        searchYouTube(query, isPlaylist = true) as List<YouTubePlaylist>

    private suspend fun searchYouTube(query: String, isPlaylist: Boolean): List<Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Any>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/search")
            val payload = JSONObject().apply {
                put("context", createInnerTubeContext())
                put("query", query)
                // Parâmetro vital para filtrar o tipo de conteúdo no YouTube
                if (isPlaylist) {
                    put("params", "EgIUAQ%3D%3D") // Filtro de Playlists
                } else {
                    put("params", "EgIQAQ%3D%3D") // Filtro de Vídeos
                }
            }
            sendPayload(conn, payload)

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Navegação na estrutura complexa do InnerTube
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
        } catch (e: Exception) { 
            Log.e(TAG, "Erro na busca unificada: ${e.message}") 
        }
        return@withContext results
    }

    // === 2. EXPANSÃO DE PLAYLIST (LISTAR VÍDEOS INTERNOS) ===

    /**
     * Obtém todos os vídeos contidos em uma playlist específica para possibilitar o download em massa.
     */
    suspend fun getPlaylistVideos(playlistId: String): List<VideoMeta> = withContext(Dispatchers.IO) {
        val videoResults = mutableListOf<VideoMeta>()
        try {
            val conn = createPostConnection("https://youtubei.googleapis.com/youtubei/v1/browse")
            val payload = JSONObject().apply {
                // VL é o prefixo interno do YouTube para visualização de listas
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

    // === 3. EXTRAÇÃO DE LINK DE ÁUDIO (STREAMING E DOWNLOAD) ===

    /**
     * Extrai a URL direta do fluxo de áudio necessária para o player e para o Worker de download.
     */
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
                // Busca especificamente por formatos de áudio (m4a/webm)
                if (fmt.optString("mimeType").contains("audio")) {
                    return@withContext OnlineSong(
                        videoId = videoId, 
                        title = json.optJSONObject("videoDetails")?.optString("title") ?: "Música",
                        author = json.optJSONObject("videoDetails")?.optString("author") ?: "",
                        thumbnailUrl = "", 
                        url = fmt.optString("url"), 
                        duration = json.optJSONObject("videoDetails")?.optString("lengthSeconds") ?: ""
                    )
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Erro na extração de link: ${e.message}") 
        }
        null
    }

    // === 4. AUXILIARES DE PARSING ===

    private fun parseVideo(obj: JSONObject): VideoMeta {
        val videoId = obj.optString("videoId")
        val title = obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
            ?: obj.optJSONObject("title")?.optString("simpleText") ?: ""
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        
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

    private fun parsePlaylist(obj: JSONObject): YouTubePlaylist {
        val id = obj.optString("playlistId")
        val title = obj.optJSONObject("title")?.optString("simpleText")
            ?: obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "Sem Título"
        
        val count = obj.optString("videoCount") ?: "0"
        
        val thumbnails = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: obj.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")
    
        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
    
        val author = obj.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: obj.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "YouTube"
    
        return YouTubePlaylist(id, title, thumbUrl, count, author)
    }

    // === 5. CONFIGURAÇÃO DE REDE (INNER TUBE) ===

    private fun createPostConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            // Simular um navegador Desktop para evitar bloqueios de robôs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            doOutput = true
        }
    }

    private fun sendPayload(conn: HttpURLConnection, payload: JSONObject) {
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
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
