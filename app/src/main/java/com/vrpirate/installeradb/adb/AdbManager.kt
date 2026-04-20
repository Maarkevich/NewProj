package com.vrpirate.installeradb.adb

import android.content.Context
import com.vrpirate.installeradb.data.model.AdbConfig
import com.vrpirate.installeradb.data.model.AdbStatus
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Менеджер ADB подключения
 */
class AdbManager(private val context: Context) {
    
    private var currentConfig: AdbConfig? = null
    private var isConnected = false
    
    companion object {
        private const val PREFS_NAME = "adb_config"
        private const val KEY_IP = "ip_address"
        private const val KEY_PORT = "port"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_LAST_CONNECTED = "last_connected"
        
        private const val DEFAULT_PORT = 5555
        private const val CONNECTION_TIMEOUT = 10000 // 10 seconds
    }
    
    /**
     * Загрузить сохранённую конфигурацию
     */
    fun loadConfig(): AdbConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val config = AdbConfig(
            ipAddress = prefs.getString(KEY_IP, "") ?: "",
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            pairingCode = prefs.getString(KEY_PAIRING_CODE, "") ?: "",
            lastConnectedTime = prefs.getLong(KEY_LAST_CONNECTED, 0)
        )
        
        currentConfig = config
        return config
    }
    
    /**
     * Сохранить конфигурацию
     */
    fun saveConfig(config: AdbConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IP, config.ipAddress)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_PAIRING_CODE, config.pairingCode)
            .putLong(KEY_LAST_CONNECTED, config.lastConnectedTime)
            .apply()
        
        currentConfig = config
    }
    
    /**
     * Проверить подключение ADB
     */
    suspend fun checkConnection(): AdbStatus = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("adb devices")
            
            isConnected = result.contains("device") && !result.contains("offline")
            
            if (isConnected) {
                Logger.i("ADB connected")
                AdbStatus.CONNECTED
            } else {
                Logger.i("ADB disconnected")
                AdbStatus.DISCONNECTED
            }
        } catch (e: Exception) {
            Logger.e("Failed to check ADB connection", e)
            isConnected = false
            AdbStatus.DISCONNECTED
        }
    }
    
    /**
     * Подключиться к ADB через Wi-Fi
     */
    suspend fun connect(ipAddress: String, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.i("Connecting to ADB: $ipAddress:$port")
            
            // Проверяем доступность хоста
            if (!isHostReachable(ipAddress, port)) {
                Logger.e("Host not reachable: $ipAddress:$port")
                return@withContext false
            }
            
            val command = "adb connect $ipAddress:$port"
            val result = executeCommand(command)
            
            val success = result.contains("connected", ignoreCase = true)
            
            if (success) {
                Logger.i("ADB connected successfully")
                isConnected = true
                
                // Сохраняем конфигурацию
                saveConfig(AdbConfig(
                    ipAddress = ipAddress,
                    port = port,
                    isConnected = true,
                    lastConnectedTime = System.currentTimeMillis()
                ))
            } else {
                Logger.e("ADB connection failed: $result")
                isConnected = false
            }
            
            success
            
        } catch (e: Exception) {
            Logger.e("ADB connect error", e)
            isConnected = false
            false
        }
    }
    
    /**
     * Отключиться от ADB
     */
    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = "adb disconnect"
            val result = executeCommand(command)
            
            isConnected = false
            Logger.i("ADB disconnected")
            
            true
        } catch (e: Exception) {
            Logger.e("ADB disconnect error", e)
            false
        }
    }
    
    /**
     * Автоподключение при запуске
     */
    suspend fun autoConnect(): Boolean {
        val config = currentConfig ?: loadConfig()
        
        if (config.ipAddress.isEmpty()) {
            Logger.i("No saved ADB configuration")
            return false
        }
        
        Logger.i("Auto-connecting to saved ADB configuration")
        return connect(config.ipAddress, config.port)
    }
    
    /**
     * Выполнить shell команду через ADB
     */
    suspend fun executeShellCommand(command: String): String = withContext(Dispatchers.IO) {
        executeCommand("adb shell $command")
    }
    
    /**
     * Установить APK через ADB
     */
    suspend fun installApk(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.i("Installing APK via ADB: $apkPath")
            
            val command = "adb install -r $apkPath"
            val result = executeCommand(command)
            
            val success = result.contains("Success", ignoreCase = true)
            
            if (success) {
                Logger.i("APK installed via ADB successfully")
            } else {
                Logger.e("APK installation via ADB failed: $result")
            }
            
            success
            
        } catch (e: Exception) {
            Logger.e("ADB install error", e)
            false
        }
    }
    
    /**
     * Копировать файл/папку через ADB push
     */
    suspend fun push(
        sourcePath: String,
        destPath: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.i("Pushing via ADB: $sourcePath -> $destPath")
            
            val command = "adb push $sourcePath $destPath"
            val process = Runtime.getRuntime().exec(command)
            
            // Читаем вывод для отслеживания прогресса
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                line?.let { output ->
                    Logger.d("ADB push: $output")
                    
                    // Пытаемся извлечь прогресс из вывода
                    val progressMatch = Regex("(\\d+)%").find(output)
                    progressMatch?.let {
                        val progress = it.groupValues[1].toIntOrNull() ?: 0
                        onProgress(progress)
                    }
                }
            }
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Logger.i("ADB push completed successfully")
                onProgress(100)
                true
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Logger.e("ADB push failed. Exit code: $exitCode, Error: $error")
                false
            }
            
        } catch (e: Exception) {
            Logger.e("ADB push error", e)
            false
        }
    }
    
    /**
     * Проверить доступность хоста
     */
    private fun isHostReachable(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)
                true
            }
        } catch (e: Exception) {
            Logger.e("Host not reachable: $host:$port", e)
            false
        }
    }
    
    /**
     * Выполнить команду в системе
     */
    private fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            
            if (error.isNotEmpty()) {
                Logger.d("Command error output: $error")
            }
            
            output + error
        } catch (e: Exception) {
            Logger.e("Command execution error: $command", e)
            ""
        }
    }
    
    /**
     * Получить текущий статус подключения
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Получить IP адрес устройства
     */
    suspend fun getDeviceIp(): String? = withContext(Dispatchers.IO) {
        try {
            val result = executeShellCommand("ip addr show wlan0")
            val ipMatch = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(result)
            ipMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            Logger.e("Failed to get device IP", e)
            null
        }
    }
}
