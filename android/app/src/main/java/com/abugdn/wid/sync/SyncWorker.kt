package com.abugdn.wid.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abugdn.wid.repository
import com.abugdn.wid.widget.ClockWidget
import com.abugdn.wid.widget.CompactWidget
import com.abugdn.wid.widget.RegionWidget
import com.abugdn.wid.widget.TopWidget
import java.util.concurrent.TimeUnit

/** A cada 30 min: baixa o feed, traduz, notifica urgentes, atualiza o widget e guarda textos. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = applicationContext.repository
        val feed = repo.refresh().getOrElse {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        Notifier.handle(applicationContext, feed)
        Notifier.followed(applicationContext, repo.followUpdates(feed))
        Notifier.trustChanges(applicationContext, feed)
        Notifier.spikes(applicationContext, feed)
        Notifier.clock(applicationContext, feed)
        Notifier.radar(applicationContext, repo.radar.value)
        Notifier.sirens(applicationContext, repo.radar.value)
        Notifier.rules(applicationContext, feed, repo.radar.value)
        Notifier.predictions(applicationContext, repo.takeDuePredictions())
        TopWidget().updateAll(applicationContext)
        CompactWidget().updateAll(applicationContext)
        RegionWidget().updateAll(applicationContext)
        ClockWidget().updateAll(applicationContext)
        com.abugdn.wid.widget.DeadlineWidget().updateAll(applicationContext)
        com.abugdn.wid.widget.SirensWidget().updateAll(applicationContext)
        // Com economia de dados, textos completos antecipados só fora da rede móvel.
        val metered = applicationContext.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        if (!repo.settings.value.dataSaver || !metered) runCatching { repo.prefetch(feed) }
        repo.updater.check().getOrNull()?.let { update ->
            if (repo.updater.shouldNotify(update)) Notifier.update(applicationContext, update)
        }
        return Result.success()
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
