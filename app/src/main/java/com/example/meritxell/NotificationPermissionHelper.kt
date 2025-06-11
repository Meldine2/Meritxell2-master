package com.example.meritxell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class NotificationPermissionHelper(private val activity: Activity) {
    
    companion object {
        private const val TAG = "NotificationPermission"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val NOTIFICATION_SETTINGS_REQUEST_CODE = 1002
    }
    
    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre Android 13, notification permission not required
        }
    }
    
    /**
     * Request notification permission with explanation
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowPermissionRationale()) {
                showPermissionRationaleDialog()
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * Check if we should show permission rationale
     */
    private fun shouldShowPermissionRationale(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }
    
    /**
     * Show dialog explaining why notifications are needed
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Enable Notifications")
            .setMessage("""
                📱 Meritxell needs notification permission to:
                
                • Notify you of new messages from admins
                • Send adoption process updates
                • Alert you about donation status changes
                • Remind you of appointments
                
                This helps you stay informed about important updates in real-time.
            """.trimIndent())
            .setPositiveButton("Allow") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                Log.d(TAG, "User declined notification permission")
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionGranted: () -> Unit = {},
        onPermissionDenied: () -> Unit = {}
    ) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted")
                onPermissionGranted()
            } else {
                Log.d(TAG, "Notification permission denied")
                if (shouldShowPermissionRationale()) {
                    // User denied but didn't check "Don't ask again"
                    showPermissionDeniedDialog()
                } else {
                    // User denied and checked "Don't ask again"
                    showPermissionPermanentlyDeniedDialog()
                }
                onPermissionDenied()
            }
        }
    }
    
    /**
     * Show dialog when permission is denied
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Notifications Disabled")
            .setMessage("""
                You won't receive important updates about:
                • New messages from admins
                • Adoption process updates
                • Donation status changes
                
                You can enable notifications later in the app settings.
            """.trimIndent())
            .setPositiveButton("Try Again") { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton("Continue Without") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show dialog when permission is permanently denied
     */
    private fun showPermissionPermanentlyDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Enable Notifications in Settings")
            .setMessage("""
                To receive important updates, please:
                
                1. Tap "Open Settings"
                2. Find "Notifications" or "App notifications"
                3. Turn on notifications for Meritxell
                
                This ensures you don't miss important messages and updates.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("Maybe Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Open app notification settings
     */
    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                    else -> {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                }
            }
            activity.startActivityForResult(intent, NOTIFICATION_SETTINGS_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings", e)
            // Fallback to app settings
            openAppSettings()
        }
    }
    
    /**
     * Open general app settings as fallback
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
    
    /**
     * Show notification setup guide for first-time users
     */
    fun showNotificationSetupGuide(onComplete: () -> Unit = {}) {
        if (hasNotificationPermission()) {
            onComplete()
            return
        }
        
        AlertDialog.Builder(activity)
            .setTitle("📱 Stay Connected!")
            .setMessage("""
                Welcome to Meritxell! 
                
                To ensure you receive important updates about your adoption journey and donations, we recommend enabling notifications.
                
                You'll be notified about:
                • Messages from our team
                • Adoption progress updates
                • Donation confirmations
                • Appointment reminders
            """.trimIndent())
            .setPositiveButton("Enable Notifications") { _, _ ->
                requestNotificationPermission()
                onComplete()
            }
            .setNegativeButton("Skip for Now") { _, _ ->
                onComplete()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Check notification settings and prompt user if needed
     */
    fun checkAndPromptForNotifications() {
        val enhancedNotificationManager = EnhancedNotificationManager(activity)
        
        when {
            !hasNotificationPermission() -> {
                // No permission granted
                requestNotificationPermission()
            }
            !enhancedNotificationManager.areNotificationsEnabled() -> {
                // Permission granted but notifications disabled in system settings
                showNotificationDisabledDialog()
            }
            else -> {
                Log.d(TAG, "Notifications are properly enabled")
            }
        }
    }
    
    /**
     * Show dialog when notifications are disabled in system settings
     */
    private fun showNotificationDisabledDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Notifications Turned Off")
            .setMessage("""
                Notifications are currently disabled for Meritxell in your phone's settings.
                
                Would you like to enable them to receive important updates?
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("Keep Disabled") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
} 