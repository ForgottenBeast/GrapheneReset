package net.graphenereset.wipe.trigger.lock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit
import net.graphenereset.wipe.Preferences

class LockJobManager(private val ctx: Context) {
    companion object {
        private const val JOB_ID = 1000
    }
    private val prefs by lazy { Preferences.new(ctx) }
    private val scheduler = ctx.getSystemService(JobScheduler::class.java)

    fun schedule(): Int {
        // Use periodic job to check every minute for precise timing
        // JobScheduler minimum period is 15 minutes, so we use a one-shot job
        // that reschedules itself after each check
        val checkIntervalMs = TimeUnit.MINUTES.toMillis(1) // Check every 1 minute

        return scheduler?.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, LockJobService::class.java))
                .setMinimumLatency(checkIntervalMs)
                .setOverrideDeadline(checkIntervalMs + TimeUnit.SECONDS.toMillis(10)) // Run within 10s
                .setPersisted(true)
                .build()
        ) ?: JobScheduler.RESULT_FAILURE
    }

    fun cancel() = scheduler?.cancel(JOB_ID)
}