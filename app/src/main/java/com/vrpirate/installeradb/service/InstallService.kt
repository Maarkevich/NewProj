package com.vrpirate.installeradb.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.vrpirate.installeradb.utils.Logger

class InstallService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Logger.i("InstallService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("InstallService started")
        // TODO: Реализовать фоновую установку
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.i("InstallService destroyed")
    }
}
