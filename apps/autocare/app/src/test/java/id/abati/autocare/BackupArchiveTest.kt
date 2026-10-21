package id.abati.autocare

import id.abati.autocare.backup.BackupArchive
import id.abati.autocare.backup.BackupValidator
import id.abati.autocare.data.AppData
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.OwnerType
import id.abati.autocare.data.Reminder
import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import id.abati.autocare.data.Templates
import id.abati.autocare.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sampleData = buildSample()

    private fun sample(): AppData = sampleData

    private fun buildSample(): AppData {
        val car = Vehicle(id = "car", brand = "Toyota", model = "Avanza", photo = "car.jpg")
        val visit = ServiceVisit(id = "v1", vehicleId = "car", day = 20_000, odometer = 48_000)
        return AppData(
            vehicles = listOf(car),
            categories = Templates.categories,
            items = Templates.items,
            reminders = listOf(Reminder(id = "r1", vehicleId = "car", title = "Oli", intervalKm = 5_000)),
            visits = listOf(visit),
            tasks = listOf(ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Oli", unitPrice = 450_000, reminderId = "r1")),
            attachments = listOf(Attachment(id = "a1", vehicleId = "car", ownerType = OwnerType.VISIT, ownerId = "v1", fileName = "nota.jpg")),
        )
    }

    private fun archive(dir: File) = BackupArchive(dir, tmp.newFolder(), "1.0.0")

    @Test
    fun `ekspor lalu periksa - data dan lampiran utuh`() {
        val src = tmp.newFolder("src")
        File(src, "nota.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(src, "car.jpg").writeBytes(byteArrayOf(9, 9))
        val zip = tmp.newFile("b.zip")
        val result = zip.outputStream().use { archive(src).export(sample(), it) }
        assertEquals(2, result.attachmentCount)
        assertEquals(0, result.missingAttachments)

        val dst = tmp.newFolder("dst")
        val a = archive(dst)
        val preview = a.inspect(zip)
        assertEquals(sample(), preview.data)
        assertEquals(1, preview.vehicleCount)
        assertEquals(2, preview.attachmentCount)
        assertTrue(preview.warnings.isEmpty())

        a.installAttachments(preview, replace = true, referenced = setOf("nota.jpg", "car.jpg"))
        assertTrue(File(dst, "nota.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(File(dst, "car.jpg").isFile)
    }

    @Test
    fun `lampiran hilang menjadi peringatan, bukan kegagalan`() {
        val src = tmp.newFolder("src")
        val zip = tmp.newFile("b.zip")
        val result = zip.outputStream().use { archive(src).export(sample(), it) }
        assertEquals(2, result.missingAttachments)
        val preview = archive(tmp.newFolder()).inspect(zip)
        assertEquals(1, preview.warnings.size)
    }

    @Test
    fun `restore ganti membersihkan file yang tidak dirujuk, gabung tidak menimpa`() {
        val src = tmp.newFolder("src")
        File(src, "nota.jpg").writeBytes(byteArrayOf(1))
        val zip = tmp.newFile("b.zip")
        zip.outputStream().use { archive(src).export(sample(), it) }

        val dst = tmp.newFolder("dst")
        File(dst, "lama.jpg").writeBytes(byteArrayOf(7))
        File(dst, "nota.jpg").writeBytes(byteArrayOf(5))
        val a = archive(dst)
        a.installAttachments(a.inspect(zip), replace = false, referenced = emptySet())
        assertTrue(File(dst, "lama.jpg").exists())
        assertTrue(File(dst, "nota.jpg").readBytes().contentEquals(byteArrayOf(5)))

        a.installAttachments(a.inspect(zip), replace = true, referenced = setOf("nota.jpg"))
        assertFalse(File(dst, "lama.jpg").exists())
        assertTrue(File(dst, "nota.jpg").readBytes().contentEquals(byteArrayOf(1)))
    }

    @Test
    fun `arsip rusak ditolak sebelum data aktif disentuh`() {
        val src = tmp.newFolder("src")
        val good = tmp.newFile("good.zip")
        good.outputStream().use { archive(src).export(sample(), it) }

        // data.json diubah → checksum tidak cocok
        val tampered = tmp.newFile("tampered.zip")
        ZipFile(good).use { z ->
            ZipOutputStream(tampered.outputStream()).use { out ->
                z.entries().toList().forEach { e ->
                    out.putNextEntry(ZipEntry(e.name))
                    val bytes = z.getInputStream(e).readBytes()
                    out.write(if (e.name == "data.json") String(bytes).replace("Avanza", "Rush").toByteArray() else bytes)
                    out.closeEntry()
                }
            }
        }
        expectFailure(tampered, "checksum")

        val notZip = tmp.newFile("x.zip").apply { writeText("halo") }
        expectFailure(notZip, "bukan arsip")

        val noManifest = tmp.newFile("nm.zip")
        ZipOutputStream(noManifest.outputStream()).use { it.putNextEntry(ZipEntry("data.json")); it.write("{}".toByteArray()); it.closeEntry() }
        expectFailure(noManifest, "manifest")
    }

    @Test
    fun `format lebih baru ditolak`() {
        val zip = tmp.newFile("new.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("manifest.json"))
            it.write("""{"app":"AutoCare","formatVersion":99,"data":{"path":"data.json","sha256":"x","size":1}}""".toByteArray())
            it.closeEntry()
        }
        expectFailure(zip, "lebih baru")
    }

    @Test
    fun `validator menemukan relasi yatim dan data tercampur`() {
        val bad = sample().copy(
            tasks = listOf(
                ServiceTask(id = "t1", visitId = "tidak-ada", vehicleId = "car", name = "x"),
                ServiceTask(id = "t2", visitId = "v1", vehicleId = "mobil-lain", name = "y"),
            ),
            attachments = listOf(Attachment(vehicleId = "car", ownerType = OwnerType.VISIT, ownerId = "v1", fileName = "../../etc/passwd")),
        )
        val errors = BackupValidator.validate(bad)
        assertTrue(errors.any { it.contains("pekerjaan servis") })
        assertTrue(errors.any { it.contains("berbeda") })
        assertTrue(errors.any { it.contains("tidak aman") })
        assertTrue(BackupValidator.validate(sample()).isEmpty())
    }

    private fun expectFailure(f: File, messagePart: String) {
        try {
            archive(tmp.newFolder()).inspect(f)
            fail("Seharusnya ditolak: $messagePart")
        } catch (e: BackupArchive.BackupException) {
            assertTrue("Pesan '${e.message}' harus memuat '$messagePart'", e.message!!.contains(messagePart, ignoreCase = true))
        }
    }
}
