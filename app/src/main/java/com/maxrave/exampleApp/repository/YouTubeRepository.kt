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

// ... (código existente da classe YouTubeRepository) ...
class YouTubeRepository(private val context: Context) {

    // Instância única para evitar overhead
    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
    
        try {
            extractor.extract(youtubeUrl)
            val meta = extractor.getVideoMeta()
            [span_3](start_span)val ytFiles = extractor.getYTFiles() //[span_3](end_span)
    
            // Pega o melhor formato de áudio disponível
            val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull()

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context)
                downloader.startDownload(
                    [span_4](start_span)title = meta.title ?: "Som", //[span_4](end_span)
                    artist = meta.author ?: "Desconhecido",
                    url = bestAudio.url!! [span_5](start_span)//[span_5](end_span)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
        suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        try {
            // Se o usuário colou um link direto do YouTube, usamos o método antigo
            if (query.contains("youtube.com") || query.contains("youtu.be")) {
                val videoId = extractVideoId(query)
                val info = extractMusicInfo(videoId)
                if (info != null) results.add(info)
                return@withContext results
            }

            // Caso contrário, fazemos a busca por texto usando a API pública do Piped
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // filter=music_songs ajuda a trazer resultados mais voltados para música
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

                // Parse do JSON nativo do Android
                val jsonObject = JSONObject(response.toString())
                val items = jsonObject.optJSONArray("items") ?: JSONArray()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    // Garantimos que estamos pegando apenas streams de vídeo/áudio
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
                                streamUrl = null // Será extraído só quando o usuário clicar para ouvir
                            )
                        )
                        
                        // Limita a 20 resultados para não pesar a lista
                        if (results.size >= 20) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    // Função auxiliar para extrair o ID de um link colado
    private fun extractVideoId(url: String): String {
        return if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?")
        } else {
            url.substringAfter("v=").substringBefore("&")
        }
    }
    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        
        try {
            extractor.extract(url) 
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()

            if (meta != null && ytFiles != null) {
                [span_6](start_span)val audioFiles = ytFiles.getAudioOnly() //[span_6](end_span)
                val bestAudio = audioFiles.firstOrNull()?.url

                return@withContext OnlineSong(
                    videoId = videoId,
                    [span_7](start_span)title = meta.title ?: "Sem título", //[span_7](end_span)
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
}
