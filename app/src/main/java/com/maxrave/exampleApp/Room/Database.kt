package com.maxrave.exampleApp.Room

import android.content.Context
import androidx.room.*

@Database(
    entities = [Playlist::class, SongEntity::class, PlaylistSongCrossRef::class, FavoriteEntity::class], 
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "max_rave_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
