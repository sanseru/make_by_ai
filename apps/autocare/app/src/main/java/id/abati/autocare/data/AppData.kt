package id.abati.autocare.data

import kotlinx.serialization.Serializable

/**
 * Snapshot seluruh data aplikasi. Dipakai UI (via StateFlow), perhitungan domain,
 * dan sebagai isi `data.json` pada backup. Data kendaraan pribadi bervolume kecil,
 * sehingga perhitungan di memori lebih sederhana & mudah diuji daripada query SQL.
 */
@Serializable
data class AppData(
    val vehicles: List<Vehicle> = emptyList(),
    val odometerLogs: List<OdometerLog> = emptyList(),
    val categories: List<MaintenanceCategory> = emptyList(),
    val items: List<MaintenanceItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val workshops: List<Workshop> = emptyList(),
    val visits: List<ServiceVisit> = emptyList(),
    val tasks: List<ServiceTask> = emptyList(),
    val components: List<Component> = emptyList(),
    val installations: List<ComponentInstallation> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val inspections: List<InspectionRecord> = emptyList(),
    val inspectionResults: List<InspectionResult> = emptyList(),
) {
    // Indeks untuk pencarian cepat di UI
    val vehicleById: Map<String, Vehicle> by lazy { vehicles.associateBy { it.id } }
    val itemById: Map<String, MaintenanceItem> by lazy { items.associateBy { it.id } }
    val categoryById: Map<String, MaintenanceCategory> by lazy { categories.associateBy { it.id } }
    val visitById: Map<String, ServiceVisit> by lazy { visits.associateBy { it.id } }
    val tasksByVisit: Map<String, List<ServiceTask>> by lazy { tasks.groupBy { it.visitId } }
    val tasksByReminder: Map<String, List<ServiceTask>> by lazy { tasks.filter { it.reminderId != null }.groupBy { it.reminderId!! } }
    val componentById: Map<String, Component> by lazy { components.associateBy { it.id } }
    val installationsByComponent: Map<String, List<ComponentInstallation>> by lazy { installations.groupBy { it.componentId } }
    val resultsByInspection: Map<String, List<InspectionResult>> by lazy { inspectionResults.groupBy { it.inspectionId } }

    fun attachmentsOf(type: OwnerType, ownerId: String): List<Attachment> =
        attachments.filter { it.ownerType == type && it.ownerId == ownerId }

    val activeVehicles: List<Vehicle> get() = vehicles.filter { !it.archived }

    val isEmpty: Boolean get() = vehicles.isEmpty()
}
