package com.vrpirate.installeradb.service

import com.vrpirate.installeradb.adb.AdbManager
import com.vrpirate.installeradb.utils.FileUtils
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Копирование кэша через ADB
 */
class CacheCopier(private val adbManager: AdbManager) {
    
    /**
     * Скопировать папку кэша в Android/obb
     */
    suspend fun copyCache(
        cacheFolder: File,
        packageName: String,
        onProgress: (Int, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        
        if (!adbManager.isConnected()) {
            Logger.e("Cannot copy cache: ADB not connected")
            return@withContext false
        }
        
        if (!cacheFolder.exists() || !cacheFolder.isDirectory) {
            Logger.e("Cache folder does not exist: ${cacheFolder.path}")
            return@withContext false
        }
        
        try {
            onProgress(0, "Подготовка к копированию кэша...")
            Logger.i("Copying cache: ${cacheFolder.path} -> /sdcard/Android/obb/$packageName")
            
            val destPath = "/sdcard/Android/obb/$packageName"
            
            // Создаём целевую папку
            adbManager.executeShellCommand("mkdir -p $destPath")
            
            // Копируем папку целиком
            val success = adbManager.push(
                sourcePath = cacheFolder.absolutePath,
                destPath = destPath,
                onProgress = { progress ->
                    onProgress(progress, "Копирование файлов...")
                }
            )
            
            if (success) {
                // Проверяем целостность
                val isValid = verifyCacheIntegrity(cacheFolder, destPath, onProgress)
                
                if (isValid) {
                    Logger.i("Cache copied and verified successfully")
                    onProgress(100, "Кэш скопирован")
                    true
                } else {
                    Logger.e("Cache integrity check failed")
                    false
                }
            } else {
                Logger.e("Cache copy failed")
                false
            }
            
        } catch (e: Exception) {
            Logger.e("Cache copy error", e)
            false
        }
    }
    
    /**
     * Проверить целостность скопированного кэша
     */
    private suspend fun verifyCacheIntegrity(
        sourceFolder: File,
        destPath: String,
        onProgress: (Int, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(95, "Проверка целостности...")
            
            // Подсчёт файлов в источнике
            val sourceFileCount = FileUtils.countFiles(sourceFolder)
            val sourceSize = FileUtils.getFolderSize(sourceFolder)
            
            // Получаем список файлов в целевой папке
            val destFileList = adbManager.executeShellCommand("find $destPath -type f | wc -l")
            val destFileCount = destFileList.trim().toIntOrNull() ?: 0
            
            // Получаем размер целевой папки
            val destSizeOutput = adbManager.executeShellCommand("du -sb $destPath")
            val destSize = destSizeOutput.split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0
            
            Logger.i("Cache verification - Source: $sourceFileCount files, ${sourceSize}B | Dest: $destFileCount files, ${destSize}B")
            
            // Проверяем соответствие
            val filesMatch = sourceFileCount == destFileCount
            val sizesMatch = (sourceSize - destSize).let { diff ->
                // Допускаем небольшую разницу (до 1%)
                kotlin.math.abs(diff) < sourceSize * 0.01
            }
            
            if (filesMatch && sizesMatch) {
                Logger.i("Cache integrity verified successfully")
                true
            } else {
                Logger.e("Cache integrity check failed. Files: $filesMatch, Size: $sizesMatch")
                false
            }
            
        } catch (e: Exception) {
            Logger.e("Cache verification error", e)
            false
        }
    }
    
    /**
     * Удалить кэш из Android/obb
     */
    suspend fun deleteCache(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destPath = "/sdcard/Android/obb/$packageName"
            val result = adbManager.executeShellCommand("rm -rf $destPath")
            
            Logger.i("Cache deleted: $destPath")
            true
        } catch (e: Exception) {
            Logger.e("Failed to delete cache", e)
            false
        }
    }
    
    /**
     * Проверить существование кэша
     */
    suspend fun cacheExists(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destPath = "/sdcard/Android/obb/$packageName"
            val result = adbManager.executeShellCommand("[ -d $destPath ] && echo 'exists' || echo 'not found'")
            result.contains("exists")
        } catch (e: Exception) {
            false
        }
    }
}
