package id.abati.autocare.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/*
 * Model data AutoCare.
 *
 * Konvensi:
 * - ID berupa UUID string → backup bisa digabung (merge) tanpa menggandakan data
 *   dan siap untuk sinkronisasi cloud di masa depan.
 * - Tanggal disimpan sebagai epoch day (Long), kilometer & rupiah sebagai Long.
 * - Kelas yang sama dipakai Room dan kotlinx.serialization (backup JSON).
 */

fun newId(): String = UUID.randomUUID().toString()

fun now(): Long = System.currentTimeMillis()

// ---------------------------------------------------------------- enum

@Serializable
enum class FuelType(val label: String) {
    GASOLINE("Bensin"), DIESEL("Solar/Diesel"), HYBRID("Hybrid"), ELECTRIC("Listrik"), LPG("LPG/CNG"), OTHER("Lainnya")
}

@Serializable
enum class Transmission(val label: String) {
    MANUAL("Manual"), AUTOMATIC("Otomatis"), CVT("CVT"), DCT("DCT"), OTHER("Lainnya")
}

@Serializable
enum class OdometerSource(val label: String) {
    INITIAL("Awal"), MANUAL("Manual"), SERVICE("Servis"), INSPECTION("Inspeksi"), EXPENSE("Pengeluaran")
}

@Serializable
enum class VisitType(val label: String) {
    ROUTINE("Servis rutin"), REPAIR("Perbaikan"), INSPECTION("Inspeksi"), MODIFICATION("Modifikasi"), OTHER("Lainnya")
}

@Serializable
enum class VisitStatus(val label: String) { DRAFT("Draft"), DONE("Selesai") }

/**
 * Jenis tindakan per pekerjaan. Membedakan pekerjaan yang direncanakan, diperiksa,
 * dan benar-benar dikerjakan — pemeriksaan TIDAK dihitung sebagai penggantian.
 */
@Serializable
enum class TaskAction(val label: String, val isWorkDone: Boolean, val changesComponent: Boolean) {
    REPLACED("Diganti", true, true),
    INSTALLED("Dipasang baru", true, true),
    SERVICED("Diservis/dibersihkan", true, false),
    REPAIRED("Diperbaiki", true, false),
    INSPECTED("Diperiksa", false, false),
    PLANNED("Direncanakan", false, false),
}

@Serializable
enum class ExpenseCategory(val label: String) {
    PARTS("Suku cadang"),
    SERVICE("Servis/jasa"),
    REPAIR("Perbaikan"),
    ACCESSORY("Aksesori/modifikasi"),
    TAX("Pajak & STNK"),
    INSURANCE("Asuransi"),
    FUEL("Bahan bakar"),
    WASH("Cuci/perawatan"),
    PARKING_TOLL("Parkir & tol"),
    OTHER("Lainnya"),
}

@Serializable
enum class OwnerType { VEHICLE, VISIT, COMPONENT, INSTALLATION, EXPENSE, INSPECTION }

@Serializable
enum class AttachmentKind(val label: String) {
    RECEIPT("Nota/kuitansi"), INVOICE("Invoice"), PHOTO("Foto"), DOCUMENT("Dokumen")
}

@Serializable
enum class CheckStatus(val label: String) {
    OK("Baik"), ATTENTION("Perlu perhatian"), PROBLEM("Bermasalah"), NOT_CHECKED("Tidak diperiksa")
}

// ---------------------------------------------------------------- entitas

@Serializable
@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val id: String = newId(),
    val brand: String,
    val model: String,
    val variant: String = "",
    val year: Int? = null,
    val plate: String = "",
    val vin: String = "",
    val engineNumber: String = "",
    val color: String = "",
    val purchaseDay: Long? = null,
    val initialOdometer: Long? = null,
    val fuel: FuelType = FuelType.GASOLINE,
    val transmission: Transmission = Transmission.MANUAL,
    /** Nama file foto di folder lampiran (kosong = tanpa foto). */
    val photo: String = "",
    val notes: String = "",
    /** Kendaraan yang sudah dijual/tidak dipakai: disembunyikan tanpa menghapus riwayat. */
    val archived: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = createdAt,
) {
    val displayName: String get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Kendaraan" }
}

@Serializable
@Entity(
    tableName = "odometer_logs",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class OdometerLog(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val day: Long,
    val km: Long,
    val note: String = "",
    val source: OdometerSource = OdometerSource.MANUAL,
    val createdAt: Long = now(),
)

@Serializable
@Entity(tableName = "maintenance_categories")
data class MaintenanceCategory(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val builtIn: Boolean = false,
    val sortOrder: Int = 100,
)

/** Katalog pekerjaan/komponen. Interval hanya saran awal dan selalu bisa diubah. */
@Serializable
@Entity(
    tableName = "maintenance_items",
    foreignKeys = [ForeignKey(MaintenanceCategory::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class MaintenanceItem(
    @PrimaryKey val id: String = newId(),
    val categoryId: String,
    val name: String,
    val intervalKm: Long? = null,
    val intervalMonths: Int? = null,
    /** true = komponen fisik yang punya riwayat pemasangan (aki, ban, kampas rem, dashcam). */
    val isComponent: Boolean = false,
    val builtIn: Boolean = false,
    val sortOrder: Int = 100,
)

/**
 * Pengingat perawatan. Jadwal berikutnya TIDAK disimpan, melainkan dihitung dari
 * acuan awal ([baseDay]/[baseKm]) atau pekerjaan selesai terbaru yang terhubung.
 * Dengan begitu koreksi/penghapusan riwayat servis otomatis memperbarui reminder.
 */
@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class Reminder(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val itemId: String? = null,
    val title: String,
    val recurring: Boolean = true,
    val intervalKm: Long? = null,
    val intervalMonths: Int? = null,
    /** Acuan terakhir dikerjakan yang diisi pengguna (opsional, boleh belum diketahui). */
    val baseDay: Long? = null,
    val baseKm: Long? = null,
    /** Jatuh tempo eksplisit: untuk pengingat sekali, atau jadwal pertama pengingat berulang. */
    val dueDay: Long? = null,
    val dueKm: Long? = null,
    val notifyBeforeKm: Long = 500,
    val notifyBeforeDays: Int = 14,
    /** Penundaan: jatuh tempo efektif tidak lebih awal dari nilai ini. */
    val snoozeDay: Long? = null,
    val snoozeKm: Long? = null,
    /** Diisi saat pengingat sekali ditandai selesai secara manual. */
    val completedDay: Long? = null,
    val active: Boolean = true,
    val notes: String = "",
    /** Penanda tahap notifikasi terakhir agar tidak mengirim notifikasi berulang. */
    val notifiedKey: String = "",
    val createdAt: Long = now(),
)

@Serializable
@Entity(tableName = "workshops")
data class Workshop(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = now(),
)

@Serializable
@Entity(
    tableName = "service_visits",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("day")],
)
data class ServiceVisit(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val day: Long,
    val odometer: Long? = null,
    val type: VisitType = VisitType.ROUTINE,
    val status: VisitStatus = VisitStatus.DONE,
    val workshopId: String? = null,
    /** Salinan nama bengkel saat dicatat (tetap terbaca meski data bengkel diubah). */
    val workshopName: String = "",
    val mechanic: String = "",
    val invoiceNumber: String = "",
    val paymentMethod: String = "",
    val complaint: String = "",
    val diagnosis: String = "",
    val recommendation: String = "",
    val notes: String = "",
    val extraFee: Long = 0,
    val extraFeeNote: String = "",
    val discount: Long = 0,
    val warrantyUntilDay: Long? = null,
    val warrantyNotes: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = createdAt,
)

@Serializable
@Entity(
    tableName = "service_tasks",
    foreignKeys = [ForeignKey(ServiceVisit::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId"), Index("vehicleId"), Index("reminderId"), Index("componentId")],
)
data class ServiceTask(
    @PrimaryKey val id: String = newId(),
    val visitId: String,
    val vehicleId: String,
    val itemId: String? = null,
    val name: String,
    val action: TaskAction = TaskAction.REPLACED,
    val brand: String = "",
    val partNumber: String = "",
    /** Jumlah dalam satuan seperseribu (4,5 liter = 4500) agar tetap integer. */
    val quantityMilli: Long = 1000,
    val unit: String = "",
    val unitPrice: Long = 0,
    val laborCost: Long = 0,
    val notes: String = "",
    /** Reminder yang diselesaikan pekerjaan ini (hanya berlaku jika kunjungan selesai). */
    val reminderId: String? = null,
    /** Komponen yang dipasang/diperiksa pada pekerjaan ini. */
    val componentId: String? = null,
    val warrantyUntilDay: Long? = null,
    val sortOrder: Int = 0,
)

/** Identitas komponen pada satu kendaraan, mis. "Aki" atau "Ban depan kiri". */
@Serializable
@Entity(
    tableName = "components",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class Component(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val itemId: String? = null,
    val name: String,
    val categoryName: String = "",
    val notes: String = "",
    val createdAt: Long = now(),
)

/**
 * Satu episode pemasangan komponen. Penggantian membuat episode baru dan menutup
 * episode lama (removed*), sehingga riwayat komponen lama tidak pernah tertimpa.
 */
@Serializable
@Entity(
    tableName = "component_installations",
    foreignKeys = [ForeignKey(Component::class, ["id"], ["componentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("componentId"), Index("vehicleId"), Index("taskId")],
)
data class ComponentInstallation(
    @PrimaryKey val id: String = newId(),
    val componentId: String,
    val vehicleId: String,
    /** Pekerjaan servis yang memasang komponen ini (null = dicatat manual). */
    val taskId: String? = null,
    val brand: String = "",
    val type: String = "",
    val serialNumber: String = "",
    val price: Long = 0,
    val installedDay: Long? = null,
    val installedKm: Long? = null,
    val removed: Boolean = false,
    val removedDay: Long? = null,
    val removedKm: Long? = null,
    /** Pekerjaan servis yang menggantikan episode ini (untuk pemulihan bila servis dihapus). */
    val removedByTaskId: String? = null,
    val removalReason: String = "",
    val expectedLifeKm: Long? = null,
    val expectedLifeMonths: Int? = null,
    val nextDay: Long? = null,
    val nextKm: Long? = null,
    val warrantyUntilDay: Long? = null,
    val conditionBefore: String = "",
    val conditionAfter: String = "",
    val notes: String = "",
    val createdAt: Long = now(),
)

/** Pengeluaran di luar kunjungan bengkel (pajak, asuransi, aksesori, cuci, dll). */
@Serializable
@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("day")],
)
data class Expense(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val day: Long,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val amount: Long,
    val description: String = "",
    val odometer: Long? = null,
    val paymentMethod: String = "",
    val createdAt: Long = now(),
)

@Serializable
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["ownerType", "ownerId"])],
)
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val ownerType: OwnerType,
    val ownerId: String,
    /** Nama file di folder lampiran internal (bukan path absolut). */
    val fileName: String,
    val displayName: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0,
    val kind: AttachmentKind = AttachmentKind.PHOTO,
    val createdAt: Long = now(),
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

@Serializable
@Entity(
    tableName = "inspection_records",
    foreignKeys = [ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class InspectionRecord(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val day: Long,
    val odometer: Long? = null,
    val notes: String = "",
    val createdAt: Long = now(),
)

@Serializable
@Entity(
    tableName = "inspection_results",
    foreignKeys = [ForeignKey(InspectionRecord::class, ["id"], ["inspectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("inspectionId")],
)
data class InspectionResult(
    @PrimaryKey val id: String = newId(),
    val inspectionId: String,
    val name: String,
    val status: CheckStatus = CheckStatus.NOT_CHECKED,
    val note: String = "",
    val sortOrder: Int = 0,
)

/** Nama file aman: tanpa path / karakter aneh (dipakai saat restore). */
fun isSafeFileName(name: String): Boolean =
    name.isNotBlank() && name.length <= 128 && name.matches(Regex("[A-Za-z0-9._-]+")) && name != "." && name != ".."
