package com.vrpirate.installeradb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vrpirate.installeradb.adb.AdbManager
import com.vrpirate.installeradb.data.local.FileScanner
import com.vrpirate.installeradb.data.model.ArchiveItem
import com.vrpirate.installeradb.data.model.AdbStatus
import com.vrpirate.installeradb.service.InstallQueue
import com.vrpirate.installeradb.ui.activity.InstructionActivity
import com.vrpirate.installeradb.ui.adapter.ArchiveAdapter
import com.vrpirate.installeradb.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    
    private lateinit var adapter: ArchiveAdapter
    private val fileScanner = FileScanner()
    private val archives = mutableListOf<ArchiveItem>()
    
    // Сервисы
    private lateinit var adbManager: AdbManager
    private lateinit var installQueue: InstallQueue
    
    // UI элементы (будут инициализированы в onCreate через findViewById)
    private lateinit var rvArchives: androidx.recyclerview.widget.RecyclerView
    private lateinit var tvEmptyState: android.widget.TextView
    private lateinit var btnRefresh: android.widget.Button
    private lateinit var btnSystemSettings: android.widget.Button
    private lateinit var adbStatusContainer: android.view.ViewGroup
    private lateinit var adbStatusIndicator: android.view.View
    private lateinit var tvAdbStatus: android.widget.TextView
    private lateinit var btnLogs: android.widget.Button
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Logger.i("Storage permission granted")
            refreshArchiveList()
        } else {
            Logger.e("Storage permission denied")
            showPermissionDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Logger.i("MainActivity onCreate")
        
        // Инициализация сервисов
        adbManager = AdbManager(this)
        installQueue = InstallQueue(this)
        
        initViews()
        setupRecyclerView()
        setupButtons()
        setupBackPressed()
        checkPermissions()
        
        // Автоподключение ADB
        lifecycleScope.launch {
            val connected = adbManager.autoConnect()
            updateAdbStatus(if (connected) AdbStatus.CONNECTED else AdbStatus.DISCONNECTED)
        }
        
        // Наблюдаем за очередью установок
        lifecycleScope.launch {
            installQueue.queueState.collect { queueItems ->
                // Обновляем статусы в списке
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (installQueue.isProcessing()) {
                    // Показываем диалог подтверждения
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.dialog_exit_title)
                        .setMessage(R.string.dialog_exit_message)
                        .setPositiveButton(R.string.button_continue) { _, _ ->
                            // Продолжаем установку, сворачиваем приложение
                            moveTaskToBack(true)
                        }
                        .setNegativeButton(R.string.button_cancel_and_close) { _, _ ->
                            // Останавливаем и закрываем
                            installQueue.stop()
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun initViews() {
        rvArchives = findViewById(R.id.rvArchives)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSystemSettings = findViewById(R.id.btnSystemSettings)
        adbStatusContainer = findViewById(R.id.adbStatusContainer)
        adbStatusIndicator = findViewById(R.id.adbStatusIndicator)
        tvAdbStatus = findViewById(R.id.tvAdbStatus)
        btnLogs = findViewById(R.id.btnLogs)
    }
    
    private fun setupRecyclerView() {
        adapter = ArchiveAdapter(
            archives = archives,
            onInstallClick = { archive ->
                Logger.i("Install clicked for ${archive.name}")
                installQueue.add(archive)
                adapter.notifyDataSetChanged()
            },
            onCancelClick = { archive ->
                Logger.i("Cancel clicked for ${archive.name}")
                installQueue.remove(archive)
                adapter.notifyDataSetChanged()
            }
        )
        
        rvArchives.layoutManager = LinearLayoutManager(this)
        rvArchives.adapter = adapter
    }
    
    private fun setupButtons() {
        btnRefresh.setOnClickListener {
            Logger.i("Refresh button clicked")
            refreshArchiveList()
        }
        
        btnSystemSettings.setOnClickListener {
            Logger.i("System settings button clicked")
            openSystemSettings()
        }
        
        adbStatusContainer.setOnClickListener {
            Logger.i("ADB status clicked")
            openAdbInstruction()
        }
        
        btnLogs.setOnClickListener {
            Logger.i("Logs button clicked")
            // TODO: Открыть экран логов
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            if (!Environment.isExternalStorageManager()) {
                Logger.i("Requesting MANAGE_EXTERNAL_STORAGE permission")
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                refreshArchiveList()
            }
        } else {
            // Android 10 и ниже
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    refreshArchiveList()
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    private fun refreshArchiveList() {
        Logger.i("Refreshing archive list")
        
        lifecycleScope.launch {
            val foundArchives = withContext(Dispatchers.IO) {
                fileScanner.scanTelegramFolder()
            }
            
            archives.clear()
            archives.addAll(foundArchives)
            adapter.notifyDataSetChanged()
            
            updateEmptyState()
            
            Logger.i("Archive list refreshed: ${archives.size} items")
        }
    }
    
    private fun updateEmptyState() {
        if (archives.isEmpty()) {
            rvArchives.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvArchives.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }
    
    private fun updateAdbStatus(status: AdbStatus) {
        val (color, text) = when (status) {
            AdbStatus.CONNECTED -> Pair(R.color.adb_connected, R.string.adb_connected)
            AdbStatus.DISCONNECTED -> Pair(R.color.adb_disconnected, R.string.adb_disconnected)
            AdbStatus.CONNECTING -> Pair(R.color.adb_connecting, R.string.adb_connecting)
        }
        
        adbStatusIndicator.setBackgroundResource(R.drawable.circle_shape)
        adbStatusIndicator.backgroundTintList = ContextCompat.getColorStateList(this, color)
        tvAdbStatus.setText(text)
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
    
    private fun openAdbInstruction() {
        val intent = Intent(this, InstructionActivity::class.java)
        startActivity(intent)
    }
    
    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Для работы приложения необходим доступ к файлам. Пожалуйста, предоставьте разрешение в настройках.")
            .setPositiveButton("Настройки") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // Проверяем ADB при возврате в приложение
        lifecycleScope.launch {
            val status = adbManager.checkConnection()
            updateAdbStatus(status)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        installQueue.cleanup()
    }
}
