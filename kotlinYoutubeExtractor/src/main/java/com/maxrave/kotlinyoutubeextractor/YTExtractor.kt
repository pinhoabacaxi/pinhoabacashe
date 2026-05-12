package com.maxrave.kotlinyoutubeextractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import com.evgenii.jsevaluator.JsEvaluator
import com.evgenii.jsevaluator.interfaces.JsCallback
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

class YTExtractor(
    con: Context, 
    private val CACHING: Boolean = true, 
    private val LOGGING: Boolean = true
) {
    private val LOG_TAG = "YTExtractor"
    private val CLIENT_NAME = "ANDROID_MUSIC"
    private val CLIENT_VERSION = "6.45.52"
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    var ytFiles: SparseArray<YtFile>? = null
    var videoMeta: VideoMeta? = null
    var state: Constant.Status = Constant.Status.PENDING

    private var refContext: WeakReference<Context> = WeakReference(con)
    private var videoID: String? = null

    @Volatile
    private var decipheredSignature: String? = null
    private var decipherJsFileName: String? = null
    private var decipherFunctions: String? = null
    private var decipherFunctionName: String? = null

    private val lock: Lock = ReentrantLock()
    private val jsExecuting = lock.newCondition()

    private val patSignatureDecFunction = Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{1,4})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")
    private val FORMAT_MAP = SparseArray<Format>()

    init {
        setupFormatMap()
    }

    private fun setupFormatMap() {
        FORMAT_MAP.put(140, Format(140, "m4a", -1, Format.VCodec.NONE, Format.ACodec.AAC, 128, true))
        FORMAT_MAP.put(249, Format(249, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 50, true))
        FORMAT_MAP.put(250, Format(250, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 70, true))
        FORMAT_MAP.put(251, Format(251, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 160, true))
        FORMAT_MAP.put(18, Format(18, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, false))
        FORMAT_MAP.put(22, Format(22, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, false))
    }

    private fun getStreamUrls(): SparseArray<YtFile>? {
        val ytFilesResult = SparseArray<YtFile>()
        val encSignatures = SparseArray<String>()

        try {
            val apiUrl = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14)")
            conn.doOutput = true

            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "pt-BR")
                        put("gl", "BR")
                    })
                })
                put("videoId", videoID)
            }

            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val playerResponse = JSONObject(response)

            val streamingData = playerResponse.optJSONObject("streamingData") ?: return null
            val allFormats = mutableListOf<JSONObject>()
            streamingData.optJSONArray("formats")?.let { for(i in 0 until it.length()) allFormats.add(it.getJSONObject(i)) }
            streamingData.optJSONArray("adaptiveFormats")?.let { for(i in 0 until it.length()) allFormats.add(it.getJSONObject(i)) }

            for (formatJson in allFormats) {
                val itag = formatJson.getInt("itag")
                if (FORMAT_MAP[itag] != null) {
                    if (formatJson.has("url")) {
                        ytFilesResult.put(itag, YtFile(FORMAT_MAP[itag], formatJson.getString("url")))
                    } else if (formatJson.has("signatureCipher")) {
                        val cipher = formatJson.getString("signatureCipher")
                        val sigUrl = cipher.split("url=")[1].split("&")[0].let { URLDecoder.decode(it, "UTF-8") }
                        val s = cipher.split("s=")[1].split("&")[0].let { URLDecoder.decode(it, "UTF-8") }
                        
                        ytFilesResult.put(itag, YtFile(FORMAT_MAP[itag], sigUrl))
                        encSignatures.put(itag, s)
                    }
                }
            }

            playerResponse.optJSONObject("videoDetails")?.let { details ->
                videoMeta = VideoMeta(
                    details.optString("videoId"), details.optString("title"),
                    details.optString("author"), details.optString("channelId"),
                    details.optString("lengthSeconds", "0").toLong(),
                    details.optString("viewCount", "0").toLong(),
                    details.optBoolean("isLiveContent"), ""
                )
            }

            if (encSignatures.size() > 0) {
                fetchAndProcessSignatures(encSignatures, ytFilesResult)
            }

        } catch (e: Exception) {
            if (LOGGING) Log.e(LOG_TAG, "Erro InnerTube: ${e.message}")
            return null
        }
        return if (ytFilesResult.size() > 0) ytFilesResult else null
    }

    private fun fetchAndProcessSignatures(encSignatures: SparseArray<String>, ytFiles: SparseArray<YtFile>) {
        try {
            val watchUrl = URL("https://youtube.com/watch?v=$videoID")
            val html = watchUrl.openConnection().inputStream.bufferedReader().use { it.readText() }
            val matJs = Pattern.compile("/s/player/[a-zA-Z0-9_-]+?/player_ias\\.vflset/[a-zA-Z0-9_-]+?/(?:base|embed)\\.js").matcher(html)
            
            if (matJs.find()) {
                decipherJsFileName = matJs.group(0)
                decipherSignature(encSignatures)
                
                lock.lock()
                try { jsExecuting.await(7, TimeUnit.SECONDS) } finally { lock.unlock() }

                decipheredSignature?.let { sigStr ->
                    val sigs = sigStr.split("\n")
                    for (i in 0 until encSignatures.size()) {
                        val key = encSignatures.keyAt(i)
                        if (i < sigs.size) {
                            val file = ytFiles[key]
                            ytFiles.put(key, YtFile(file.meta, file.url + "&sig=${sigs[i]}"))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(LOG_TAG, "Erro Signatures: ${e.message}") }
    }

    private fun decipherSignature(encSignatures: SparseArray<String>) {
        try {
            val jsUrl = "https://youtube.com$decipherJsFileName"
            val jsContent = URL(jsUrl).openConnection().apply {
                setRequestProperty("User-Agent", USER_AGENT)
            }.getInputStream().bufferedReader().use { it.readText() }

            val mat = patSignatureDecFunction.matcher(jsContent)
            if (mat.find()) {
                decipherFunctionName = mat.group(1)
                val patMainFunct = Pattern.compile("var\\s+${Pattern.quote(decipherFunctionName!!)}\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\);\\s*([a-zA-Z0-9$]{1,4})\\.")
                val matMain = patMainFunct.matcher(jsContent)
                if (matMain.find()) {
                    val helperObj = matMain.group(1)
                    val patHelper = Pattern.compile("var\\s+${Pattern.quote(helperObj!!)}\\s*=\\s*\\{(.+?)\\};", Pattern.DOTALL)
                    val matHelper = patHelper.matcher(jsContent)
                    if (matHelper.find()) {
                        decipherFunctions = "var $helperObj={${matHelper.group(1)}}; " +
                                "var $decipherFunctionName=function(a){a=a.split(\"\");${jsContent.substring(matMain.end() - 1, jsContent.indexOf("};", matMain.end()) + 1)};"
                        decipherViaWebView(encSignatures)
                    }
                }
            }
        } catch (e: Exception) { Log.e(LOG_TAG, "Erro JS: ${e.message}") }
    }

    private fun decipherViaWebView(encSignatures: SparseArray<String>) {
        val context = refContext.get() ?: return
        val stb = StringBuilder(decipherFunctions ?: "")
        stb.append(" function decipher(){ return ")
        for (i in 0 until encSignatures.size()) {
            val key = encSignatures.keyAt(i)
            stb.append(decipherFunctionName).append("('").append(encSignatures[key]).append("')")
            if (i < encSignatures.size() - 1) stb.append(" + '\\n' + ")
        }
        stb.append(" }; decipher();")

        Handler(Looper.getMainLooper()).post {
            JsEvaluator(context).evaluate(stb.toString(), object : JsCallback {
                override fun onResult(result: String) {
                    lock.lock()
                    try {
                        decipheredSignature = result
                        jsExecuting.signal()
                    } finally { lock.unlock() }
                }
                override fun onError(e: String) {
                    lock.lock()
                    try { jsExecuting.signal() } finally { lock.unlock() }
                }
            })
        }
    }

    suspend fun extract(videoId: String) = withContext(Dispatchers.IO) {
        state = Constant.Status.RUNNING
        videoID = videoId
        val result = getStreamUrls()
        if (result != null) {
            ytFiles = result
            state = Constant.Status.FINISHED
        } else {
            state = Constant.Status.ERROR
        }
    }
}
