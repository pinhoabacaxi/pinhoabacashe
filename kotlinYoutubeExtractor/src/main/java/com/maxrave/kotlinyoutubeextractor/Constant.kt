package com.maxrave.kotlinyoutubeextractor

/**
 * Objeto de constantes globais para o estado da biblioteca.
 * Alterado para 'object' para facilitar o acesso estático em todo o projeto.
 */
object Constant {
    enum class Status {
        PENDING,
        RUNNING,
        FINISHED,
        ERROR // Adicionado para sincronizar com a lógica de tratamento de falhas do Extrator
    }
}
object YTConstants {
    const val ANDROID_MUSIC_CLIENT = "ANDROID_MUSIC"
    const val ANDROID_VR_CLIENT = "ANDROID_VR" // Útil para pular certas restrições
    const val WEB_REMIX_CLIENT = "WEB_REMIX"   // Versão YouTube Music Web
}
