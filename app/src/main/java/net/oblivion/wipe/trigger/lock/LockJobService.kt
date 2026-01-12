package net.oblivion.wipe.trigger.lock

import android.app.job.JobParameters
import android.app.job.JobService

import net.oblivion.wipe.Trigger
import net.oblivion.wipe.Utils

class LockJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Utils(this).fire(Trigger.LOCK)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean { return true }
}