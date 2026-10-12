package ch.glockenpost

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Sucht einmal täglich im Hintergrund nach neuen Meldungen. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = NewsRepository(applicationContext).refresh()
        if (result.newCount > 0) notifyNew(applicationContext, result.newCount)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val CHANNEL = "neue_meldungen"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("glockenpost_refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Neue Meldungen", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Hinweis, wenn neue Glocken-Meldungen gefunden wurden"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        @android.annotation.SuppressLint("MissingPermission")
        fun notifyNew(context: Context, count: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (count == 1) "1 neue Meldung" else "$count neue Meldungen"
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle("Glockenpost")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, n)
        }
    }
}
