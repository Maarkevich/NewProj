package com.vrpirate.installeradb.data.model

/**
 * Информация об APK файле
 */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val appName: String
)
