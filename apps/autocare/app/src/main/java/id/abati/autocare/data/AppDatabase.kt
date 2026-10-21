package id.abati.autocare.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Vehicle::class, OdometerLog::class, MaintenanceCategory::class, MaintenanceItem::class,
        Reminder::class, Workshop::class, ServiceVisit::class, ServiceTask::class,
        Component::class, ComponentInstallation::class, Expense::class, Attachment::class,
        InspectionRecord::class, InspectionResult::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AutoCareDao

    companion object {
        const val NAME = "autocare.db"

        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, NAME)
            }
            // Migrasi versi skema berikutnya ditambahkan di sini (jangan pakai destructive migration:
            // riwayat servis pengguna tidak boleh hilang saat update aplikasi).
            return builder.build()
        }
    }
}

/** Menjalankan [block] dalam satu transaksi database. */
suspend fun <R> AppDatabase.tx(block: suspend () -> R): R = withTransaction(block)

@Dao
interface AutoCareDao {
    // ---------- observasi seluruh tabel (data pribadi, volume kecil) ----------
    @Query("SELECT * FROM vehicles ORDER BY archived, createdAt") fun vehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM odometer_logs ORDER BY day, km") fun odometerLogs(): Flow<List<OdometerLog>>
    @Query("SELECT * FROM maintenance_categories ORDER BY sortOrder, name") fun categories(): Flow<List<MaintenanceCategory>>
    @Query("SELECT * FROM maintenance_items ORDER BY sortOrder, name") fun items(): Flow<List<MaintenanceItem>>
    @Query("SELECT * FROM reminders ORDER BY createdAt") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM workshops ORDER BY name COLLATE NOCASE") fun workshops(): Flow<List<Workshop>>
    @Query("SELECT * FROM service_visits ORDER BY day DESC, createdAt DESC") fun visits(): Flow<List<ServiceVisit>>
    @Query("SELECT * FROM service_tasks ORDER BY sortOrder") fun tasks(): Flow<List<ServiceTask>>
    @Query("SELECT * FROM components ORDER BY name COLLATE NOCASE") fun components(): Flow<List<Component>>
    @Query("SELECT * FROM component_installations ORDER BY createdAt") fun installations(): Flow<List<ComponentInstallation>>
    @Query("SELECT * FROM expenses ORDER BY day DESC, createdAt DESC") fun expenses(): Flow<List<Expense>>
    @Query("SELECT * FROM attachments ORDER BY createdAt") fun attachments(): Flow<List<Attachment>>
    @Query("SELECT * FROM inspection_records ORDER BY day DESC, createdAt DESC") fun inspections(): Flow<List<InspectionRecord>>
    @Query("SELECT * FROM inspection_results ORDER BY sortOrder") fun inspectionResults(): Flow<List<InspectionResult>>

    // ---------- baca sekali (worker, backup, logika repository) ----------
    @Query("SELECT * FROM vehicles") suspend fun allVehicles(): List<Vehicle>
    @Query("SELECT * FROM odometer_logs") suspend fun allOdometerLogs(): List<OdometerLog>
    @Query("SELECT * FROM maintenance_categories") suspend fun allCategories(): List<MaintenanceCategory>
    @Query("SELECT * FROM maintenance_items") suspend fun allItems(): List<MaintenanceItem>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>
    @Query("SELECT * FROM workshops") suspend fun allWorkshops(): List<Workshop>
    @Query("SELECT * FROM service_visits") suspend fun allVisits(): List<ServiceVisit>
    @Query("SELECT * FROM service_tasks") suspend fun allTasks(): List<ServiceTask>
    @Query("SELECT * FROM components") suspend fun allComponents(): List<Component>
    @Query("SELECT * FROM component_installations") suspend fun allInstallations(): List<ComponentInstallation>
    @Query("SELECT * FROM expenses") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<Attachment>
    @Query("SELECT * FROM inspection_records") suspend fun allInspections(): List<InspectionRecord>
    @Query("SELECT * FROM inspection_results") suspend fun allInspectionResults(): List<InspectionResult>

    @Query("SELECT COUNT(*) FROM maintenance_categories") suspend fun categoryCount(): Int
    @Query("SELECT * FROM service_tasks WHERE visitId = :visitId") suspend fun tasksOfVisit(visitId: String): List<ServiceTask>
    @Query("SELECT * FROM service_visits WHERE id = :id") suspend fun visit(id: String): ServiceVisit?
    @Query("SELECT * FROM component_installations WHERE componentId = :componentId") suspend fun installationsOf(componentId: String): List<ComponentInstallation>
    @Query("SELECT * FROM component_installations WHERE taskId IN (:taskIds)") suspend fun installationsByTasks(taskIds: List<String>): List<ComponentInstallation>
    @Query("SELECT * FROM component_installations WHERE removedByTaskId IN (:taskIds)") suspend fun installationsRemovedByTasks(taskIds: List<String>): List<ComponentInstallation>
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :ownerId") suspend fun attachmentsOf(type: OwnerType, ownerId: String): List<Attachment>
    @Query("SELECT * FROM attachments WHERE vehicleId = :vehicleId") suspend fun attachmentsOfVehicle(vehicleId: String): List<Attachment>
    @Query("SELECT fileName FROM attachments") suspend fun allAttachmentFiles(): List<String>
    @Query("SELECT photo FROM vehicles WHERE photo != ''") suspend fun allVehiclePhotos(): List<String>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: String): Reminder?

    // ---------- tulis ----------
    @Upsert suspend fun upsertVehicle(v: Vehicle)
    @Upsert suspend fun upsertOdometer(o: OdometerLog)
    @Upsert suspend fun upsertCategory(c: MaintenanceCategory)
    @Upsert suspend fun upsertItem(i: MaintenanceItem)
    @Upsert suspend fun upsertReminder(r: Reminder)
    @Upsert suspend fun upsertWorkshop(w: Workshop)
    @Upsert suspend fun upsertVisit(v: ServiceVisit)
    @Upsert suspend fun upsertTask(t: ServiceTask)
    @Upsert suspend fun upsertComponent(c: Component)
    @Upsert suspend fun upsertInstallation(i: ComponentInstallation)
    @Upsert suspend fun upsertExpense(e: Expense)
    @Upsert suspend fun upsertAttachment(a: Attachment)
    @Upsert suspend fun upsertInspection(i: InspectionRecord)
    @Upsert suspend fun upsertInspectionResult(r: InspectionResult)

    @Delete suspend fun deleteVehicle(v: Vehicle)
    @Delete suspend fun deleteOdometer(o: OdometerLog)
    @Delete suspend fun deleteCategory(c: MaintenanceCategory)
    @Delete suspend fun deleteItem(i: MaintenanceItem)
    @Delete suspend fun deleteReminder(r: Reminder)
    @Delete suspend fun deleteWorkshop(w: Workshop)
    @Delete suspend fun deleteVisit(v: ServiceVisit)
    @Delete suspend fun deleteTask(t: ServiceTask)
    @Delete suspend fun deleteComponent(c: Component)
    @Delete suspend fun deleteInstallation(i: ComponentInstallation)
    @Delete suspend fun deleteExpense(e: Expense)
    @Delete suspend fun deleteAttachment(a: Attachment)
    @Delete suspend fun deleteInspection(i: InspectionRecord)

    @Query("DELETE FROM inspection_results WHERE inspectionId = :inspectionId") suspend fun deleteResultsOf(inspectionId: String)
    @Query("UPDATE reminders SET notifiedKey = :key WHERE id = :id") suspend fun setNotifiedKey(id: String, key: String)
    @Query("UPDATE reminders SET snoozeDay = NULL, snoozeKm = NULL, notifiedKey = '' WHERE id IN (:ids)") suspend fun clearSnooze(ids: List<String>)
    @Query("UPDATE service_tasks SET reminderId = NULL WHERE reminderId = :reminderId") suspend fun unlinkReminder(reminderId: String)
    @Query("UPDATE service_tasks SET componentId = NULL WHERE componentId = :componentId") suspend fun unlinkComponent(componentId: String)
    @Query("UPDATE service_visits SET workshopId = NULL WHERE workshopId = :workshopId") suspend fun unlinkWorkshop(workshopId: String)

    // ---------- restore: kosongkan semua (urutan anak → induk) ----------
    @Query("DELETE FROM inspection_results") suspend fun clearInspectionResults()
    @Query("DELETE FROM inspection_records") suspend fun clearInspections()
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM component_installations") suspend fun clearInstallations()
    @Query("DELETE FROM components") suspend fun clearComponents()
    @Query("DELETE FROM service_tasks") suspend fun clearTasks()
    @Query("DELETE FROM service_visits") suspend fun clearVisits()
    @Query("DELETE FROM workshops") suspend fun clearWorkshops()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM maintenance_items") suspend fun clearItems()
    @Query("DELETE FROM maintenance_categories") suspend fun clearCategories()
    @Query("DELETE FROM odometer_logs") suspend fun clearOdometerLogs()
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()

    // ---------- restore gabung: lewati yang sudah ada (ID sama) ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertVehicles(v: List<Vehicle>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOdometerLogs(v: List<OdometerLog>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(v: List<MaintenanceCategory>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItems(v: List<MaintenanceItem>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReminders(v: List<Reminder>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkshops(v: List<Workshop>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertVisits(v: List<ServiceVisit>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTasks(v: List<ServiceTask>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertComponents(v: List<Component>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInstallations(v: List<ComponentInstallation>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertExpenses(v: List<Expense>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttachments(v: List<Attachment>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInspections(v: List<InspectionRecord>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInspectionResults(v: List<InspectionResult>)
}
