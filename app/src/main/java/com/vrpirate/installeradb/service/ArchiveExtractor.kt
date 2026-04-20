package com.vrpirate.installeradb.service

import com.vrpirate.installeradb.data.model.ArchiveItem
import com.vrpirate.installeradb.data.model.ArchiveType
import com.vrpirate.installeradb.utils.FileUtils
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Извлечение архивов (ZIP, 7Z, RAR)
 */
class ArchiveExtractor {
    
    @Volatile
    private var isCancelled = false
    
    /**
     * Распаковать архив
     * @param archive архив для распаковки
     * @param onProgress callback прогресса (0-100)
     * @return папка с распакованными файлами или null при ошибке
     */
    suspend fun extract(
        archive: ArchiveItem,
        onProgress: (Int, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        isCancelled = false
        
        try {
            val extractFolder = FileUtils.getExtractFolder()
            val targetFolder = File(extractFolder, archive.name)
            
            // Удаляем если уже существует
            if (targetFolder.exists()) {
                FileUtils.deleteRecursively(targetFolder)
            }
            targetFolder.mkdirs()
            
            Logger.i("Extracting ${archive.name} to ${targetFolder.path}")
            
            // Проверка свободного места (архив × 2.5)
            val requiredSpace = archive.totalSize * 2.5
            if (!FileUtils.hasEnoughSpace(requiredSpace.toLong())) {
                Logger.e("Not enough space. Required: $requiredSpace, Available: ${FileUtils.getAvailableSpace()}")
                return@withContext null
            }
            
            val result = when (archive.type) {
                ArchiveType.ZIP -> extractZip(archive, targetFolder, onProgress)
                ArchiveType.SEVEN_Z -> extract7z(archive, targetFolder, onProgress)
                ArchiveType.RAR -> extractRar(archive, targetFolder, onProgress)
                ArchiveType.APK -> {
                    // APK не распаковываем, просто копируем
                    FileUtils.copyFile(archive.file, File(targetFolder, archive.file.name))
                    true
                }
            }
            
            if (result && !isCancelled) {
                Logger.i("Extraction completed: ${archive.name}")
                targetFolder
            } else {
                Logger.e("Extraction failed or cancelled: ${archive.name}")
                FileUtils.deleteRecursively(targetFolder)
                null
            }
            
        } catch (e: Exception) {
            Logger.e("Extraction error: ${archive.name}", e)
            null
        }
    }
    
    /**
     * Распаковать ZIP архив
     */
    private fun extractZip(
        archive: ArchiveItem,
        targetFolder: File,
        onProgress: (Int, String) -> Unit
    ): Boolean {
        return try {
            val sourceFile = if (archive.isMultipart) {
                // Для многотомных ZIP нужно объединить части
                concatenateZipParts(archive)
            } else {
                archive.file
            }
            
            var totalSize = 0L
            var extractedSize = 0L
            
            // Первый проход - подсчёт общего размера
            ZipInputStream(FileInputStream(sourceFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        totalSize += entry.size
                    }
                    entry = zis.nextEntry
                }
            }
            
            // Второй проход - извлечение
            ZipInputStream(FileInputStream(sourceFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                
                while (entry != null && !isCancelled) {
                    val entryFile = File(targetFolder, entry.name)
                    
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        
                        FileOutputStream(entryFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                if (isCancelled) break
                                fos.write(buffer, 0, len)
                                extractedSize += len
                                
                                if (totalSize > 0) {
                                    val progress = ((extractedSize * 100) / totalSize).toInt()
                                    onProgress(progress, entry.name)
                                }
                            }
                        }
                    }
                    
                    entry = zis.nextEntry
                }
            }
            
            // Удаляем временный объединённый файл если создавали
            if (archive.isMultipart && sourceFile != archive.file) {
                sourceFile.delete()
            }
            
            !isCancelled
            
        } catch (e: Exception) {
            Logger.e("ZIP extraction error", e)
            false
        }
    }
    
    /**
     * Распаковать 7Z архив
     */
    private fun extract7z(
        archive: ArchiveItem,
        targetFolder: File,
        onProgress: (Int, String) -> Unit
    ): Boolean {
        return try {
            val sourceFile = if (archive.isMultipart) {
                concatenate7zParts(archive)
            } else {
                archive.file
            }
            
            SevenZFile(sourceFile).use { sevenZFile ->
                var totalSize = 0L
                var extractedSize = 0L
                
                // Подсчёт общего размера
                var entry = sevenZFile.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        totalSize += entry.size
                    }
                    entry = sevenZFile.nextEntry
                }
                
                // Сброс для второго прохода
                sevenZFile.close()
                SevenZFile(sourceFile).use { sevenZFile2 ->
                    entry = sevenZFile2.nextEntry
                    
                    while (entry != null && !isCancelled) {
                        val entryFile = File(targetFolder, entry.name)
                        
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            
                            FileOutputStream(entryFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (sevenZFile2.read(buffer).also { len = it } > 0) {
                                    if (isCancelled) break
                                    fos.write(buffer, 0, len)
                                    extractedSize += len
                                    
                                    if (totalSize > 0) {
                                        val progress = ((extractedSize * 100) / totalSize).toInt()
                                        onProgress(progress, entry.name)
                                    }
                                }
                            }
                        }
                        
                        entry = sevenZFile2.nextEntry
                    }
                }
            }
            
            if (archive.isMultipart && sourceFile != archive.file) {
                sourceFile.delete()
            }
            
            !isCancelled
            
        } catch (e: Exception) {
            Logger.e("7Z extraction error", e)
            false
        }
    }
    
    /**
     * Распаковать RAR архив
     */
    private fun extractRar(
        archive: ArchiveItem,
        targetFolder: File,
        onProgress: (Int, String) -> Unit
    ): Boolean {
        return try {
            val sourceFile = if (archive.isMultipart) {
                // Для многотомных RAR используем первый файл
                archive.parts.first()
            } else {
                archive.file
            }
            
            val rarArchive = com.github.junrar.Archive(sourceFile)
            
            var totalSize = 0L
            var extractedSize = 0L
            
            // Подсчёт общего размера
            val headers = rarArchive.fileHeaders
            headers.forEach { header ->
                if (!header.isDirectory) {
                    totalSize += header.fullUnpackSize
                }
            }
            
            // Извлечение
            headers.forEach { header ->
                if (isCancelled) return@forEach
                
                val entryFile = File(targetFolder, header.fileName)
                
                if (header.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    
                    FileOutputStream(entryFile).use { fos ->
                        rarArchive.extractFile(header, fos)
                        
                        extractedSize += header.fullUnpackSize
                        
                        if (totalSize > 0) {
                            val progress = ((extractedSize * 100) / totalSize).toInt()
                            onProgress(progress, header.fileName)
                        }
                    }
                }
            }
            
            rarArchive.close()
            
            !isCancelled
            
        } catch (e: Exception) {
            Logger.e("RAR extraction error", e)
            false
        }
    }
    
    /**
     * Объединить части многотомного ZIP архива
     */
    private fun concatenateZipParts(archive: ArchiveItem): File {
        val tempFile = File.createTempFile("vrpirate_zip_", ".zip")
        
        FileOutputStream(tempFile).use { output ->
            archive.parts.forEach { part ->
                FileInputStream(part).use { input ->
                    input.copyTo(output)
                }
            }
        }
        
        return tempFile
    }
    
    /**
     * Объединить части многотомного 7Z архива
     */
    private fun concatenate7zParts(archive: ArchiveItem): File {
        val tempFile = File.createTempFile("vrpirate_7z_", ".7z")
        
        FileOutputStream(tempFile).use { output ->
            archive.parts.forEach { part ->
                FileInputStream(part).use { input ->
                    input.copyTo(output)
                }
            }
        }
        
        return tempFile
    }
    
    /**
     * Отменить распаковку
     */
    fun cancel() {
        isCancelled = true
        Logger.i("Archive extraction cancelled")
    }
}
