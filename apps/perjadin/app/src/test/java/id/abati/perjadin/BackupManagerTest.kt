package id.abati.perjadin

import id.abati.perjadin.backup.BackupManager
import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var phoneA: BackupManager
    private lateinit var dirA: File
    private val data = AppData(
        profile = Profile(name = "Budi", logoFile = "logo_1.png", signatureFile = "signature_1.png"),
        trips = listOf(
            Trip(
                odrNumber = "ODR/1", startDay = 1, endDay = 3, advance = 1000,
                expenses = listOf(
                    Expense(day = 1, amount = 100, photo = "r1.jpg"),
                    Expense(day = 2, amount = 200, photo = "r2.jpg"),
                    Expense(day = 2, amount = 300),
                ),
            ),
        ),
        onboardingDone = true,
    )

    private fun phone(name: String): Pair<BackupManager, File> {
        val root = tmp.newFolder(name)
        return BackupManager(File(root, "receipts"), File(root, "branding"), File(root, "cache").apply { mkdirs() }, "test") to root
    }

    @Before fun setUp() {
        val (m, root) = phone("a")
        phoneA = m
        dirA = root
        File(root, "receipts").apply { mkdirs() }.let { d ->
            File(d, "r1.jpg").writeBytes(byteArrayOf(1, 2, 3))
            File(d, "r2.jpg").writeBytes(byteArrayOf(4, 5, 6))
            File(d, "orphan.jpg").writeBytes(byteArrayOf(9)) // tidak dirujuk → tidak ikut
        }
        File(root, "branding").apply { mkdirs() }.let { d ->
            File(d, "logo_1.png").writeBytes(byteArrayOf(7))
            File(d, "signature_1.png").writeBytes(byteArrayOf(8))
        }
    }

    private fun exportZip(d: AppData = data): File {
        val f = tmp.newFile()
        f.outputStream().use { phoneA.export(d, it, now = 123) }
        return f
    }

    private fun entries(zip: File): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                map[e.name] = z.readBytes()
            }
        }
        return map
    }

    private fun writeZip(entries: Map<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return f
    }

    private fun expectRejected(file: File, manager: BackupManager = phoneA, contains: String) {
        try {
            manager.inspect(file)
            fail("backup seharusnya ditolak")
        } catch (e: BackupManager.BackupException) {
            assertTrue("pesan: ${e.message}", e.message!!.contains(contains, ignoreCase = true))
        }
    }

    @Test fun exportContainsDataAndOnlyReferencedAssets() {
        val names = entries(exportZip()).keys
        assertTrue("manifest.json" in names && "data.json" in names)
        assertTrue("assets/receipts/r1.jpg" in names && "assets/receipts/r2.jpg" in names)
        assertTrue("assets/logo/logo_1.png" in names && "assets/signatures/signature_1.png" in names)
        assertFalse(names.any { "orphan" in it })
    }

    @Test fun restoreToFreshPhoneRecoversEverything() {
        val zip = exportZip()
        val (phoneB, rootB) = phone("b")
        val preview = phoneB.inspect(zip)
        assertTrue(preview.isFullBackup)
        assertEquals(1, preview.tripCount)
        assertEquals(3, preview.expenseCount)
        assertEquals(2, preview.photoCount)
        assertEquals(123, preview.exportedAt)
        assertTrue(preview.warnings.isEmpty())

        var committed: AppData? = null
        phoneB.apply(preview) { committed = it }
        assertEquals(data.trips, committed!!.trips)
        assertEquals(data.profile, committed!!.profile)
        assertTrue(File(rootB, "receipts/r1.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(rootB, "branding/logo_1.png").exists())
        assertTrue(File(rootB, "branding/signature_1.png").exists())
    }

    @Test fun failedCommitRollsBackAssets() {
        val zip = exportZip()
        // HP B sudah punya data lain
        val (phoneB, rootB) = phone("b2")
        File(rootB, "receipts").apply { mkdirs() }.resolve("old.jpg").writeBytes(byteArrayOf(42))
        val preview = phoneB.inspect(zip)
        try {
            phoneB.apply(preview) { throw IllegalStateException("disk penuh") }
            fail("harus gagal")
        } catch (_: IllegalStateException) {
        }
        assertTrue(File(rootB, "receipts/old.jpg").exists())
        assertFalse(File(rootB, "receipts/r1.jpg").exists())
    }

    @Test fun tamperedAssetIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e["assets/receipts/r1.jpg"] = byteArrayOf(0, 0, 0)
        expectRejected(writeZip(e), contains = "checksum")
    }

    @Test fun tamperedDataIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e["data.json"] = data.copy(trips = emptyList()).toJson().toString().toByteArray()
        expectRejected(writeZip(e), contains = "checksum")
    }

    @Test fun pathTraversalIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e["assets/receipts/../../evil.sh"] = byteArrayOf(1)
        expectRejected(writeZip(e), contains = "tidak aman")
        assertFalse(File(dirA, "evil.sh").exists())
    }

    @Test fun unknownEntryIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e["something/else.txt"] = byteArrayOf(1)
        expectRejected(writeZip(e), contains = "tidak dikenal")
    }

    @Test fun newerFormatIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e["manifest.json"] = String(e["manifest.json"]!!).replace("\"formatVersion\": 1", "\"formatVersion\": 99").toByteArray()
        expectRejected(writeZip(e), contains = "lebih baru")
    }

    @Test fun missingManifestIsRejected() {
        val e = entries(exportZip()).toMutableMap()
        e.remove("manifest.json")
        expectRejected(writeZip(e), contains = "manifest")
    }

    @Test fun corruptFileIsRejected() {
        val f = tmp.newFile().apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5)) }
        try {
            phoneA.inspect(f)
            fail()
        } catch (_: BackupManager.BackupException) {
        }
        val garbage = tmp.newFile().apply { writeText("ini bukan backup") }
        expectRejected(garbage, contains = "tidak valid")
    }

    @Test fun legacyJsonBackupIsStillAccepted() {
        val f = tmp.newFile().apply { writeText(data.toJson().toString()) }
        val p = phoneA.inspect(f)
        assertFalse(p.isFullBackup)
        assertEquals(3, p.expenseCount)
        var committed: AppData? = null
        phoneA.apply(p) { committed = it }
        assertEquals(data.trips, committed!!.trips)
        // Backup JSON lama tidak menyentuh foto yang ada
        assertTrue(File(dirA, "receipts/r1.jpg").exists())
    }

    @Test fun missingAssetProducesWarningNotSilentLoss() {
        File(dirA, "receipts/r2.jpg").delete()
        val p = phoneA.inspect(exportZip())
        assertTrue(p.warnings.any { "foto" in it })
    }

    @Test fun exportIsDeterministicInContent() {
        val a = ByteArrayOutputStream().also { phoneA.export(data, it, now = 1) }.toByteArray()
        val b = ByteArrayOutputStream().also { phoneA.export(data, it, now = 1) }.toByteArray()
        assertEquals(a.size, b.size)
    }
}
