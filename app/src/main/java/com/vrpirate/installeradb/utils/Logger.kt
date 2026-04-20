package com.vrpirate.installeradb.utils

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Система логирования с ротацией файлов
 * Максимум 10 файлов по 5 МБ каждый
 */
object Logger {
    private const val TAG = "VRPirate"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB
    private const val MAX_FILES = 10
    
    private val logsDir = File(Environment.getExternalStorageDirectory(), "VRPirate/logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    init {
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
    }
    
    enum class Level {
        DEBUG, INFO, ERROR
    }
    
    fun d(message: String) {
        log(Level.DEBUG, message)
    }
    
    fun i(message: String) {
        log(Level.INFO, message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(Level.ERROR, fullMessage)
    }
    
    private fun log(level: Level, message: String) {
        // Логирование в Logcat
        when (level) {
            Level.DEBUG -> Log.d(TAG, message)
            Level.INFO -> Log.i(TAG, message)
            Level.ERROR -> Log.e(TAG, message)
        }
        
        // Запись в файл
        try {
            val currentFile = getCurrentLogFile()
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] [${level.name}] $message\n"
            
            FileWriter(currentFile, true).use { writer ->
                writer.append(logLine)
            }
            
            // Проверка размера и ротация
            if (currentFile.length() > MAX_FILE_SIZE) {
                rotateLogFiles()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
    
    private fun getCurrentLogFile(): File {
        return File(logsDir, "vrpirate.log")
    }
    
    private fun rotateLogFiles() {
        try {
            val currentFile = getCurrentLogFile()
            
            // Удаляем самый старый файл если есть
            val oldestFile = File(logsDir, "vrpirate_${MAX_FILES - 1}.log")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }
            
            // Сдвигаем все файлы
            for (i in (MAX_FILES - 2) downTo 0) {
                val oldFile = if (i == 0) {
                    currentFile
                } else {
                    File(logsDir, "vrpirate_${i}.log")
                }
                
                if (oldFile.exists()) {
                    val newFile = File(logsDir, "vrpirate_${i + 1}.log")
                    oldFile.renameTo(newFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate logs", e)
        }
    }
    
    fun getLogFiles(): List<File> {
        return logsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun clearLogs() {
        logsDir.listFiles()?.forEach { it.delete() }
        i("Logs cleared")
    }
}
