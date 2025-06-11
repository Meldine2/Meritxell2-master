package com.example.meritxell

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Background service to automatically check and update expired appointments
 * Runs periodic checks every 30 minutes to convert expired appointments to "completed"
 */
class AppointmentExpiryService : Service() {
    
    companion object {
        private const val TAG = "AppointmentExpiryService"
        private const val CHECK_INTERVAL_MINUTES = 30L // Check every 30 minutes
    }
    
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Appointment expiry service created")
        
        handler = Handler(Looper.getMainLooper())
        
        runnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Running periodic appointment expiry check")
                
                // Check and update expired appointments
                AppointmentStatusHelper.checkAndUpdateExpiredAppointments {
                    Log.d(TAG, "Completed appointment expiry check")
                }
                
                // Schedule next run
                handler.postDelayed(this, TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES))
            }
        }
        
        // Start first run after 1 minute
        handler.postDelayed(runnable, TimeUnit.MINUTES.toMillis(1))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Appointment expiry service started")
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Appointment expiry service destroyed")
        handler.removeCallbacks(runnable)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service
    }
} 