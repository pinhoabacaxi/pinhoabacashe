package com.maxrave.exampleApp.repository

import android.content.Context
import android.util.Log
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeRepository(private val context: Context) {

    [span_1](start_span)private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)[span_1](end_span)

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        [span_2](start_span)val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"[span_2](end_span)
        try {
            [span_3](start_span)extractor.extract(youtubeUrl)[span_3](end_span)
            [span_4](start_span)val meta = extractor.getVideoMeta()[span_4](end_span)
            [span_5](start_span)val ytFiles = extractor.getYTFiles()[span_5](end_span)
            [span_6](start_span)val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull()[span_6](end_span)

            if (meta != null && bestAudio?.url != null) {
                [span_7](start_span)val downloader = DownloadHelper(context)[span_7](end_span)
                downloader.startDownload(
                    [span_8](start_span)title = meta.title ?: "Som",[span_8](end_span)
                    [span_9](start_span)artist = meta.author ?: "Desconhecido",[span_9](end_span)
                    [span_10](start_span)url = bestAudio.url[span_10](end_span)!!
                )
            }
        } catch (e: Exception) {
            [span_11](start_span)Log.e("YouTubeRepo", "Erro no download: ${e.message}")[span_11](end_span)
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        [span_12](start_span)val url = "https://www.youtube.com/watch?v=$videoId"[span_12](end_span)
        try {
            [span_13](start_span)extractor.extract(url)[span_13](end_span)
            [span_14](start_span)val meta = extractor.getVideoMeta()[span_14](end_span)
            [span_15](start_span)val ytFiles = extractor.getYTFiles()[span_15](end_span)

            if (meta != null && ytFiles != null) {
                [span_16](start_span)val bestAudio = ytFiles.getAudioOnly().firstOrNull()?.url[span_16](end_span)

                return@withContext OnlineSong(
                    [span_17](start_span)videoId = videoId,[span_17](end_span)
                    [span_18](start_span)title = meta.title ?: "Sem título",[span_18](end_span)
                    [span_19](start_span)author = meta.author ?: "Artista desconhecido",[span_19](end_span)
                    [span_20](start_span)thumbnailUrl = meta.maxResImageUrl,[span_20](end_span)
                    [span_21](start_span)streamUrl = bestAudio[span_21](end_span)
                )
            }
        } catch (e: Exception) {
            [span_22](start_span)Log.e("YouTubeRepo", "Erro na extração: ${e.message}")[span_22](end_span)
        }
        null
    }

    suspend fun searchTracks(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSong>()
        val trimmedQuery = query.trim()

        [span_23](start_span)// 1. PRIORIDADE: Se for um link, processa localmente[span_23](end_span)
        if (trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")) {
            val vId = extractVideoId(trimmedQuery)
            val info = extractMusicInfo(vId)
            if (info != null) results.add(info)
            return@withContext results
        }

        [span_24](start_span)// 2. FALLBACK: APIs para busca por texto[span_24](end_span)
        val apiInstances = arrayOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.victr.me",
            "https://piped-api.privacydev.net"
        )

        for (baseUrl in apiInstances) {
            try {
                [span_25](start_span)val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")[span_25](end_span)
                [span_26](start_span)val url = URL("$baseUrl/search?q=$encoded&filter=music_songs")[span_26](end_span)
                
                [span_27](start_span)val conn = url.openConnection() as HttpURLConnection[span_27](end_span)
                [span_28](start_span)conn.requestMethod = "GET"[span_28](end_span)
                [span_29](start_span)conn.connectTimeout = 3000[span_29](end_span)
                [span_30](start_span)conn.readTimeout = 3000[span_30](end_span)
                [span_31](start_span)conn.setRequestProperty("User-Agent", "Mozilla/5.0")[span_31](end_span)

                if (conn.responseCode == 200) {
                    [span_32](start_span)val response = conn.inputStream.bufferedReader().use { it.readText() }[span_32](end_span)
                    [span_33](start_span)val items = JSONObject(response).optJSONArray("items") ?: JSONArray()[span_33](end_span)

                    for (i in 0 until items.length()) {
                        [span_34](start_span)val item = items.optJSONObject(i)[span_34](end_span)
                        [span_35](start_span)if (item != null && item.optString("type") == "stream") {[span_35](end_span)
                            [span_36](start_span)val rawUrl = item.optString("url")[span_36](end_span)
                            [span_37](start_span)val id = rawUrl.substringAfter("v=", "")[span_37](end_span)
                            if (id.isNotEmpty()) {
                                results.add(OnlineSong(
                                    [span_38](start_span)videoId = id,[span_38](end_span)
                                    [span_39](start_span)title = item.optString("title") ?: "Sem título",[span_39](end_span)
                                    [span_40](start_span)author = item.optString("uploaderName") ?: "Canal",[span_40](end_span)
                                    [span_41](start_span)thumbnailUrl = item.optString("thumbnail"),[span_41](end_span)
                                    streamUrl = null
                                ))
                            }
                        }
                    }
                    [span_42](start_span)if (results.isNotEmpty()) break[span_42](end_span)
                }
            } catch (e: Exception) {
                [span_43](start_span)Log.e("YouTubeRepo", "Falha na instância $baseUrl: ${e.message}")[span_43](end_span)
            }
        }
        [span_44](start_span)results[span_44](end_span)
    }

    private fun extractVideoId(url: String): String {
        return try {
            when {
                [span_45](start_span)url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").split("/").first()[span_45](end_span)
                [span_46](start_span)url.contains("v=") -> url.substringAfter("v=").substringBefore("&").split("/").first()[span_46](end_span)
                [span_47](start_span)else -> url[span_47](end_span)
            }
        [span_48](start_span)} catch (e: Exception) { url }[span_48](end_span)
    }
}
