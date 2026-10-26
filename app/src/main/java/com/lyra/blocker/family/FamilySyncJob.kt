// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class FamilySyncJob : JobService() {
    private val cancelled = ConcurrentHashMap<Int, AtomicBoolean>()
    override fun onStartJob(params: JobParameters): Boolean {
        val stop = AtomicBoolean(false)
        cancelled[params.jobId] = stop
        FamilyRepository.get(this).sync(params.jobId == MANUAL, { stop.get() }) {
            cancelled.remove(params.jobId, stop)
            FamilyControl.reconcile(this)
            if (!stop.get()) jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        cancelled.remove(params.jobId)?.set(true)
        return true
    }
    companion object {
        private const val DAILY = 5701
        private const val MANUAL = 5702
        fun schedule(context: Context, immediate: Boolean = false) {
            val jobs = context.getSystemService(JobScheduler::class.java)
            if (FamilyStore(context).enabled.isEmpty()) { jobs.cancel(DAILY); jobs.cancel(MANUAL); return }
            if (jobs.getPendingJob(DAILY) == null) jobs.schedule(JobInfo.Builder(DAILY, ComponentName(context, FamilySyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(86_400_000).build())
            if (immediate) jobs.schedule(JobInfo.Builder(MANUAL, ComponentName(context, FamilySyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).build())
        }
    }
}
