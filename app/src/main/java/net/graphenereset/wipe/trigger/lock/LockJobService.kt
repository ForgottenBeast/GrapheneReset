package net.graphenereset.wipe.trigger.lock

import android.app.KeyguardManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit
import net.graphenereset.wipe.Preferences
import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.WipeManager

class LockJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = Preferences(this, encrypted = false)

        if (!prefs.isEnabled) return false
        if (prefs.triggers and Trigger.LOCK.value == 0) return false

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceLocked
        } else {
            km.isKeyguardLocked
        }
        if (!locked) return false

        val lastUnlock = prefs.lastUnlockTime
        if (lastUnlock <= 0L) return false

        val timeoutMs = TimeUnit.MINUTES.toMillis(prefs.triggerLockCount.toLong())
        if (System.currentTimeMillis() - lastUnlock < timeoutMs) return false

        WipeManager.requestWipe(this, Trigger.LOCK)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
