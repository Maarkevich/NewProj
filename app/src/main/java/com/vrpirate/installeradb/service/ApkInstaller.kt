package com.vrpirate.installeradb.service

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.vrpirate.installeradb.data.model.ApkInfo
import com.vrpirate.installeradb.utils.FileUtils
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Установка и управление APK
 */
class ApkInstaller(private val context: Context) {
    
    private val packageManager = context.packageManager
    
    /**
     * Получить информацию из APK файла
     */
    suspend fun getApkInfo(apkFile: File): ApkInfo? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = packageManager.getPackageArchiveInfo(
                apkFile.path,
                PackageManager.GET_META_DATA
            ) ?: return@withContext null
            
            val appInfo = packageInfo.applicationInfo
            appInfo.sourceDir = apkFile.path
            appInfo.publicSourceDir = apkFile.path
            
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            
            ApkInfo(
                packageName = packageInfo.packageName,
                versionCode = packageInfo.versionCode.toLong(),
                versionName = packageInfo.versionName ?: "Unknown",
                appName = appName
            )
        } catch (e: Exception) {
            Logger.e("Failed to get APK info: ${apkFile.path}", e)
            null
        }
    }
    
    /**
     * Проверить установленную версию
     * @return null если не установлено, иначе PackageInfo
     */
    suspend fun getInstalledVersion(packageName: String): PackageInfo? = withContext(Dispatchers.IO) {
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            Logger.e("Failed to get installed version: $packageName", e)
            null
        }
    }
    
    /**
     * Сравнить версии
     * @return -1 если новая версия ниже, 0 если равны, 1 если новая версия выше
     */
    fun compareVersions(newVersionCode: Long, installedVersionCode: Long): Int {
        return when {
            newVersionCode < installedVersionCode -> -1
            newVersionCode == installedVersionCode -> 0
            else -> 1
        }
    }
    
    /**
     * Установить APK через PackageManager
     */
    suspend fun installApk(
        apkFile: File,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Подготовка к установке...")
            Logger.i("Installing APK: ${apkFile.path}")
            
            // Используем команду pm install через Runtime
            val command = "pm install -r ${apkFile.absolutePath}"
            val process = Runtime.getRuntime().exec(command)
            
            onProgress("Установка APK...")
            
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                Logger.i("APK installed successfully: ${apkFile.name}")
                true
            } else {
                Logger.e("APK installation failed. Exit code: $exitCode\nOutput: $output\nError: $error")
                false
            }
            
        } catch (e: Exception) {
            Logger.e("APK installation error", e)
            false
        }
    }
    
    /**
     * Удалить установленное приложение
     */
    suspend fun uninstallApp(
        packageName: String,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Удаление старой версии...")
            Logger.i("Uninstalling package: $packageName")
            
            // Удаляем через pm uninstall
            val command = "pm uninstall $packageName"
            val process = Runtime.getRuntime().exec(command)
            
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            
            if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                Logger.i("Package uninstalled successfully: $packageName")
                
                // Удаляем папки данных и кэша
                deleteAppFolders(packageName, onProgress)
                
                true
            } else {
                Logger.e("Package uninstall failed: $packageName. Output: $output")
                false
            }
            
        } catch (e: Exception) {
            Logger.e("Uninstall error: $packageName", e)
            false
        }
    }
    
    /**
     * Удалить папки приложения (data, obb, sdcard)
     */
    private fun deleteAppFolders(packageName: String, onProgress: (String) -> Unit) {
        try {
            onProgress("Очистка данных приложения...")
            
            // Android/data
            val dataFolder = File("/sdcard/Android/data/$packageName")
            if (dataFolder.exists()) {
                Logger.i("Deleting data folder: ${dataFolder.path}")
                FileUtils.deleteRecursively(dataFolder)
            }
            
            // Android/obb
            val obbFolder = File("/sdcard/Android/obb/$packageName")
            if (obbFolder.exists()) {
                Logger.i("Deleting obb folder: ${obbFolder.path}")
                FileUtils.deleteRecursively(obbFolder)
            }
            
            // Папка в корне sdcard (если есть)
            val sdcardFolder = File("/sdcard/$packageName")
            if (sdcardFolder.exists()) {
                Logger.i("Deleting sdcard folder: ${sdcardFolder.path}")
                FileUtils.deleteRecursively(sdcardFolder)
            }
            
        } catch (e: Exception) {
            Logger.e("Failed to delete app folders: $packageName", e)
        }
    }
    
    /**
     * Проверить успешность установки
     */
    suspend fun verifyInstallation(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo != null
        } catch (e: Exception) {
            false
        }
    }
}
