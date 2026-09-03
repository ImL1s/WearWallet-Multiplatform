package com.cbstudio.wearwallet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * 圖片緩存管理器
 * 
 * 為 Wear OS 優化的輕量級圖片緩存系統
 */
@Singleton
class ImageCache(
    private val context: Context
) {
    companion object {
        private const val TAG = "ImageCache"
        private const val MEMORY_CACHE_SIZE = 4 * 1024 * 1024 // 4MB
        private const val DISK_CACHE_SIZE = 10 * 1024 * 1024L // 10MB
        private const val MAX_IMAGE_SIZE = 512 // 512px max dimension
    }
    
    // 內存緩存
    private val memoryCache = LruCache<String, Bitmap>(MEMORY_CACHE_SIZE)
    
    // 磁盤緩存目錄
    private val diskCacheDir = File(context.cacheDir, "image_cache").apply {
        if (!exists()) mkdirs()
    }
    
    /**
     * 從緩存獲取 Bitmap
     */
    fun getBitmap(url: String): Bitmap? {
        // 先檢查內存緩存
        val memoryBitmap = memoryCache.get(url)
        if (memoryBitmap != null) {
            Logger.d(TAG, "從內存緩存載入: $url")
            return memoryBitmap
        }
        
        // 檢查磁盤緩存
        val diskBitmap = loadFromDisk(url)
        if (diskBitmap != null) {
            Logger.d(TAG, "從磁盤緩存載入: $url")
            memoryCache.put(url, diskBitmap)
            return diskBitmap
        }
        
        return null
    }
    
    /**
     * 從網絡載入 Bitmap
     */
    suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 檢查是否已緩存
            getBitmap(url)?.let { return@withContext it }
            
            Logger.d(TAG, "從網絡載入圖片: $url")
            
            // 從網絡下載
            val inputStream = URL(url).openStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                // 縮放圖片以節省記憶體
                val scaledBitmap = scaleBitmap(bitmap)
                
                // 保存到緩存
                saveToDisk(url, scaledBitmap)
                memoryCache.put(url, scaledBitmap)
                
                Logger.d(TAG, "圖片載入完成: $url")
                scaledBitmap
            } else {
                Logger.w(TAG, "無法解碼圖片: $url")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "載入圖片失敗: $url", e)
            null
        }
    }
    
    /**
     * 縮放 Bitmap
     */
    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        
        val ratio = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 從磁盤載入
     */
    private fun loadFromDisk(url: String): Bitmap? {
        return try {
            val cacheFile = getCacheFile(url)
            if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "從磁盤載入失敗: $url", e)
            null
        }
    }
    
    /**
     * 保存到磁盤
     */
    private fun saveToDisk(url: String, bitmap: Bitmap) {
        try {
            val cacheFile = getCacheFile(url)
            val fos = FileOutputStream(cacheFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
            
            // 清理舊的緩存文件
            cleanupOldCache()
        } catch (e: Exception) {
            Logger.e(TAG, "保存到磁盤失敗: $url", e)
        }
    }
    
    /**
     * 獲取緩存文件
     */
    private fun getCacheFile(url: String): File {
        val filename = generateCacheKey(url)
        return File(diskCacheDir, filename)
    }
    
    /**
     * 生成緩存鍵
     */
    private fun generateCacheKey(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(url.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }
    
    /**
     * 清理舊的緩存
     */
    private fun cleanupOldCache() {
        try {
            val files = diskCacheDir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }
            
            if (totalSize > DISK_CACHE_SIZE) {
                // 按修改時間排序，刪除最舊的文件
                files.sortedBy { it.lastModified() }
                    .take((files.size * 0.3).toInt()) // 刪除 30% 最舊的文件
                    .forEach { it.delete() }
                
                Logger.d(TAG, "清理舊緩存完成")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "清理緩存失敗", e)
        }
    }
    
    /**
     * 清除所有緩存
     */
    fun clearCache() {
        try {
            memoryCache.evictAll()
            diskCacheDir.listFiles()?.forEach { it.delete() }
            Logger.d(TAG, "所有緩存已清除")
        } catch (e: Exception) {
            Logger.e(TAG, "清除緩存失敗", e)
        }
    }
}
