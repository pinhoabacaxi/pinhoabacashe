package com.maxrave.kotlinyoutubeextractor

/**
 * Representa os diferentes estados da tela de busca online.
 * Usamos List<Any> no Success para suportar VideoMeta e YouTubePlaylist simultaneamente.
 */
sealed class SearchState {
    
    // Estado inicial: quando o usuário ainda não digitou nada
    object Idle : SearchState()
    
    // Estado de progresso: enquanto a requisição de rede está ativa
    object Loading : SearchState()
    
    // Estado de sucesso: contém a lista unificada de resultados
    data class Success(val results: List<Any>) : SearchState()
    
    // Estado de erro: contém a mensagem amigável para o usuário
    data class Error(val message: String) : SearchState()
}
