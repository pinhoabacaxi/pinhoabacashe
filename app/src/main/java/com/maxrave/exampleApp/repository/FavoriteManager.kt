package com.maxrave.exampleApp.repository

import android.content.Context
import android.content.SharedPreferences

class FavoriteManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fav_prefs", Context.MODE_PRIVATE)

    fun toggleFavorite(songId: Long) {
        val favorites = getFavorites().toMutableSet()
        val idStr = songId.toString()
        if (favorites.contains(idStr)) favorites.remove(idStr)
        else favorites.add(idStr)
        prefs.edit().putStringSet("fav_list", favorites).apply()
    }

    fun isFavorite(songId: Long): Boolean {
        return getFavorites().contains(songId.toString())
    }

    private fun getFavorites(): Set<String> {
        return prefs.getStringSet("fav_list", emptySet()) ?: emptySet()
    }
}
