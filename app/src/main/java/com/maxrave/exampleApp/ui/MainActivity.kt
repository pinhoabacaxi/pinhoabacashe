[span_16](start_span)package com.maxrave.exampleApp.ui[span_16](end_span)

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.maxrave.exampleApp.R
[span_17](start_span)import com.maxrave.kotlinyoutubeextractor.State[span_17](end_span)
[span_18](start_span)import com.maxrave.kotlinyoutubeextractor.YTExtractor[span_18](end_span)
[span_19](start_span)import com.maxrave.kotlinyoutubeextractor.bestQuality[span_19](end_span)
[span_20](start_span)import com.maxrave.kotlinyoutubeextractor.getAudioOnly[span_20](end_span)
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        setupPlayer()
        loadMusicResources()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        // O player cuidará da playlist e do áudio em segundo plano
    }

    private fun loadMusicResources() {
        [span_21](start_span)val listVideoId = listOf("d40rzwlq8l4", "Q2T8-q9fGSI")[span_21](end_span)
        [span_22](start_span)val yt = YTExtractor(con = this, CACHING = false, LOGGING = true, retryCount = 3)[span_22](end_span)

        scope.launch {
            listVideoId.forEach { videoId ->
                withContext(Dispatchers.IO) {
                    [span_23](start_span)yt.extract(videoId)[span_23](end_span)
                }
                
                [span_24](start_span)if (yt.state == State.SUCCESS) {[span_24](end_span)
                    [span_25](start_span)[span_26](start_span)val audioUrl = yt.getYTFiles()?.getAudioOnly()?.bestQuality()?.url[span_25](end_span)[span_26](end_span)
                    audioUrl?.let { url ->
                        val mediaItem = MediaItem.fromUri(url)
                        [span_27](start_span)player.addMediaItem(mediaItem) // Adiciona na playlist[span_27](end_span)
                    }
                }
            }
            player.prepare()
            player.play()
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        scope.cancel()
    }
}
