package com.vrpirate.installeradb.data.model

import java.io.File

/**
 * Модель архива/APK файла
 */
data class ArchiveItem(
    val id: String,                          // Уникальный ID
    val name: String,                        // Имя без расширения
    val file: File,                          // Основной файл
    val type: ArchiveType,                   // Тип архива
    val isMultipart: Boolean = false,        // Многотомный?
    val parts: List<File> = emptyList(),     // Части архива (если многотомный)
    val totalSize: Long = 0,                 // Общий размер
    var status: InstallStatus = InstallStatus.NEW,  // Текущий статус
    var progress: Int = 0,                   // Прогресс 0-100
    var progressText: String = "",           // Текст прогресса
    var errorMessage: String? = null,        // Сообщение об ошибке
    var queuePosition: Int = -1              // Позиция в очереди (-1 = не в очереди)
)

enum class ArchiveType {
    ZIP,
    SEVEN_Z,
    RAR,
    APK
}

enum class InstallStatus {
    NEW,           // Новый (синий)
    IN_QUEUE,      // В очереди
    EXTRACTING,    // Распаковка (жёлтый)
    INSTALLING,    // Установка APK (жёлтый)
    COPYING_CACHE, // Копирование кэша (жёлтый)
    SUCCESS,       // Успешно (зелёный)
    ERROR          // Ошибка (красный)
}
