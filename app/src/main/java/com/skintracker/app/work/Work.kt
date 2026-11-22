package com.skintracker.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skintracker.app.App
import com.skintracker.app.MainActivity
import com.skintracker.app.R
import com.skintracker.app.data.Analysis
import com.skintracker.app.data.Signal
import com.skintracker.app.data.Tracked
import com.skintracker.app.data.fmtMoney
import java.util.concurrent.TimeUnit

class PriceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repo
        val err = repo.refresh(true)
        val pending = repo.pendingNotifications()
        if (pending.isNotEmpty()) Notifier.send(applicationContext, pending)
        return if (err != null && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

object Scheduler {
    fun schedule(ctx: Context, hours: Int, replace: Boolean) {
        val req = PeriodicWorkRequestBuilder<PriceWorker>(hours.toLong().coerceAtLeast(1L), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "price-monitor",
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }
}

object Notifier {
    private const val CHANNEL = "signals"

    fun send(ctx: Context, list: List<Pair<Tracked, Analysis>>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Сигналы по ценам", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (list.size >= 3) {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Выгодных скинов: ${list.size}")
                .setContentText(list.take(3).joinToString(", ") { it.first.name.substringAfter("| ").substringBefore(" (") })
                .setStyle(NotificationCompat.InboxStyle().also { st ->
                    for ((t, a) in list.take(7)) {
                        val tag = when (a.signal) {
                            Signal.SELL -> "продавать"
                            Signal.BUY -> "покупать"
                            else -> "перекуп"
                        }
                        st.addLine(t.name + " — " + tag)
                    }
                })
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
            return
        }
        for ((t, a) in list) {
            val title = when (a.signal) {
                Signal.SELL -> "Продавать: "
                Signal.BUY -> "Покупать: "
                else -> "Перекуп: "
            } + t.name
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(fmtMoney(a.bestBuy?.ask ?: a.price) + " · " + a.short)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.headline + "\n" + a.reasons.take(3).joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(t.name.hashCode(), n)
        }
    }
}
