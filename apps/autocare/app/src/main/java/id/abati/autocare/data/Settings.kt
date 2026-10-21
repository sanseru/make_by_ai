package id.abati.autocare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Preferensi aplikasi (DataStore). */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    /** Jam pengecekan harian pengingat (0–23). */
    val notifyHour: Int = 8,
    /** Interval pengingat backup dalam hari (0 = nonaktif). */
    val backupReminderDays: Int = 30,
    /** Waktu backup terakhir (epoch ms, 0 = belum pernah). */
    val lastBackupAt: Long = 0,
    /** Kendaraan yang sedang dipilih di beranda. */
    val selectedVehicleId: String = "",
    /** Pengingat mencatat odometer jika tidak diperbarui selama N hari (0 = nonaktif). */
    val odometerReminderDays: Int = 30,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val notifications = booleanPreferencesKey("notifications_enabled")
        val hour = intPreferencesKey("notify_hour")
        val backupDays = intPreferencesKey("backup_reminder_days")
        val lastBackup = longPreferencesKey("last_backup_at")
        val selected = stringPreferencesKey("selected_vehicle")
        val odoDays = intPreferencesKey("odometer_reminder_days")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            notificationsEnabled = p[K.notifications] ?: true,
            notifyHour = p[K.hour] ?: 8,
            backupReminderDays = p[K.backupDays] ?: 30,
            lastBackupAt = p[K.lastBackup] ?: 0,
            selectedVehicleId = p[K.selected] ?: "",
            odometerReminderDays = p[K.odoDays] ?: 30,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                notificationsEnabled = p[K.notifications] ?: true,
                notifyHour = p[K.hour] ?: 8,
                backupReminderDays = p[K.backupDays] ?: 30,
                lastBackupAt = p[K.lastBackup] ?: 0,
                selectedVehicleId = p[K.selected] ?: "",
                odometerReminderDays = p[K.odoDays] ?: 30,
            )
            val n = transform(cur)
            p[K.notifications] = n.notificationsEnabled
            p[K.hour] = n.notifyHour.coerceIn(0, 23)
            p[K.backupDays] = n.backupReminderDays.coerceAtLeast(0)
            p[K.lastBackup] = n.lastBackupAt
            p[K.selected] = n.selectedVehicleId
            p[K.odoDays] = n.odometerReminderDays.coerceAtLeast(0)
        }
    }
}
