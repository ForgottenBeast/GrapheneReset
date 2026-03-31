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
        if (intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Preferences.new(context ?: return)

        // Check if lock timeout has expired during shutdown/reboot
        // Use unencrypted preferences for direct boot compatibility
        val lockPrefs = Preferences(context, encrypted = false)
        if (lockPrefs.triggers and Trigger.LOCK.value != 0) {
            val lastUnlock = lockPrefs.lastUnlockTime
            if (lastUnlock > 0L) {
                val timeoutMs = TimeUnit.MINUTES.toMillis(lockPrefs.triggerLockCount.toLong())
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    km.isDeviceLocked
                } else {
                    km.isKeyguardLocked
                }

                if (locked && System.currentTimeMillis() - lastUnlock >= timeoutMs) {
                    // Timeout already expired during shutdown - trigger wipe immediately
                    android.util.Log.i("GrapheneReset", "RestartReceiver: Lock timeout expired during shutdown, triggering wipe")
                    WipeManager.requestWipe(context, Trigger.LOCK)
                    return
                } else if (locked) {
                    // Device is locked but timeout not reached yet - reschedule the job
                    android.util.Log.i("GrapheneReset", "RestartReceiver: Rescheduling lock timeout job after boot")
                    LockJobManager(context).schedule()
                }
            }
        }

        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, ForegroundService::class.java),
        )
    }
}