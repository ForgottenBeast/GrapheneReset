package net.graphenereset.wipe.trigger.shared

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

import net.graphenereset.wipe.Preferences
import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.WipeManager
import net.graphenereset.wipe.trigger.lock.LockJobManager

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        android.util.Log.i("GrapheneReset", "RestartReceiver.onReceive() called with action: ${intent?.action}")

        if (intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Preferences.new(context ?: return)

        // Check if lock timeout has expired during shutdown/reboot
        // Use unencrypted preferences for direct boot compatibility
        val lockPrefs = Preferences(context, encrypted = false)
        val triggers = lockPrefs.triggers
        val lockEnabled = triggers and Trigger.LOCK.value != 0

        android.util.Log.i("GrapheneReset", "RestartReceiver: triggers=$triggers, lockEnabled=$lockEnabled, isEnabled=${lockPrefs.isEnabled}")

        if (lockEnabled) {
            val lastLock = lockPrefs.lastLockTime
            val lastUnlock = lockPrefs.lastUnlockTime
            val timeoutMinutes = lockPrefs.triggerLockCount
            val timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes.toLong())
            val currentTime = System.currentTimeMillis()

            // Determine when the "lock period" started
            // If lastLockTime > 0: device was locked before shutdown, use lastLockTime
            // If lastLockTime == 0: device was NOT locked, treat shutdown as implicit lock from lastUnlock
            val lockStartTime = if (lastLock > 0L) lastLock else lastUnlock
            val elapsed = currentTime - lockStartTime

            android.util.Log.i("GrapheneReset", "RestartReceiver: lastLock=$lastLock, lastUnlock=$lastUnlock, lockStartTime=$lockStartTime, timeoutMinutes=$timeoutMinutes, elapsed=${elapsed}ms, timeoutMs=${timeoutMs}ms")

            if (lockStartTime > 0L) {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    km.isDeviceLocked
                } else {
                    km.isKeyguardLocked
                }

                android.util.Log.i("GrapheneReset", "RestartReceiver: device locked=$locked")

                if (locked && elapsed >= timeoutMs) {
                    // Timeout already expired during shutdown - trigger wipe immediately
                    android.util.Log.i("GrapheneReset", "RestartReceiver: Lock timeout expired (elapsed=${elapsed}ms >= timeout=${timeoutMs}ms), triggering wipe")
                    WipeManager.requestWipe(context, Trigger.LOCK)
                    return
                } else if (locked) {
                    // Device is locked but timeout not reached yet - reschedule the job
                    android.util.Log.i("GrapheneReset", "RestartReceiver: Timeout not reached yet (elapsed=${elapsed}ms < timeout=${timeoutMs}ms), rescheduling job")
                    LockJobManager(context).schedule()
                } else {
                    android.util.Log.i("GrapheneReset", "RestartReceiver: Device not locked, skipping lock timeout check")
                }
            } else {
                android.util.Log.i("GrapheneReset", "RestartReceiver: No lock start time available, skipping")
            }
        }

        android.util.Log.i("GrapheneReset", "RestartReceiver: Starting ForegroundService")
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, ForegroundService::class.java),
        )
    }
}