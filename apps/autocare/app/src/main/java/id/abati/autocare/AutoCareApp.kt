package id.abati.autocare

import android.app.Application
import id.abati.autocare.data.AppDatabase
import id.abati.autocare.data.Repository
import id.abati.autocare.data.SettingsStore
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.util.AttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Kontainer dependensi sederhana (tanpa framework DI). */
class AutoCareApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: Repository by lazy { Repository(database, appScope, AttachmentStore.cleaner(this)) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.seedIfEmpty()
            val s = settings.current()
            Reminders.schedule(this@AutoCareApp, s.notificationsEnabled, s.notifyHour, replace = false)
        }
    }
}

val android.content.Context.app: AutoCareApp get() = applicationContext as AutoCareApp
