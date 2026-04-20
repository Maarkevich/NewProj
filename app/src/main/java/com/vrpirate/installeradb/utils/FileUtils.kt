package com.vrpirate.installeradb.utils

import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Утилиты для работы с файлами
 */
object FileUtils {
    
    /**
     * Получить папку Download/Telegram
     */
    fun getTelegramFolder(): File {
        return File(Environment.getExternalStorageDirectory(), "Download/Telegram")
    }
    
    /**
     * Получить папку для распаковки
     */
    fun getExtractFolder(): File {
        val folder = File(Environment.getExternalStorageDirectory(), "VRPirate/extracted")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }
    
    /**
     * Получить папку Android/obb
     */
    fun getObbFolder(): File {
        return File(Environment.getExternalStorageDirectory(), "Android/obb")
    }
    
    /**
     * Проверка свободного места
     * @param requiredBytes необходимое количество байт
     * @return true если места достаточно
     */
    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes >= requiredBytes
    }
    
    /**
     * Получить свободное место в байтах
     */
    fun getAvailableSpace(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }
    
    /**
     * Удалить папку рекурсивно
     */
    fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete()
        } catch (e: Exception) {
            Logger.e("Failed to delete ${file.path}", e)
            false
        }
    }
    
    /**
     * Получить размер папки
     */
    fun getFolderSize(folder: File): Long {
        var size = 0L
        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
    
    /**
     * Подсчитать количество файлов в папке
     */
    fun countFiles(folder: File): Int {
        var count = 0
        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
            }
        }
        return count
    }
    
    /**
     * Копировать файл
     */
    fun copyFile(source: File, dest: File): Boolean {
        return try {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Logger.e("Failed to copy ${source.path} to ${dest.path}", e)
            false
        }
    }
    
    /**
     * Найти APK файл в папке
     */
    fun findApkFile(folder: File): File? {
        return folder.walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }
    }
    
    /**
     * Найти папку кэша по package name
     */
    fun findCacheFolder(parentFolder: File, packageName: String): File? {
        return parentFolder.listFiles()
            ?.firstOrNull { it.isDirectory && it.name == packageName }
    }
}
