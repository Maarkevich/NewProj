package com.vrpirate.installeradb.data.local

import com.vrpirate.installeradb.data.model.ArchiveItem
import com.vrpirate.installeradb.data.model.ArchiveType
import com.vrpirate.installeradb.utils.FileUtils
import com.vrpirate.installeradb.utils.Logger
import java.io.File
import java.util.*

/**
 * Сканер архивов в папке Download/Telegram
 */
class FileScanner {
    
    /**
     * Сканировать папку и найти все архивы и APK
     */
    fun scanTelegramFolder(): List<ArchiveItem> {
        val telegramFolder = FileUtils.getTelegramFolder()
        
        if (!telegramFolder.exists()) {
            Logger.i("Telegram folder does not exist: ${telegramFolder.path}")
            return emptyList()
        }
        
        val archives = mutableListOf<ArchiveItem>()
        val files = telegramFolder.listFiles() ?: return emptyList()
        
        // Группируем многотомные архивы
        val multipartGroups = mutableMapOf<String, MutableList<File>>()
        val singleFiles = mutableListOf<File>()
        
        for (file in files) {
            if (!file.isFile) continue
            
            val name = file.name.lowercase(Locale.getDefault())
            
            when {
                // ZIP многотомный
                name.matches(Regex(".*\\.zip\\.\\d{3}$")) -> {
                    val baseName = file.nameWithoutExtension.substringBeforeLast(".zip")
                    multipartGroups.getOrPut("$baseName.zip") { mutableListOf() }.add(file)
                }
                
                // 7Z многотомный
                name.matches(Regex(".*\\.7z\\.\\d{3}$")) || name.matches(Regex(".*\\.7z\\d{2}$")) -> {
                    val baseName = when {
                        name.matches(Regex(".*\\.7z\\.\\d{3}$")) -> 
                            file.nameWithoutExtension.substringBeforeLast(".7z")
                        else -> 
                            file.name.substring(0, file.name.length - 4) // Убираем .7z01
                    }
                    multipartGroups.getOrPut("$baseName.7z") { mutableListOf() }.add(file)
                }
                
                // RAR многотомный
                name.matches(Regex(".*\\.part\\d+\\.rar$")) -> {
                    val baseName = file.name.substringBeforeLast(".part")
                    multipartGroups.getOrPut("$baseName.rar") { mutableListOf() }.add(file)
                }
                
                // Одиночные файлы
                name.endsWith(".zip") || name.endsWith(".7z") || 
                name.endsWith(".rar") || name.endsWith(".apk") -> {
                    singleFiles.add(file)
                }
            }
        }
        
        // Создаём элементы для многотомных
        multipartGroups.forEach { (baseName, parts) ->
            val sortedParts = parts.sortedBy { it.name }
            val type = when {
                baseName.endsWith(".zip") -> ArchiveType.ZIP
                baseName.endsWith(".7z") -> ArchiveType.SEVEN_Z
                baseName.endsWith(".rar") -> ArchiveType.RAR
                else -> return@forEach
            }
            
            val totalSize = sortedParts.sumOf { it.length() }
            val displayName = baseName.substringBeforeLast(".")
            
            archives.add(
                ArchiveItem(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    file = sortedParts.first(),
                    type = type,
                    isMultipart = true,
                    parts = sortedParts,
                    totalSize = totalSize
                )
            )
        }
        
        // Создаём элементы для одиночных
        singleFiles.forEach { file ->
            val type = when (file.extension.lowercase(Locale.getDefault())) {
                "zip" -> ArchiveType.ZIP
                "7z" -> ArchiveType.SEVEN_Z
                "rar" -> ArchiveType.RAR
                "apk" -> ArchiveType.APK
                else -> return@forEach
            }
            
            archives.add(
                ArchiveItem(
                    id = UUID.randomUUID().toString(),
                    name = file.nameWithoutExtension,
                    file = file,
                    type = type,
                    isMultipart = false,
                    parts = emptyList(),
                    totalSize = file.length()
                )
            )
        }
        
        // Сортировка по имени (алфавитная, без учёта регистра)
        archives.sortBy { it.name.lowercase(Locale.getDefault()) }
        
        Logger.i("Found ${archives.size} archives/APKs in Telegram folder")
        return archives
    }
}
