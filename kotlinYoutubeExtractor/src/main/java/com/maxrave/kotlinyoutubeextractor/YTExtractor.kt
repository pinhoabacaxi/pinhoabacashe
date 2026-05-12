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

    // Patterns de extração
    private val patPlayerResponse = Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});")
    private val patPlayerResponseAlternative = Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});")
    private val patPlayerResponseEmbedded = Pattern.compile("window\\[\"ytInitialPlayerResponse\"\\]\\s*=\\s*(\\{.+?\\});")
    
    private val patSigEncUrl = Pattern.compile("url=(.+?)(\\u0026|$)")
    private val patSignature = Pattern.compile("s=(.+?)(\\u0026|$)")
    
    private val patSignatureDecFunction = Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{1,4})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")

    private val FORMAT_MAP = SparseArray<Format>()

    init {
        setupFormatMap()
    }

    private fun setupFormatMap() {
        // Formatos MP4 / 3GP
        FORMAT_MAP.put(17, Format(17, "3gp", 144, Format.VCodec.MPEG4, Format.ACodec.AAC, false))
        FORMAT_MAP.put(18, Format(18, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, false))
        FORMAT_MAP.put(22, Format(22, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, false))
        
        // Dash Audio (Os mais importantes para players de música)
        FORMAT_MAP.put(140, Format(140, "m4a", -1, Format.VCodec.NONE, Format.ACodec.AAC, 128, true))
        FORMAT_MAP.put(249, Format(249, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 50, true))
        FORMAT_MAP.put(250, Format(250, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 70, true))
        FORMAT_MAP.put(251, Format(251, "webm", -1, Format.VCodec.NONE, Format.ACodec.OPUS, 160, true))
        
        // Dash Video
        FORMAT_MAP.put(134, Format(134, "mp4", 360, Format.VCodec.H264, Format.ACodec.NONE, true))
        FORMAT_MAP.put(135, Format(135, "mp4", 480, Format.VCodec.H264, Format.ACodec.NONE, true))
        FORMAT_MAP.put(136, Format(136, "mp4", 720, Format.VCodec.H264, Format.ACodec.NONE, true))
        FORMAT_MAP.put(137, Format(137, "mp4", 1080, Format.VCodec.H264, Format.ACodec.NONE, true))
    }

    private fun getStreamUrls(): SparseArray<YtFile>? {
        var pageHtml = ""
        val encSignatures = SparseArray<String>()
        val ytFilesResult = SparseArray<YtFile>()

        try {
            val getUrl = URL("https://www.youtube.com/watch?v=$videoID&has_verified=1&bpctr=9999999999&el=embedded")
            val urlConnection = getUrl.openConnection() as HttpURLConnection
    
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
            urlConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            urlConnection.setRequestProperty("Cookie", "CONSENT=PENDING+999; YES+cb.20230531-04-p0.en+FX+999")
            urlConnection.inputStream.bufferedReader().use { pageHtml = it.readText() }
            urlConnection.disconnect()

            var jsonStr: String? = null
            listOf(patPlayerResponse, patPlayerResponseAlternative, patPlayerResponseEmbedded).forEach { pat ->
                val mat = pat.matcher(pageHtml)
                if (mat.find()) jsonStr = mat.group(1)
            }

            if (jsonStr == null) return null

            val ytPlayerResponse = JSONObject(jsonStr!!)
            val streamingData = ytPlayerResponse.optJSONObject("streamingData") ?: return null

            val allFormats = mutableListOf<JSONObject>()
            streamingData.optJSONArray("formats")?.let { for(i in 0 until it.length()) allFormats.add(it.getJSONObject(i)) }
            streamingData.optJSONArray("adaptiveFormats")?.let { for(i in 0 until it.length()) allFormats.add(it.getJSONObject(i)) }

            for (formatJson in allFormats) {
                val itag = formatJson.getInt("itag")
                if (FORMAT_MAP[itag] != null) {
                    if (formatJson.has("url")) {
                        val url = formatJson.getString("url").replace("\\u0026", "&")
                        ytFilesResult.put(itag, YtFile(FORMAT_MAP[itag], url))
                    } else if (formatJson.has("signatureCipher") || formatJson.has("cipher")) {
                        val cipher = formatJson.getString(if (formatJson.has("signatureCipher")) "signatureCipher" else "cipher")
                        val matUrl = patSigEncUrl.matcher(cipher)
                        val matSig = patSignature.matcher(cipher)
                        
                        if (matUrl.find() && matSig.find()) {
                            val url = URLDecoder.decode(matUrl.group(1), "UTF-8")
                            val signature = URLDecoder.decode(matSig.group(1), "UTF-8")
                            ytFilesResult.put(itag, YtFile(FORMAT_MAP[itag], url))
                            encSignatures.put(itag, signature)
                        }
                    }
                }
            }

            // Extração de Meta
            ytPlayerResponse.optJSONObject("videoDetails")?.let { details ->
                videoMeta = VideoMeta(
                    details.optString("videoId"),
                    details.optString("title"),
                    details.optString("author"),
                    details.optString("channelId"),
                    details.optString("lengthSeconds", "0").toLong(),
                    details.optString("viewCount", "0").toLong(),
                    details.optBoolean("isLiveContent"),
                    details.optString("shortDescription", "")
                )
            }

            if (encSignatures.size() > 0) {
                processSignatures(pageHtml, encSignatures, ytFilesResult)
            }

        } catch (e: Exception) {
            if (LOGGING) Log.e(LOG_TAG, "Erro na extração: ${e.message}")
            return null
        }
        return if (ytFilesResult.size() > 0) ytFilesResult else null
    }

    private fun processSignatures(pageHtml: String, encSignatures: SparseArray<String>, ytFiles: SparseArray<YtFile>) {
        val matJs = Pattern.compile("/s/player/([a-zA-Z0-9_-]+?)/player_ias\\.vflset/[a-zA-Z0-9_-]+?/(?:base|embed)\\.js").matcher(pageHtml)
        if (matJs.find()) {
            decipherJsFileName = matJs.group(0)
            decipherSignature(encSignatures)

            lock.lock()
            try {
                jsExecuting.await(7, TimeUnit.SECONDS)
            } finally {
                lock.unlock()
            }

            decipheredSignature?.let { sigStr ->
                val sigs = sigStr.split("\n")
                for (i in 0 until encSignatures.size()) {
                    val key = encSignatures.keyAt(i)
                    if (i < sigs.size) {
                        val originalFile = ytFiles[key]
                        val decipheredUrl = originalFile.url + "&sig=${sigs[i]}"
                        ytFiles.put(key, YtFile(originalFile.meta, decipheredUrl))
                    }
                }
            }
        }
    }

    private fun decipherSignature(encSignatures: SparseArray<String>) {
        try {
            val jsUrl = "https://www.youtube.com$decipherJsFileName"
            val jsContent = URL(jsUrl).openConnection().apply {
                setRequestProperty("User-Agent", USER_AGENT)
            }.getInputStream().bufferedReader().use { it.readText() }

            val mat = patSignatureDecFunction.matcher(jsContent)
            if (mat.find()) {
                decipherFunctionName = mat.group(1)
                
                // Extração robusta do objeto de auxílio (ex: var fO = { ... })
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
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro ao baixar JS: ${e.message}")
        }
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
                    Log.e(LOG_TAG, "JS Error: $e")
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
