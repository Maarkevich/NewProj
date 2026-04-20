package com.vrpirate.installeradb.utils

import kotlin.math.log10
import kotlin.math.pow

/**
 * Форматирование размеров файлов
 */
object SizeFormatter {
    
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")
    
    /**
     * Форматировать размер в читаемый вид
     * @param bytes размер в байтах
     * @return строка вида "1.5 GB"
     */
    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val size = bytes / 1024.0.pow(digitGroups.toDouble())
        
        return String.format("%.1f %s", size, units[digitGroups])
    }
    
    /**
     * Форматировать размер в МБ/ГБ
     */
    fun formatMbGb(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> {
                val kb = bytes / 1024.0
                String.format("%.0f KB", kb)
            }
            bytes < 1024 * 1024 * 1024 -> {
                val mb = bytes / (1024.0 * 1024.0)
                String.format("%.1f MB", mb)
            }
            else -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                String.format("%.2f GB", gb)
            }
        }
    }
}
