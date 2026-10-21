package id.abati.autocare.reminder

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import id.abati.autocare.MainActivity
import id.abati.autocare.R
import id.abati.autocare.app
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.ReminderEngine
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.reminderStates
import id.abati.autocare.util.Fmt
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Notifikasi lokal (tanpa internet). Pengecekan dijalankan WorkManager sekali sehari
 * pada jam pilihan pengguna, dan segera setelah odometer/servis berubah.
 */
object Reminders {
    private const val DAILY_WORK = "autocare-daily-check"
    private const val NOW_WORK = "autocare-check-now"
    private const val CHANNEL_DUE = "service_due"
    private const val CHANNEL_GENERAL = "general"
    const val EXTRA_OPEN = "open"

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** [replace] = true saat pengaturan diubah agar jam baru langsung berlaku. */
    fun schedule(context: Context, enabled: Boolean, hour: Int, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(DAILY_WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf(ReminderWorker.KEY_DAILY to true))
            .build()
        wm.enqueueUniquePeriodicWork(
            DAILY_WORK,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Pengecekan segera (mis. setelah odometer diperbarui) agar status reminder ikut berubah. */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(5, TimeUnit.SECONDS).build(),
        )
    }

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUE, "Jadwal perawatan", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Servis yang mendekati jatuh tempo atau terlambat"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GENERAL, "Pengingat umum", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pengingat update odometer & backup data"
            },
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String, general: Boolean = false, open: String = "reminders") {
        if (!hasPermission(context)) return // izin ditolak: lewati tanpa crash
        ensureChannels(context)
        val intent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, if (general) CHANNEL_GENERAL else CHANNEL_DUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // izin dicabut di antara pengecekan & notify
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_DAILY = "daily"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val settings = app.settings.current()
        if (!settings.notificationsEnabled) return Result.success()
        val repo = app.repository
        val data = repo.snapshot()
        val today = Fmt.today()

        // 1. Reminder servis: notifikasi sekali per tahap (segera → jatuh tempo → terlambat)
        data.reminderStates(null, today).forEach { s ->
            val key = if (s.info.status.needsAttention) ReminderEngine.notifyKey(s.info) else ""
            if (key == s.reminder.notifiedKey) return@forEach
            if (key.isNotEmpty()) {
                val vehicle = s.vehicle?.let { v -> v.plate.ifBlank { v.displayName } } ?: ""
                val title = when (s.info.status) {
                    DueStatus.OVERDUE -> "Terlambat: ${s.reminder.title}"
                    DueStatus.DUE -> "Jatuh tempo: ${s.reminder.title}"
                    else -> "Segera: ${s.reminder.title}"
                }
                Reminders.notify(
                    applicationContext, s.reminder.id.hashCode(), title,
                    "$vehicle · ${Fmt.dueSummary(s.info)} (${Fmt.remainingSummary(s.info)})",
                )
            }
            repo.setNotifiedKey(s.reminder.id, key)
        }

        // 2 & 3 hanya pada pengecekan harian agar tidak berulang dalam satu hari
        if (inputData.getBoolean(KEY_DAILY, false)) {
            if (settings.odometerReminderDays > 0) {
                data.activeVehicles.forEach { v ->
                    val last = data.currentOdometer(v.id) ?: return@forEach
                    val age = today - last.day
                    if (age >= settings.odometerReminderDays && (age - settings.odometerReminderDays) % 7 == 0L) {
                        Reminders.notify(
                            applicationContext, ("odo" + v.id).hashCode(),
                            "Perbarui odometer ${v.plate.ifBlank { v.displayName }}",
                            "Kilometer terakhir dicatat $age hari lalu (${Fmt.km(last.km)}). Pengingat berbasis km butuh odometer terkini.",
                            general = true, open = "home",
                        )
                    }
                }
            }
            if (settings.backupReminderDays > 0 && data.vehicles.isNotEmpty()) {
                val since = if (settings.lastBackupAt > 0) settings.lastBackupAt else data.vehicles.minOf { it.createdAt }
                val age = (System.currentTimeMillis() - since) / 86_400_000L
                if (age >= settings.backupReminderDays && (age - settings.backupReminderDays) % 7 == 0L) {
                    Reminders.notify(
                        applicationContext, 4242,
                        "Saatnya backup data AutoCare",
                        if (settings.lastBackupAt > 0) "Backup terakhir $age hari lalu. Simpan backup lengkap agar riwayat servis aman."
                        else "Anda belum pernah membuat backup. Simpan backup lengkap agar riwayat servis aman.",
                        general = true, open = "backup",
                    )
                }
            }
        }
        return Result.success()
    }
}
