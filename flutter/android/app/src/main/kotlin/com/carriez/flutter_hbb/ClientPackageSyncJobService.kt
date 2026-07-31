package com.carriez.flutter_hbb

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

class ClientPackageSyncJobService : JobService() {
    companion object {
        private const val TAG = "ClientPackageJob"
        private const val PERIODIC_JOB_ID = 46_107
        private const val BOOT_JOB_ID = 46_108
        private const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L

        fun schedule(context: Context, afterBoot: Boolean = false) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
            val component = ComponentName(context, ClientPackageSyncJobService::class.java)
            val periodic = baseJob(component, PERIODIC_JOB_ID)
                .setPeriodic(SIX_HOURS_MS)
                .setPersisted(true)
                .build()
            if (scheduler.schedule(periodic) != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "Cannot schedule periodic client sync")
            }
            if (afterBoot) {
                val boot = baseJob(component, BOOT_JOB_ID)
                    .setMinimumLatency(60_000L)
                    .setOverrideDeadline(60L * 60L * 1000L)
                    .build()
                if (scheduler.schedule(boot) != JobScheduler.RESULT_SUCCESS) {
                    Log.w(TAG, "Cannot schedule boot client sync")
                }
            }
        }

        private fun baseJob(component: ComponentName, id: Int): JobInfo.Builder =
            JobInfo.Builder(id, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setRequiresDeviceIdle(true)
                    }
                }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val started = ClientPackageSync.get(applicationContext).syncAllAsync { ok ->
            jobFinished(params, !ok)
        }
        return started
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
