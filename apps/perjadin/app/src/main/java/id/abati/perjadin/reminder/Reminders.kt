package id.abati.perjadin.reminder

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.abati.perjadin.MainActivity
import id.abati.perjadin.R
import id.abati.perjadin.data.Repository
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.util.Fmt
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Penjadwalan & tampilan notifikasi pengingat harian. */
object Reminders {
    private const val WORK_NAME = "perjadin-daily-reminder"
    private const val CHANNEL_ID = "reminders"

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Menjadwalkan pengecekan harian pada [hour]. [replace] = true saat pengaturan diubah
     * agar jam baru langsung berlaku; false saat app dibuka (pertahankan jadwal yang ada).
     */
    fun schedule(context: Context, enabled: Boolean, hour: Int, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pengingat perjalanan dinas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pengingat mencatat pengeluaran & mengirim laporan"
            },
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // izin dicabut pengguna di antara pengecekan & notify
        }
    }
}

/**
 * Dijalankan sekali sehari. Mengingatkan:
 * 1. perjalanan yang sedang berlangsung tapi belum ada pengeluaran hari ini;
 * 2. perjalanan yang sudah selesai tapi laporannya belum dikirim.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = Repository.get(applicationContext).state.value
        if (!data.reminderEnabled) return Result.success()
        val today = Fmt.today()
        val open = data.trips.filter { it.status != TripStatus.REPORTED }

        open.filter { today in it.startDay..it.endDay && it.expenses.none { e -> e.day == today } }.forEach { t ->
            Reminders.notify(
                applicationContext, t.id.hashCode(),
                "Sudah catat pengeluaran hari ini?",
                "${t.odrNumber} · ${t.destination}. Catat sekarang selagi struknya masih ada.",
            )
        }

        val pending = open.filter { it.endDay < today }
        if (pending.isNotEmpty()) {
            Reminders.notify(
                applicationContext, 1001,
                "${pending.size} laporan perjalanan belum dikirim",
                pending.joinToString(", ") { it.odrNumber } + ". Buat laporan PDF lalu tandai \"Dilaporkan\".",
            )
        }
        // Sekalian segarkan widget karena "perjalanan aktif" bergantung pada tanggal
        id.abati.perjadin.widget.TripWidget.updateAll(applicationContext)
        return Result.success()
    }
}
