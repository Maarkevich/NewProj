package com.vrpirate.installeradb.service

import android.content.Context
import com.vrpirate.installeradb.adb.AdbManager
import com.vrpirate.installeradb.data.model.ArchiveItem
import com.vrpirate.installeradb.data.model.ArchiveType
import com.vrpirate.installeradb.data.model.InstallStatus
import com.vrpirate.installeradb.utils.FileUtils
import com.vrpirate.installeradb.utils.Logger
import com.vrpirate.installeradb.utils.VibrationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Менеджер очереди установок
 */
class InstallQueue(private val context: Context) {
    
    private val queue = ConcurrentLinkedQueue<ArchiveItem>()
    private val archiveExtractor = ArchiveExtractor()
    private val apkInstaller = ApkInstaller(context)
    private val adbManager = AdbManager(context)
    private lateinit var cacheCopier: CacheCopier
    
    private var isProcessing = false
    private var currentJob: Job? = null
    
    private val _queueState = MutableStateFlow<List<ArchiveItem>>(emptyList())
    val queueState: StateFlow<List<ArchiveItem>> = _queueState
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        cacheCopier = CacheCopier(adbManager)
    }
    
    /**
     * Добавить в очередь
     */
    fun add(archive: ArchiveItem) {
        if (queue.none { it.id == archive.id }) {
            archive.status = InstallStatus.IN_QUEUE
            archive.queuePosition = queue.size + 1
            queue.add(archive)
            updateQueueState()
            
            Logger.i("Added to queue: ${archive.name}, position: ${archive.queuePosition}")
            
            // Запускаем обработку если не запущена
            if (!isProcessing) {
                startProcessing()
            }
        }
    }
    
    /**
     * Удалить из очереди
     */
    fun remove(archive: ArchiveItem) {
        queue.removeIf { it.id == archive.id }
        archive.status = InstallStatus.NEW
        archive.queuePosition = -1
        updateQueuePositions()
        updateQueueState()
        
        Logger.i("Removed from queue: ${archive.name}")
    }
    
    /**
     * Очистить очередь
     */
    fun clear() {
        queue.forEach {
            it.status = InstallStatus.NEW
            it.queuePosition = -1
        }
        queue.clear()
        updateQueueState()
        
        Logger.i("Queue cleared")
    }
    
    /**
     * Получить размер очереди
     */
    fun size(): Int = queue.size
    
    /**
     * Проверить, обрабатывается ли очередь
     */
    fun isProcessing(): Boolean = isProcessing
    
    /**
     * Запустить обработку очереди
     */
    private fun startProcessing() {
        if (isProcessing) return
        
        isProcessing = true
        Logger.i("Starting queue processing")
        
        currentJob = coroutineScope.launch {
            while (queue.isNotEmpty()) {
                val archive = queue.poll() ?: break
                
                try {
                    processArchive(archive)
                } catch (e: Exception) {
                    Logger.e("Failed to process archive: ${archive.name}", e)
                    archive.status = InstallStatus.ERROR
                    archive.errorMessage = e.message
                    VibrationHelper.vibrateError(context)
                }
                
                updateQueueState()
            }
            
            isProcessing = false
            Logger.i("Queue processing finished")
        }
    }
    
    /**
     * Остановить обработку
     */
    fun stop() {
        currentJob?.cancel()
        archiveExtractor.cancel()
        isProcessing = false
        
        Logger.i("Queue processing stopped")
    }
    
    /**
     * Обработать один архив
     */
    private suspend fun processArchive(archive: ArchiveItem) {
        Logger.i("Processing archive: ${archive.name}")
        
        var extractedFolder: File? = null
        var apkFile: File? = null
        var cacheFolder: File? = null
        var packageName: String? = null
        
        try {
            // 1. РАСПАКОВКА (если нужно)
            if (archive.type != ArchiveType.APK) {
                archive.status = InstallStatus.EXTRACTING
                updateQueueState()
                
                extractedFolder = archiveExtractor.extract(archive) { progress, fileName ->
                    archive.progress = progress
                    archive.progressText = "Распаковка $progress%"
                    updateQueueState()
                }
                
                if (extractedFolder == null) {
                    throw Exception("Ошибка распаковки")
                }
                
                // Находим APK
                apkFile = FileUtils.findApkFile(extractedFolder)
                if (apkFile == null) {
                    throw Exception("APK не найден в архиве")
                }
            } else {
                apkFile = archive.file
                extractedFolder = archive.file.parentFile
            }
            
            // 2. ПРОВЕРКА APK
            val apkInfo = apkInstaller.getApkInfo(apkFile)
                ?: throw Exception("Не удалось прочитать информацию из APK")
            
            packageName = apkInfo.packageName
            Logger.i("APK Info: $apkInfo")
            
            // Проверяем установленную версию
            val installedInfo = apkInstaller.getInstalledVersion(packageName)
            
            if (installedInfo != null) {
                val comparison = apkInstaller.compareVersions(
                    apkInfo.versionCode,
                    installedInfo.versionCode.toLong()
                )
                
                when (comparison) {
                    -1 -> Logger.i("Downgrade detected: ${installedInfo.versionCode} -> ${apkInfo.versionCode}")
                    0 -> Logger.i("Same version: ${apkInfo.versionCode}")
                    1 -> Logger.i("Upgrade detected: ${installedInfo.versionCode} -> ${apkInfo.versionCode}")
                }
                
                // Удаляем старую версию
                archive.progressText = "Удаление старой версии..."
                updateQueueState()
                
                apkInstaller.uninstallApp(packageName) { progress ->
                    archive.progressText = progress
                    updateQueueState()
                }
            }
            
            // Ищем папку кэша
            cacheFolder = extractedFolder?.let { 
                FileUtils.findCacheFolder(it, packageName)
            }
            
            // 3. УСТАНОВКА APK
            archive.status = InstallStatus.INSTALLING
            archive.progress = 0
            archive.progressText = "Установка APK..."
            updateQueueState()
            
            val installSuccess = apkInstaller.installApk(apkFile) { progress ->
                archive.progressText = progress
                updateQueueState()
            }
            
            if (!installSuccess) {
                throw Exception("Ошибка установки APK")
            }
            
            // Проверяем установку
            if (!apkInstaller.verifyInstallation(packageName)) {
                throw Exception("APK не установлен")
            }
            
            // 4. КОПИРОВАНИЕ КЭША (если есть и ADB подключен)
            if (cacheFolder != null) {
                if (adbManager.isConnected()) {
                    archive.status = InstallStatus.COPYING_CACHE
                    archive.progress = 0
                    updateQueueState()
                    
                    val cacheSuccess = cacheCopier.copyCache(
                        cacheFolder,
                        packageName
                    ) { progress, text ->
                        archive.progress = progress
                        archive.progressText = text
                        updateQueueState()
                    }
                    
                    if (!cacheSuccess) {
                        Logger.e("Cache copy failed, but APK is installed")
                    }
                } else {
                    Logger.i("ADB not connected, skipping cache copy")
                }
            }
            
            // 5. УСПЕХ
            archive.status = InstallStatus.SUCCESS
            archive.progress = 100
            archive.progressText = "Установлено"
            updateQueueState()
            
            VibrationHelper.vibrateSuccess(context)
            Logger.i("Archive processed successfully: ${archive.name}")
            
            // Удаляем распакованную папку
            if (extractedFolder != null && extractedFolder != archive.file.parentFile) {
                withContext(Dispatchers.IO) {
                    FileUtils.deleteRecursively(extractedFolder)
                }
            }
            
        } catch (e: Exception) {
            archive.status = InstallStatus.ERROR
            archive.errorMessage = e.message
            archive.progressText = "Ошибка: ${e.message}"
            updateQueueState()
            
            VibrationHelper.vibrateError(context)
            Logger.e("Archive processing error: ${archive.name}", e)
            
            // Очищаем при ошибке
            if (extractedFolder != null) {
                FileUtils.deleteRecursively(extractedFolder)
            }
        }
    }
    
    /**
     * Обновить позиции в очереди
     */
    private fun updateQueuePositions() {
        queue.forEachIndexed { index, archive ->
            archive.queuePosition = index + 1
        }
    }
    
    /**
     * Обновить состояние очереди
     */
    private fun updateQueueState() {
        _queueState.value = queue.toList()
    }
    
    /**
     * Освободить ресурсы
     */
    fun cleanup() {
        stop()
        coroutineScope.cancel()
    }
}
