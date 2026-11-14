package ru.digitalpunk.lumi.updates

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.LumiApplication
import java.util.concurrent.TimeUnit

/** The daily update check; it never starts Gecko. */
class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as LumiApplication
        scope.launch {
            val retry = app.settings.updatesOn.first() && !app.updates.checkAndNotify()
            jobFinished(params, retry)
        }
        return true
    }

    /** The system stopped the job (the network went away): try again later. */
    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

object UpdateSchedule {
    const val JOB_ID = 4201

    /** On: one periodic job, kept across reboots; an existing one keeps its period. Off: no job. */
    fun sync(context: Context, on: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!on) return scheduler.cancel(JOB_ID)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build(),
        )
    }
}
