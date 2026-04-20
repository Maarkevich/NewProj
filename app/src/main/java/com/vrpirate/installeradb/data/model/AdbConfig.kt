package com.vrpirate.installeradb.data.model

/**
 * Конфигурация ADB подключения
 */
data class AdbConfig(
    val ipAddress: String = "",
    val port: Int = 5555,
    val pairingCode: String = "",
    val isConnected: Boolean = false,
    val lastConnectedTime: Long = 0
)

enum class AdbStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING
}
