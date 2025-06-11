package com.example.meritxell

import android.app.Application
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.database.FirebaseDatabase

class MeritxellApplication : Application() {
    
    companion object {
        private const val TAG = "MeritxellApplication"
        private const val DATABASE_URL = "https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase first
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized")
            
            // Get Firebase Database instance with correct region URL
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            
            // Enable Firebase Realtime Database persistence
            database.setPersistenceEnabled(true)
            database.setPersistenceCacheSizeBytes(50 * 1024 * 1024) // 50MB cache
            Log.d(TAG, "Firebase Realtime Database persistence enabled with Asia Southeast region")
            
            // Initialize Firebase App Check
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            
            // For now, always use debug provider during development
            // In production, you should switch to PlayIntegrityAppCheckProviderFactory
            // Only initialize App Check if not running in emulator
            if (!isEmulator()) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check initialized with Debug provider")
            } else {
                Log.d(TAG, "Skipping Firebase App Check in emulator")
            }
            
            // Initialize history scheduler for automatic record management
            initializeHistorySystem()
            
            // Initialize notification system
            initializeNotificationSystem()
            
            // Initialize appointment expiry monitoring
            initializeAppointmentExpiryService()
            
            // REQUIREMENT: Initialize automatic history service for 24-hour timers
            initializeAutomaticHistoryService()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase components", e)
        }
    }
    
    /**
     * Initialize the history system with automated scheduling
     */
    private fun initializeHistorySystem() {
        try {
            // Schedule periodic history checks using WorkManager
            HistoryScheduler.scheduleHistoryChecks(this)
            Log.d(TAG, "History system initialized with periodic checks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize history system", e)
        }
    }
    
    /**
     * Check if running in emulator
     */
    private fun isEmulator(): Boolean {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")
    }

    /**
     * Initialize comprehensive notification system
     */
    private fun initializeNotificationSystem() {
        try {
            // Initialize notification manager helper
            val notificationManager = NotificationManagerHelper.getInstance(this)
            notificationManager.initializeNotifications()
            Log.d(TAG, "Notification system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize notification system", e)
        }
    }

    /**
     * Initialize appointment expiry monitoring service
     */
    private fun initializeAppointmentExpiryService() {
        try {
            val serviceIntent = Intent(this, AppointmentExpiryService::class.java)
            startService(serviceIntent)
            Log.d(TAG, "Appointment expiry service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start appointment expiry service", e)
        }
    }

    /**
     * REQUIREMENT: Initialize automatic history service for 24-hour timers
     */
    private fun initializeAutomaticHistoryService() {
        try {
            val serviceIntent = Intent(this, AutomaticHistoryService::class.java)
            startService(serviceIntent)
            Log.d(TAG, "Automatic history service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start automatic history service", e)
        }
    }

    /**
     * Get the properly configured Firebase Database instance
     */
    fun getFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance(DATABASE_URL)
    }
} 