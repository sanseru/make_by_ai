package id.abati.perjadin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.abati.perjadin.backup.BackupService
import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Repository
import id.abati.perjadin.data.Trip
import id.abati.perjadin.report.PdfReport
import id.abati.perjadin.report.ShareHelper
import id.abati.perjadin.util.ReceiptStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Pengujian di perangkat/emulator nyata (dijalankan CI pada Android 8 & Android 14):
 * PDF, aset foto, FileProvider, dan backup/restore lengkap lewat Repository.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Membuat foto struk palsu lewat jalur impor asli (kompresi JPEG). */
    private fun fakeReceipt(color: Int, w: Int = 1200, h: Int = 1800): String {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val src = File(context.cacheDir, "src_${System.nanoTime()}.jpg")
        FileOutputStream(src).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return ReceiptStore.importImage(context, android.net.Uri.fromFile(src)).also { src.delete() }
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    private fun bigTrip(photos: List<String>): Trip {
        val start = 20_000L
        val expenses = (0 until 80).map { i ->
            Expense(
                day = start + i % 4,
                categoryId = Category.BUILT_INS[i % Category.BUILT_INS.size].id,
                description = "Pengeluaran nomor $i dengan uraian yang cukup panjang agar baris tabel harus dibungkus menjadi beberapa baris teks",
                amount = 10_000L + i * 1_000,
                receiptNo = "STRUK-$i",
                photo = photos.getOrElse(i) { "" },
            )
        }
        return Trip(
            odrNumber = "ODR/2026/11/TEST", purpose = "Uji laporan", origin = "Jakarta", destination = "Medan",
            startDay = start, endDay = start + 3, advance = 5_000_000, notes = "Catatan ".repeat(80),
            budgets = listOf(
                BudgetItem(categoryId = "HOTEL", basis = BudgetBasis.PER_NIGHT, rate = 500_000),
                BudgetItem(categoryId = "TRANSPORT", amount = 2_000_000),
            ),
            expenses = expenses,
        ).withRecalculatedBudgets()
    }

    @Test
    fun pdfWithManyExpensesAndPhotosIsGeneratedAndReadable() {
        val photos = (0 until 7).map { fakeReceipt(if (it % 2 == 0) Color.LTGRAY else Color.YELLOW) }
        val trip = bigTrip(photos)
        val file = PdfReport.generate(context, trip, Profile(name = "Penguji"), Category.BUILT_INS, PdfReport.Options())
        assertTrue(file.exists() && file.length() > 0)
        val pages = pageCount(file)
        // Rekap + 80 baris + catatan + tanda tangan (>= 3 halaman) + 4 halaman lampiran (7 foto, 2 per baris)
        assertTrue("jumlah halaman $pages", pages >= 5)
        assertTrue("ukuran PDF ${file.length()}", file.length() < 25L * 1024 * 1024)
        assertTrue(PdfReport.fileName(trip).startsWith("Perjadin_ODR-2026-11-TEST_"))
    }

    @Test
    fun pdfWithoutExpensesOrPhotosStillWorks() {
        val trip = Trip(odrNumber = "ODR/EMPTY", startDay = 1, endDay = 1)
        val file = PdfReport.generate(context, trip, Profile(), Category.BUILT_INS, PdfReport.Options())
        assertTrue(pageCount(file) >= 1)
    }

    @Test
    fun pdfIsSharedThroughContentUri() {
        val file = PdfReport.generate(context, Trip(odrNumber = "X", startDay = 1, endDay = 1), Profile(), Category.BUILT_INS, PdfReport.Options())
        val uri = ShareHelper.uriOf(context, file)
        assertEquals("content", uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
    }

    @Test
    fun fullBackupRestoresDataAndPhotosThroughRepository() {
        val repo = Repository.get(context)
        val photo = fakeReceipt(Color.BLUE)
        val trip = bigTrip(listOf(photo)).copy(odrNumber = "ODR/BACKUP")
        repo.replaceAllBlocking(AppData(trips = listOf(trip), onboardingDone = true))

        val zip = File(context.cacheDir, "test-backup.zip")
        zip.outputStream().use { BackupService.manager(context).export(repo.state.value, it) }

        // Simulasi HP baru: hapus data & foto
        repo.replaceAllBlocking(AppData(onboardingDone = true))
        ReceiptStore.file(context, photo).delete()

        val preview = BackupService.manager(context).inspect(zip)
        assertEquals(1, preview.tripCount)
        assertEquals(1, preview.photoCount)
        BackupService.restore(context, repo, preview)

        val restored = repo.state.value.trips.single()
        assertEquals("ODR/BACKUP", restored.odrNumber)
        assertEquals(trip.totalActual, restored.totalActual)
        assertEquals(trip.totalBudget, restored.totalBudget)
        assertTrue(ReceiptStore.file(context, photo).exists())
        assertTrue(BackupService.latestPreRestore(repo) != null)
    }
}
