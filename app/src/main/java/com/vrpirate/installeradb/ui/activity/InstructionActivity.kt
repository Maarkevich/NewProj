package com.vrpirate.installeradb.ui.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.vrpirate.installeradb.R
import com.vrpirate.installeradb.adb.AdbManager
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.launch

class InstructionActivity : AppCompatActivity() {
    
    private lateinit var etPairingCode: TextInputEditText
    private lateinit var btnConnect: Button
    private lateinit var btnOpenSettings: Button
    
    private lateinit var adbManager: AdbManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instruction)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.instruction_title)
        
        adbManager = AdbManager(this)
        
        initViews()
        setupButtons()
    }
    
    private fun initViews() {
        etPairingCode = findViewById(R.id.etPairingCode)
        btnConnect = findViewById(R.id.btnConnect)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
    }
    
    private fun setupButtons() {
        btnConnect.setOnClickListener {
            val code = etPairingCode.text?.toString() ?: ""
            if (code.length == 6) {
                connectAdb(code)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_invalid_pairing_code),
                    Toast.LENGTH_SHORT
                ).show()
                Logger.e("Invalid pairing code length: ${code.length}")
            }
        }
        
        btnOpenSettings.setOnClickListener {
            openSystemSettings()
        }
    }
    
    private fun connectAdb(pairingCode: String) {
        Logger.i("Attempting to connect ADB with code: $pairingCode")
        
        btnConnect.isEnabled = false
        btnConnect.text = getString(R.string.adb_connecting)
        
        lifecycleScope.launch {
            try {
                // Получаем IP устройства
                val deviceIp = adbManager.getDeviceIp()
                
                if (deviceIp != null) {
                    Logger.i("Device IP: $deviceIp")
                    
                    // Подключаемся
                    val connected = adbManager.connect(deviceIp)
                    
                    if (connected) {
                        Toast.makeText(
                            this@InstructionActivity,
                            getString(R.string.adb_connected),
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        Logger.i("ADB connected successfully")
                        finish()
                    } else {
                        showError(getString(R.string.error_adb_connection_failed))
                    }
                } else {
                    showError("Не удалось получить IP адрес устройства")
                }
                
            } catch (e: Exception) {
                Logger.e("ADB connection error", e)
                showError(e.message ?: "Неизвестная ошибка")
            } finally {
                btnConnect.isEnabled = true
                btnConnect.text = getString(R.string.button_connect)
            }
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Logger.e("ADB connection error: $message")
    }
    
    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e("Failed to open system settings", e)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
