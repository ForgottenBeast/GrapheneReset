package net.graphenereset.wipe.trigger.shared

import android.app.KeyguardManager
import android.app.Service
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

import net.graphenereset.wipe.Preferences
import net.graphenereset.wipe.R
import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.Utils
import net.graphenereset.wipe.trigger.lock.LockJobManager

class ForegroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1000
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    }

    private lateinit var prefs: Preferences
    private lateinit var lockReceiver: LockReceiver
    private var receiversRegistered = false
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            updateHandler.postDelayed(this, 60000) // Update every minute
        }
    }
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            android.util.Log.i("GrapheneReset", "Received notification refresh broadcast")
            updateNotification()
        }
    }
    //private val usbReceiver = UsbReceiver()

    //USB trigger is disabled an

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("GrapheneReset", "ForegroundService.onCreate() called")
        init()
        android.util.Log.d("GrapheneReset", "ForegroundService.onCreate() completed")
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
        try {
            unregisterReceiver(refreshReceiver)
        } catch (exc: IllegalArgumentException) {
            // Receiver wasn't registered
        }
        deinit()
    }

    private fun init() {
        if (receiversRegistered) {
            android.util.Log.d("GrapheneReset", "Receivers already registered, skipping init()")
            return
        }

        prefs = Preferences.new(this)
        lockReceiver = LockReceiver(getSystemService(KeyguardManager::class.java).isDeviceLocked, this)

        // Register refresh receiver to handle notification updates from MainActivity
        registerReceiver(refreshReceiver, IntentFilter("net.graphenereset.wipe.REFRESH_NOTIFICATION"))

        val triggers = prefs.triggers
        if (triggers.and(Trigger.LOCK.value) != 0) {
            registerReceiver(lockReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            })
            receiversRegistered = true
            android.util.Log.i("GrapheneReset", "Broadcast receivers registered for LOCK trigger")
        }
        //if (triggers.and(Trigger.USB.value) != 0)
           //registerReceiver(usbReceiver, IntentFilter(ACTION_USB_STATE))
    }

    private fun deinit() {
        if (!receiversRegistered) {
            android.util.Log.d("GrapheneReset", "Receivers not registered, skipping deinit()")
            return
        }

        val unregister: (BroadcastReceiver) -> Unit = {
            try { unregisterReceiver(it) } catch (exc: IllegalArgumentException) {}
        }
        unregister(lockReceiver)
        receiversRegistered = false
        android.util.Log.i("GrapheneReset", "Broadcast receivers unregistered")
        //unregister(usbReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        android.util.Log.i("GrapheneReset", "ForegroundService.onStartCommand() called")

        // Ensure receivers are registered even if onCreate() wasn't called
        init()

        updateNotification()

        // Start periodic updates
        updateHandler.removeCallbacks(updateRunnable)
        updateHandler.postDelayed(updateRunnable, 60000)

        return START_STICKY
    }

    internal fun updateNotification() {
        val lockPrefs = Preferences(this, encrypted = false)
        val lastLock = lockPrefs.lastLockTime
        val lastUnlock = lockPrefs.lastUnlockTime
        val timeoutMinutes = lockPrefs.triggerLockCount
        val timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes.toLong())
        val currentTime = System.currentTimeMillis()

        val km = getSystemService(KeyguardManager::class.java)
        val isLocked = km.isDeviceLocked

        val lockStartTime = if (lastLock > 0L) lastLock else lastUnlock
        val elapsed = if (lockStartTime > 0L) currentTime - lockStartTime else 0L
        val remaining = if (lockStartTime > 0L && isLocked) timeoutMs - elapsed else timeoutMs

        val timeoutHours = TimeUnit.MINUTES.toHours(timeoutMinutes.toLong())
        val timeoutDays = timeoutHours / 24
        val timeoutDisplay = when {
            timeoutDays > 0 -> "$timeoutDays days"
            timeoutHours > 0 -> "${timeoutHours}h"
            else -> "${timeoutMinutes}m"
        }

        val contentText = when {
            !isLocked -> "Unlocked - Timeout: $timeoutDisplay - DO NOT DISMISS"
            lockStartTime == 0L -> "Timeout: $timeoutDisplay - Waiting for lock"
            remaining <= 0 -> "⚠️ TIMEOUT EXPIRED - Wipe pending"
            else -> {
                val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                "⚠️ Wipes in ${hours}h ${minutes}m - DO NOT DISMISS"
            }
        }

        val notification = NotificationCompat.Builder(this, NotificationManager.CHANNEL_DEFAULT_ID)
            .setContentTitle("GrapheneReset Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_tile_icon_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()

        android.util.Log.d("GrapheneReset", "Updating notification: $contentText")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            android.util.Log.i("GrapheneReset", "Notification updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("GrapheneReset", "Failed to update notification: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? { return null }

    private class LockReceiver(private var locked: Boolean, private val service: ForegroundService) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Preferences.new(context ?: return).triggers.and(Trigger.LOCK.value) == 0)
                return
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    locked = false
                    val prefs = Preferences(context, encrypted = false)
                    prefs.lastUnlockTime = System.currentTimeMillis()
                    prefs.lastLockTime = 0L  // Clear lock time on unlock
                    LockJobManager(context).cancel()
                    // Update notification immediately
                    service.updateNotification()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (locked) return
                    locked = true
                    // Set lock time when screen turns off
                    Preferences(context, encrypted = false).lastLockTime = System.currentTimeMillis()
                    Thread(Runner(context, goAsync())).start()
                    // Update notification immediately
                    service.updateNotification()
                }
            }
        }

        private class Runner(
            private val ctx: Context,
            private val pendingResult: PendingResult,
        ) : Runnable {
            override fun run() {
                val job = LockJobManager(ctx)
                var delay = 1000L
                while (job.schedule() != JobScheduler.RESULT_SUCCESS) {
                    Thread.sleep(delay)
                    delay = delay.shl(1)
                }
                pendingResult.finish()
            }
        }
    }

    //private class UsbReceiver : BroadcastReceiver() {
    //    companion object {
    //        private const val KEY_1 = "connected"
    //        private const val KEY_2 = "host_connected"
    //    }
//
    //    override fun onReceive(context: Context?, intent: Intent?) {
    //        if (intent?.action != ACTION_USB_STATE) return
    //        val utils = Utils(context ?: return)
    //        if (!utils.isDeviceLocked()) return
    //        val extras = intent.extras ?: return
    //        if (!extras.getBoolean(KEY_1) && !extras.getBoolean(KEY_2)) return
    //        utils.fire(Trigger.USB)
    //    }
    //}
}