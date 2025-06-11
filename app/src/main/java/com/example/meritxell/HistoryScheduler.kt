package com.example.meritxell

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class HistoryScheduler {
    
    companion object {
        private const val TAG = "HistoryScheduler"
        private const val HISTORY_JOB_ID = 1001
        private const val HISTORY_WORK_NAME = "history_cleanup_work"
        
        /**
         * Schedule periodic history checks using WorkManager
         */
        fun scheduleHistoryChecks(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val historyCheckRequest = PeriodicWorkRequestBuilder<HistoryWorker>(
                6, TimeUnit.HOURS // Run every 6 hours
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    15000L,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                HISTORY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                historyCheckRequest
            )
            
            Log.d(TAG, "History cleanup work scheduled")
        }
        
        /**
         * Schedule immediate history check
         */
        fun runImmediateHistoryCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val immediateRequest = OneTimeWorkRequestBuilder<HistoryWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueue(immediateRequest)
            Log.d(TAG, "Immediate history check scheduled")
        }
        
        /**
         * Cancel scheduled history checks
         */
        fun cancelHistoryChecks(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(HISTORY_WORK_NAME)
            Log.d(TAG, "History cleanup work cancelled")
        }
    }
}

/**
 * Worker class that performs the actual history cleanup
 */
class HistoryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    companion object {
        private const val TAG = "HistoryWorker"
    }
    
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting history cleanup work...")
            
            // Run all history checks
            // Status-based history filtering is now used instead of scheduled tasks
            // No background history movement needed
            
            Log.d(TAG, "History cleanup work completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "History cleanup work failed: ${e.message}", e)
            Result.retry()
        }
    }
}

/**
 * JobService for Android 5.0+ compatibility (fallback)
 */
class HistoryJobService : JobService() {
    
    companion object {
        private const val TAG = "HistoryJobService"
        private const val JOB_ID = 1001
        
        fun scheduleJob(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                
                val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, HistoryJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(6 * 60 * 60 * 1000) // 6 hours
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .build()
                
                val result = jobScheduler.schedule(jobInfo)
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.d(TAG, "History job scheduled successfully")
                } else {
                    Log.e(TAG, "Failed to schedule history job")
                }
            }
        }
    }
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "History job started")
        
        // Run history checks in background thread
        Thread {
            try {
                        // Status-based history filtering is now used instead of scheduled tasks
        // No background history movement needed
                Log.d(TAG, "History job completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "History job failed: ${e.message}", e)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        
        return true // Return true because work is being done asynchronously
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "History job stopped")
        return false // Return false to not reschedule
    }
} 