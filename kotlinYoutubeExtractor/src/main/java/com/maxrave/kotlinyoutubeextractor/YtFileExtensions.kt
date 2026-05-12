package com.maxrave.kotlinyoutubeextractor

import android.util.SparseArray
import androidx.core.util.forEach

/**
 * Retorna uma lista de objetos YtFile que contêm apenas áudio.
 */
fun SparseArray<YtFile>.getAudioOnly(): List<YtFile> {
    val resultList = mutableListOf<YtFile>()
    // 140: M4A, 171: WebM, 249-251: Opus
    val listAudioItag = listOf(140, 171, 249, 250, 251)
    
    for (itag in listAudioItag) {
        val file = this.get(itag)
        if (file != null) {
            resultList.add(file)
        }
    }
    return resultList
}

/**
 * Retorna uma lista de objetos YtFile que contêm vídeo.
 */
fun SparseArray<YtFile>.getVideoOnly(): List<YtFile> {
    val resultList = mutableListOf<YtFile>()
    val listVideoItag = listOf(
        18, 22, 37, 38, 82, 83, 84, 85, 133, 134, 135, 136, 137, 138, 160, 
        242, 243, 244, 247, 248, 264, 266, 271, 272, 278, 298, 299, 302, 
        303, 308, 313, 315, 330, 331, 332, 333, 334, 335, 336, 337, 394, 
        395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410
    )
    
    for (itag in listVideoItag) {
        val file = this.get(itag)
        if (file != null) {
            resultList.add(file)
        }
    }
    return resultList
}

/**
 * Converte o SparseArray nativo para uma List comum do Kotlin.
 */
fun <T> SparseArray<T>.values(): List<T> {
    val list = mutableListOf<T>()
    this.forEach { _, value ->
        list.add(value)
    }
    return list
}

/**
 * Retorna o ficheiro com a melhor qualidade de áudio baseada no bitrate.
 */
fun List<YtFile>.bestQuality(): YtFile? {
    return this.maxByOrNull { it.meta?.audioBitrate ?: 0 }
}

/**
 * Verifica se o SparseArray está vazio.
 */
fun <T> SparseArray<T>.isEmpty(): Boolean {
    return this.size() == 0
}
/**
 * Retorna o ficheiro com a melhor qualidade de áudio baseada no bitrate.
 * Adicionada verificação de nulidade segura para o campo meta.
 */
fun List<YtFile>.bestQuality(): YtFile? {
    return this.maxByOrNull { it.meta?.audioBitrate ?: 0 }
}
