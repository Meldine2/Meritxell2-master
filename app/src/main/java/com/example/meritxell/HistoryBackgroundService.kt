package com.example.meritxell

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Background service to handle automatic history management
 * Runs periodic checks for records that need to be moved to history
 */
class HistoryBackgroundService : Service() {
    
    companion object {
        private const val TAG = "HistoryBackgroundService"
        private const val CHECK_INTERVAL_HOURS = 6L // Run every 6 hours
    }
    
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "History background service created")
        
        handler = Handler(Looper.getMainLooper())
        
        runnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Running periodic history checks")
                
                        // Status-based history filtering is now used instead of background services
        // No background history movement needed
                
                // Schedule next run
                handler.postDelayed(this, TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS))
            }
        }
        
        // Start first run after 1 minute
        handler.postDelayed(runnable, TimeUnit.MINUTES.toMillis(1))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "History background service started")
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "History background service destroyed")
        handler.removeCallbacks(runnable)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service
    }
} 