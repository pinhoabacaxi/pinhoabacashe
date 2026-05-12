package com.maxrave.kotlinyoutubeextractor

import android.util.SparseArray

/**
 * Filtra o SparseArray para retornar apenas formatos de áudio conhecidos.
 */
fun SparseArray<YtFile>.getAudioOnly(): List<YtFile> {
    val resultList = mutableListOf<YtFile>()
    // 140: M4A (128kbps), 171: WebM, 249-251: Opus (Alta qualidade) 
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
 * Retorna o arquivo de melhor qualidade da lista baseando-se no bitrate.
 */
fun List<YtFile>.bestQuality(): YtFile? {
    return this.maxByOrNull { it.meta?.audioBitrate ?: 0 }
}

/**
 * Extensão utilitária para verificar o tamanho do SparseArray com segurança.
 */
fun <T> SparseArray<T>.isEmpty(): Boolean {
    return this.size() == 0
}
