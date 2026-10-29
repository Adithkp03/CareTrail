package com.caretrail.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.caretrail.app.data.Api
import com.caretrail.app.data.Repo
import com.caretrail.app.data.Store
import com.caretrail.app.ui.Notifier
import java.util.concurrent.TimeUnit

/**
 * Periodic closed-app reminder check (Sprint 6). Fetches the reminder inbox and
 * posts the existing generic local notification when something is unread.
 * Best-effort: WorkManager guarantees neither exact timing nor survival of an
 * aggressive battery manager; the inbox inside the app stays the source of truth.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        if (store.token == null || !Notifier.enabled(applicationContext) || !Notifier.granted(applicationContext)) return Result.success()
        return try {
            val api = Api(BuildConfig.API_URL, { store.token })
            val journey = Repo(api, store).myJourney().value ?: return Result.success()
            Notifier.maybeNotify(applicationContext, api.reminders(journey.journey_id))
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK = "caretrail_reminder_check"

        /** Idempotent: KEEP leaves an existing schedule untouched. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
