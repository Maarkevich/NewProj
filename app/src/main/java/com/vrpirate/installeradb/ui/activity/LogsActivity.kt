package com.vrpirate.installeradb.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vrpirate.installeradb.R

class LogsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.button_logs)
        
        // TODO: Реализовать экран с логами
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
