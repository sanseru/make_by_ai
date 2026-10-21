package id.abati.autocare.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Satu pintu akses data. Semua penulisan yang menyentuh beberapa tabel
 * dijalankan dalam transaksi agar riwayat tidak pernah setengah tersimpan.
 */
class Repository(private val db: AppDatabase, scope: CoroutineScope, private val files: FileCleaner) {
    private val dao = db.dao()

    /** Hapus file lampiran fisik (diimplementasikan AttachmentStore; bisa di-fake saat test). */
    fun interface FileCleaner {
        fun delete(fileNames: Collection<String>)
    }

    @Suppress("UNCHECKED_CAST")
    private val combined: Flow<AppData> = combine(
        listOf(
            dao.vehicles(), dao.odometerLogs(), dao.categories(), dao.items(), dao.reminders(), dao.workshops(),
            dao.visits(), dao.tasks(), dao.components(), dao.installations(), dao.expenses(), dao.attachments(),
            dao.inspections(), dao.inspectionResults(),
        ),
    ) { a ->
        AppData(
            vehicles = a[0] as List<Vehicle>,
            odometerLogs = a[1] as List<OdometerLog>,
            categories = a[2] as List<MaintenanceCategory>,
            items = a[3] as List<MaintenanceItem>,
            reminders = a[4] as List<Reminder>,
            workshops = a[5] as List<Workshop>,
            visits = a[6] as List<ServiceVisit>,
            tasks = a[7] as List<ServiceTask>,
            components = a[8] as List<Component>,
            installations = a[9] as List<ComponentInstallation>,
            expenses = a[10] as List<Expense>,
            attachments = a[11] as List<Attachment>,
            inspections = a[12] as List<InspectionRecord>,
            inspectionResults = a[13] as List<InspectionResult>,
        )
    }

    /** null = data belum selesai dimuat dari database. */
    val state: StateFlow<AppData?> = combined.stateIn(scope, SharingStarted.Eagerly, null)

    /** Membaca seluruh data langsung dari database (untuk worker & backup). */
    suspend fun snapshot(): AppData = db.tx {
        AppData(
            vehicles = dao.allVehicles(),
            odometerLogs = dao.allOdometerLogs(),
            categories = dao.allCategories(),
            items = dao.allItems(),
            reminders = dao.allReminders(),
            workshops = dao.allWorkshops(),
            visits = dao.allVisits(),
            tasks = dao.allTasks(),
            components = dao.allComponents(),
            installations = dao.allInstallations(),
            expenses = dao.allExpenses(),
            attachments = dao.allAttachments(),
            inspections = dao.allInspections(),
            inspectionResults = dao.allInspectionResults(),
        )
    }

    /** Mengisi katalog bawaan saat pertama kali dipakai. */
    suspend fun seedIfEmpty() {
        if (dao.categoryCount() > 0) return
        db.tx {
            dao.insertCategories(Templates.categories)
            dao.insertItems(Templates.items)
        }
    }

    // ---------------------------------------------------------------- kendaraan

    suspend fun saveVehicle(v: Vehicle, isNew: Boolean) = db.tx {
        dao.upsertVehicle(v.copy(updatedAt = now()))
        if (isNew && v.initialOdometer != null) {
            dao.upsertOdometer(
                OdometerLog(
                    vehicleId = v.id,
                    day = v.purchaseDay ?: java.time.LocalDate.now().toEpochDay(),
                    km = v.initialOdometer,
                    source = OdometerSource.INITIAL,
                    note = "Odometer awal",
                ),
            )
        }
    }

    suspend fun setArchived(v: Vehicle, archived: Boolean) = dao.upsertVehicle(v.copy(archived = archived, updatedAt = now()))

    /** Menghapus kendaraan beserta seluruh catatannya (sudah dikonfirmasi pengguna di UI). */
    suspend fun deleteVehicle(v: Vehicle) {
        val files = db.tx {
            val names = dao.attachmentsOfVehicle(v.id).map { it.fileName } + listOf(v.photo).filter { it.isNotBlank() }
            dao.deleteVehicle(v) // cascade ke semua tabel anak
            names
        }
        this.files.delete(files)
    }

    // ---------------------------------------------------------------- odometer

    suspend fun saveOdometer(o: OdometerLog) = dao.upsertOdometer(o)

    suspend fun deleteOdometer(o: OdometerLog) = dao.deleteOdometer(o)

    // ---------------------------------------------------------------- katalog

    suspend fun saveCategory(c: MaintenanceCategory) = dao.upsertCategory(c)

    suspend fun saveItem(i: MaintenanceItem) = dao.upsertItem(i)

    /** Item katalog boleh dihapus; reminder/pekerjaan yang merujuknya tetap memakai nama yang tersimpan. */
    suspend fun deleteItem(i: MaintenanceItem) = dao.deleteItem(i)

    suspend fun deleteCategory(c: MaintenanceCategory) = dao.deleteCategory(c)

    // ---------------------------------------------------------------- bengkel

    suspend fun saveWorkshop(w: Workshop) = dao.upsertWorkshop(w)

    suspend fun deleteWorkshop(w: Workshop) = db.tx {
        dao.unlinkWorkshop(w.id) // nama bengkel tetap tersimpan di kunjungan
        dao.deleteWorkshop(w)
    }

    // ---------------------------------------------------------------- reminder

    suspend fun saveReminder(r: Reminder) = dao.upsertReminder(r.copy(notifiedKey = ""))

    suspend fun deleteReminder(r: Reminder) = db.tx {
        dao.unlinkReminder(r.id)
        dao.deleteReminder(r)
    }

    suspend fun snooze(r: Reminder, untilDay: Long?, untilKm: Long?) =
        dao.upsertReminder(r.copy(snoozeDay = untilDay, snoozeKm = untilKm, notifiedKey = ""))

    suspend fun markReminderDone(r: Reminder, day: Long?) =
        dao.upsertReminder(r.copy(completedDay = day, notifiedKey = ""))

    suspend fun setNotifiedKey(id: String, key: String) = dao.setNotifiedKey(id, key)

    // ---------------------------------------------------------------- kunjungan servis

    /**
     * Pekerjaan yang memasang/mengganti komponen. [componentName] dipakai untuk membuat
     * komponen baru bila [ServiceTask.componentId] belum ada.
     */
    data class ComponentPlan(
        val componentName: String,
        val categoryName: String,
        val expectedLifeKm: Long?,
        val expectedLifeMonths: Int?,
        val serialNumber: String = "",
    )

    /**
     * Menyimpan kunjungan + seluruh pekerjaannya secara atomik dan menyinkronkan riwayat komponen:
     * - pekerjaan "diganti/dipasang" pada kunjungan selesai → episode pemasangan baru,
     *   episode lama ditutup (tidak dihapus);
     * - pekerjaan yang dihapus/diubah → episode yang dibuatnya dihapus dan episode lama dipulihkan.
     * Reminder tidak perlu diubah karena jatuh temponya dihitung dari riwayat.
     */
    suspend fun saveVisit(
        visit: ServiceVisit,
        tasks: List<ServiceTask>,
        componentPlans: Map<String, ComponentPlan>,
    ) = db.tx {
        val old = dao.tasksOfVisit(visit.id)
        dao.upsertVisit(visit.copy(updatedAt = now()))

        // Lepas sementara efek komponen dari versi lama kunjungan ini
        revertComponentEffects(old.map { it.id })

        val keep = tasks.map { it.id }.toSet()
        old.filter { it.id !in keep }.forEach { dao.deleteTask(it) }

        val completedReminderIds = mutableListOf<String>()
        tasks.forEachIndexed { index, t0 ->
            var t = t0.copy(visitId = visit.id, vehicleId = visit.vehicleId, sortOrder = index)
            val plan = componentPlans[t.id]
            if (plan != null && t.componentId == null) {
                val c = Component(vehicleId = visit.vehicleId, itemId = t.itemId, name = plan.componentName, categoryName = plan.categoryName)
                dao.upsertComponent(c)
                t = t.copy(componentId = c.id)
            }
            dao.upsertTask(t)

            if (visit.status == VisitStatus.DONE && t.action.changesComponent && t.componentId != null) {
                installComponent(visit, t, plan)
            }
            if (visit.status == VisitStatus.DONE && t.reminderId != null && t.action != TaskAction.PLANNED) {
                completedReminderIds += t.reminderId
            }
        }
        // Pekerjaan baru selesai → penundaan lama tidak berlaku lagi
        if (completedReminderIds.isNotEmpty()) dao.clearSnooze(completedReminderIds)
    }

    private suspend fun installComponent(visit: ServiceVisit, t: ServiceTask, plan: ComponentPlan?) {
        val componentId = t.componentId ?: return
        val previous = dao.installationsOf(componentId).filter { !it.removed }
        val prevLife = previous.maxByOrNull { it.createdAt }
        previous.forEach {
            dao.upsertInstallation(
                it.copy(
                    removed = true,
                    removedDay = visit.day,
                    removedKm = visit.odometer,
                    removedByTaskId = t.id,
                    removalReason = if (t.action == TaskAction.REPLACED) "Diganti" else it.removalReason,
                ),
            )
        }
        dao.upsertInstallation(
            ComponentInstallation(
                componentId = componentId,
                vehicleId = visit.vehicleId,
                taskId = t.id,
                brand = t.brand,
                type = t.partNumber,
                serialNumber = plan?.serialNumber ?: "",
                price = id.abati.autocare.domain.Money.partsCost(t),
                installedDay = visit.day,
                installedKm = visit.odometer,
                expectedLifeKm = plan?.expectedLifeKm ?: prevLife?.expectedLifeKm,
                expectedLifeMonths = plan?.expectedLifeMonths ?: prevLife?.expectedLifeMonths,
                warrantyUntilDay = t.warrantyUntilDay,
                notes = t.notes,
            ),
        )
    }

    /** Menghapus episode yang dibuat [taskIds] dan memulihkan episode yang ditutup olehnya. */
    private suspend fun revertComponentEffects(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        dao.installationsByTasks(taskIds).forEach { dao.deleteInstallation(it) }
        dao.installationsRemovedByTasks(taskIds).forEach {
            dao.upsertInstallation(it.copy(removed = false, removedDay = null, removedKm = null, removedByTaskId = null, removalReason = ""))
        }
    }

    suspend fun deleteVisit(v: ServiceVisit) {
        val files = db.tx {
            val tasks = dao.tasksOfVisit(v.id)
            revertComponentEffects(tasks.map { it.id })
            val att = dao.attachmentsOf(OwnerType.VISIT, v.id)
            att.forEach { dao.deleteAttachment(it) }
            dao.deleteVisit(v) // cascade ke service_tasks
            att.map { it.fileName }
        }
        this.files.delete(files)
    }

    // ---------------------------------------------------------------- komponen

    suspend fun saveComponent(c: Component) = dao.upsertComponent(c)

    suspend fun saveInstallation(i: ComponentInstallation) = dao.upsertInstallation(i)

    suspend fun deleteInstallation(i: ComponentInstallation) = dao.deleteInstallation(i)

    /** Mencatat pemasangan manual (mis. aki sebelum memakai aplikasi); episode aktif sebelumnya ditutup. */
    suspend fun installManually(i: ComponentInstallation) = db.tx {
        dao.installationsOf(i.componentId).filter { !it.removed && it.id != i.id }.forEach {
            dao.upsertInstallation(it.copy(removed = true, removedDay = i.installedDay, removedKm = i.installedKm, removalReason = "Diganti"))
        }
        dao.upsertInstallation(i)
    }

    suspend fun deleteComponent(c: Component) {
        val files = db.tx {
            dao.unlinkComponent(c.id)
            val att = dao.attachmentsOf(OwnerType.COMPONENT, c.id)
            att.forEach { dao.deleteAttachment(it) }
            dao.deleteComponent(c)
            att.map { it.fileName }
        }
        this.files.delete(files)
    }

    // ---------------------------------------------------------------- pengeluaran

    suspend fun saveExpense(e: Expense) = dao.upsertExpense(e)

    suspend fun deleteExpense(e: Expense) {
        val files = db.tx {
            val att = dao.attachmentsOf(OwnerType.EXPENSE, e.id)
            att.forEach { dao.deleteAttachment(it) }
            dao.deleteExpense(e)
            att.map { it.fileName }
        }
        this.files.delete(files)
    }

    // ---------------------------------------------------------------- inspeksi

    suspend fun saveInspection(r: InspectionRecord, results: List<InspectionResult>) = db.tx {
        dao.upsertInspection(r)
        dao.deleteResultsOf(r.id)
        results.forEachIndexed { i, res -> dao.upsertInspectionResult(res.copy(inspectionId = r.id, sortOrder = i)) }
    }

    suspend fun deleteInspection(r: InspectionRecord) {
        val files = db.tx {
            val att = dao.attachmentsOf(OwnerType.INSPECTION, r.id)
            att.forEach { dao.deleteAttachment(it) }
            dao.deleteInspection(r)
            att.map { it.fileName }
        }
        this.files.delete(files)
    }

    // ---------------------------------------------------------------- lampiran

    suspend fun saveAttachment(a: Attachment) = dao.upsertAttachment(a)

    suspend fun deleteAttachment(a: Attachment) {
        dao.deleteAttachment(a)
        files.delete(listOf(a.fileName))
    }

    // ---------------------------------------------------------------- restore

    enum class RestoreMode { REPLACE, MERGE }

    /**
     * Menerapkan data backup yang SUDAH divalidasi dalam satu transaksi.
     * REPLACE: data lama diganti seluruhnya. MERGE: hanya menambah data yang ID-nya belum ada,
     * sehingga restore berulang tidak menggandakan data.
     */
    suspend fun restore(data: AppData, mode: RestoreMode) = db.tx {
        if (mode == RestoreMode.REPLACE) {
            dao.clearInspectionResults(); dao.clearInspections(); dao.clearAttachments(); dao.clearExpenses()
            dao.clearInstallations(); dao.clearComponents(); dao.clearTasks(); dao.clearVisits()
            dao.clearWorkshops(); dao.clearReminders(); dao.clearItems(); dao.clearCategories()
            dao.clearOdometerLogs(); dao.clearVehicles()
        }
        // Urutan induk → anak agar foreign key terpenuhi
        dao.insertVehicles(data.vehicles)
        dao.insertOdometerLogs(data.odometerLogs)
        dao.insertCategories(data.categories)
        dao.insertItems(data.items)
        dao.insertReminders(data.reminders)
        dao.insertWorkshops(data.workshops)
        dao.insertVisits(data.visits)
        dao.insertTasks(data.tasks)
        dao.insertComponents(data.components)
        dao.insertInstallations(data.installations)
        dao.insertExpenses(data.expenses)
        dao.insertAttachments(data.attachments)
        dao.insertInspections(data.inspections)
        dao.insertInspectionResults(data.inspectionResults)
        // Katalog bawaan harus selalu ada (backup tanpa katalog)
        if (data.categories.isEmpty()) {
            dao.insertCategories(Templates.categories)
            dao.insertItems(Templates.items)
        }
    }

    /** Nama file yang masih dirujuk database (lampiran + foto kendaraan). */
    suspend fun referencedFiles(): Set<String> = (dao.allAttachmentFiles() + dao.allVehiclePhotos()).toSet()
}
