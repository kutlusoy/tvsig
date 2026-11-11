package com.kutlusoy.tvsig.utils

import android.content.Context
import com.kutlusoy.tvsig.repositories.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheManager private constructor(private val context: Context) {

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            MediaRepository.getInstance(context).clearLocalMedia()
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            Logger.d("Cache cleared")
        } catch (e: Exception) {
            Logger.e("Error clearing cache", e)
        }
    }

    companion object {
        @Volatile
        private var instance: CacheManager? = null

        fun getInstance(context: Context): CacheManager =
            instance ?: synchronized(this) {
                instance ?: CacheManager(context.applicationContext).also { instance = it }
            }
    }
}
