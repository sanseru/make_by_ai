package id.abati.perjadin.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Trip
import id.abati.perjadin.util.Fmt
import java.io.File
import java.io.FileOutputStream

/**
 * Membuat laporan pertanggungjawaban perjalanan dinas (A4) memakai
 * android.graphics.pdf.PdfDocument — tanpa library pihak ketiga.
 * Angka yang dicetak selalu berasal dari data anggaran & pengeluaran riil.
 */
object PdfReport {

    data class Options(
        val includeBudgetDetails: Boolean = true,
        val includeExpenseDetails: Boolean = true,
        val includeSignature: Boolean = true,
        val includeNotes: Boolean = true,
    )

    fun fileName(trip: Trip): String {
        val odr = trip.odrNumber.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "tanpa-nomor" }
        return "Laporan_Perjadin_$odr.pdf"
    }

    fun generate(context: Context, trip: Trip, profile: Profile, options: Options): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // hanya simpan laporan terakhir di cache
        val file = File(dir, fileName(trip))
        val doc = PdfDocument()
        try {
            ReportLayout(doc, trip, profile, options).render()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }
}

private enum class Align { L, R, C }

private class ReportLayout(
    private val doc: PdfDocument,
    private val trip: Trip,
    private val profile: Profile,
    private val opt: PdfReport.Options,
) {
    // A4 dalam point (1/72 inci)
    private val pageW = 595f
    private val pageH = 842f
    private val margin = 40f
    private val contentW = pageW - 2 * margin
    private val bottomLimit = pageH - margin - 16f

    private val brand = 0xFF0F766E.toInt()
    private val brandLight = 0xFFE6F4F1.toInt()
    private val zebra = 0xFFF8FAFC.toInt()
    private val border = 0xFFD1D5DB.toInt()
    private val dark = 0xFF111827.toInt()
    private val muted = 0xFF6B7280.toInt()
    private val red = 0xFFDC2626.toInt()
    private val green = 0xFF15803D.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private val body = paint(8.5f)
    private val bodyBold = paint(8.5f, bold = true)
    private val small = paint(7.5f, color = muted)
    private val section = paint(11f, bold = true, color = brand)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = border }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = 0f

    private fun paint(size: Float, bold: Boolean = false, color: Int = dark) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.color = color
    }

    private fun lineH(p: Paint) = p.textSize * 1.35f

    fun render() {
        newPage()
        header()
        tripInfo()
        recap()
        if (opt.includeBudgetDetails && trip.budgets.isNotEmpty()) budgetDetails()
        if (opt.includeExpenseDetails) expenseDetails()
        if (opt.includeNotes && trip.notes.isNotBlank()) notes()
        if (opt.includeSignature) signatures()
        finishPage()
    }

    // ---------------- halaman ----------------

    private fun newPage() {
        finishPage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNo).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun finishPage() {
        val p = page ?: return
        val fy = pageH - margin + 8f
        canvas.drawLine(margin, fy - 10f, pageW - margin, fy - 10f, strokePaint)
        canvas.drawText("Dibuat dengan aplikasi Perjadin · ${trip.odrNumber}", margin, fy, small)
        val pg = "Halaman $pageNo"
        canvas.drawText(pg, pageW - margin - small.measureText(pg), fy, small)
        doc.finishPage(p)
        page = null
    }

    private fun ensure(h: Float) {
        if (y + h > bottomLimit) newPage()
    }

    // ---------------- util teks ----------------

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split("\n")) {
            var rest = para
            if (rest.isEmpty()) { out += ""; continue }
            while (rest.isNotEmpty()) {
                var n = p.breakText(rest, true, width, null)
                if (n < rest.length) {
                    val sp = rest.lastIndexOf(' ', n)
                    if (sp > 0) n = sp
                }
                if (n <= 0) n = 1
                out += rest.substring(0, n).trimEnd()
                rest = rest.substring(n).trimStart()
            }
        }
        return out
    }

    private fun drawAligned(text: String, p: Paint, left: Float, right: Float, baseline: Float, align: Align) {
        val x = when (align) {
            Align.L -> left
            Align.R -> right - p.measureText(text)
            Align.C -> (left + right - p.measureText(text)) / 2
        }
        canvas.drawText(text, x, baseline, p)
    }

    // ---------------- bagian laporan ----------------

    private fun header() {
        val h = 70f
        fillPaint.color = brand
        canvas.drawRoundRect(RectF(margin, y, pageW - margin, y + h), 6f, 6f, fillPaint)
        val title = paint(14f, bold = true, color = white)
        val sub = paint(8.5f, color = 0xFFCCFBF1.toInt())
        canvas.drawText(profile.company.ifBlank { "LAPORAN PERJALANAN DINAS" }.uppercase(), margin + 14f, y + 24f, title)
        canvas.drawText("Laporan Pertanggungjawaban Perjalanan Dinas", margin + 14f, y + 40f, paint(10f, color = white))
        canvas.drawText("Dicetak: ${Fmt.dateLong(Fmt.today())}", margin + 14f, y + 56f, sub)

        val odrLabel = "No. ODR"
        val right = pageW - margin - 14f
        drawAligned(odrLabel, sub, 0f, right, y + 24f, Align.R)
        drawAligned(trip.odrNumber, paint(11f, bold = true, color = white), 0f, right, y + 40f, Align.R)
        drawAligned("Status: ${trip.status.label}", sub, 0f, right, y + 56f, Align.R)
        y += h + 18f
    }

    private fun sectionTitle(text: String) {
        ensure(48f)
        canvas.drawText(text, margin, y + 11f, section)
        y += 16f
        fillPaint.color = brand
        canvas.drawRect(margin, y, margin + 36f, y + 2f, fillPaint)
        y += 10f
    }

    private fun keyValues(rows: List<Pair<String, String>>) {
        val labelW = 120f
        for ((k, v) in rows) {
            val lines = wrap(v.ifBlank { "-" }, body, contentW - labelW)
            val h = lines.size * lineH(body) + 3f
            ensure(h)
            canvas.drawText(k, margin, y + body.textSize, small.apply { color = muted })
            lines.forEachIndexed { i, line -> canvas.drawText(line, margin + labelW, y + body.textSize + i * lineH(body), body) }
            y += h
        }
        y += 10f
    }

    private fun tripInfo() {
        sectionTitle("A. Informasi Perjalanan")
        keyValues(
            listOf(
                "Nomor ODR" to trip.odrNumber,
                "Keperluan / Tujuan Dinas" to trip.purpose,
                "Rute" to trip.route,
                "Tanggal" to "${Fmt.range(trip.startDay, trip.endDay)} (${trip.durationDays} hari)",
                "Nama Pelaksana" to trip.travelerName,
                "NIK / No. Karyawan" to trip.employeeId,
                "Jabatan" to profile.position,
                "Departemen" to trip.department,
            ),
        )
    }

    private fun table(
        headers: List<String>,
        widths: List<Float>,
        aligns: List<Align>,
        rows: List<List<String>>,
        footer: List<String>? = null,
        rowColors: List<Int?>? = null,
    ) {
        val pad = 4f

        fun heightOf(cells: List<String>, p: Paint): Float =
            cells.mapIndexed { i, s -> wrap(s, p, widths[i] - 2 * pad).size }.max() * lineH(p) + 2 * pad

        fun drawRow(cells: List<String>, p: Paint, fill: Int?, textColor: Int? = null) {
            val h = heightOf(cells, p)
            var x = margin
            cells.forEachIndexed { i, s ->
                val rect = RectF(x, y, x + widths[i], y + h)
                if (fill != null) {
                    fillPaint.color = fill
                    canvas.drawRect(rect, fillPaint)
                }
                canvas.drawRect(rect, strokePaint)
                val cp = if (textColor != null && i == cells.lastIndex) Paint(p).apply { color = textColor } else p
                wrap(s, cp, widths[i] - 2 * pad).forEachIndexed { li, line ->
                    drawAligned(line, cp, x + pad, x + widths[i] - pad, y + pad + cp.textSize + li * lineH(cp), aligns[i])
                }
                x += widths[i]
            }
            y += h
        }

        val head = paint(8f, bold = true, color = brand)
        fun drawHeader() = drawRow(headers, head, brandLight)

        ensure(heightOf(headers, head) + (rows.firstOrNull()?.let { heightOf(it, body) } ?: 0f))
        drawHeader()
        rows.forEachIndexed { idx, r ->
            if (y + heightOf(r, body) > bottomLimit) {
                newPage()
                drawHeader()
            }
            drawRow(r, body, if (idx % 2 == 1) zebra else null, rowColors?.getOrNull(idx))
        }
        if (footer != null) {
            ensure(heightOf(footer, bodyBold))
            drawRow(footer, bodyBold, brandLight)
        }
        y += 14f
    }

    private fun recap() {
        sectionTitle("B. Rekapitulasi Anggaran vs Realisasi")
        val cats = trip.activeCategories
        val rows = cats.map { c ->
            val b = trip.budgetFor(c)
            val a = trip.actualFor(c)
            listOf(c.label, Fmt.rupiah(b), Fmt.rupiah(a), Fmt.rupiah(b - a), Fmt.percent(a, b))
        }
        val colors = cats.map { c -> if (trip.actualFor(c) > trip.budgetFor(c)) red else null }
        if (rows.isEmpty()) {
            ensure(20f)
            canvas.drawText("Belum ada anggaran maupun pengeluaran.", margin, y + 10f, small)
            y += 24f
        } else {
            table(
                headers = listOf("Kategori", "Anggaran", "Realisasi", "Selisih", "Serapan"),
                widths = listOf(155f, 95f, 95f, 95f, 75f),
                aligns = listOf(Align.L, Align.R, Align.R, Align.R, Align.R),
                rows = rows,
                rowColors = colors,
                footer = listOf(
                    "TOTAL",
                    Fmt.rupiah(trip.totalBudget),
                    Fmt.rupiah(trip.totalActual),
                    Fmt.rupiah(trip.remaining),
                    Fmt.percent(trip.totalActual, trip.totalBudget),
                ),
            )
        }
        summaryBox()
    }

    private fun summaryBox() {
        val hasAdvance = trip.advance > 0
        val h = if (hasAdvance) 92f else 54f
        ensure(h + 10f)
        fillPaint.color = zebra
        val rect = RectF(margin, y, pageW - margin, y + h)
        canvas.drawRoundRect(rect, 6f, 6f, fillPaint)
        canvas.drawRoundRect(rect, 6f, 6f, strokePaint)

        val colW = contentW / 3
        val stats = listOf(
            Triple("Total Anggaran", Fmt.rupiah(trip.totalBudget), dark),
            Triple("Total Realisasi", Fmt.rupiah(trip.totalActual), dark),
            if (trip.remaining >= 0) Triple("Sisa Anggaran", Fmt.rupiah(trip.remaining), green)
            else Triple("Melebihi Anggaran", Fmt.rupiah(-trip.remaining), red),
        )
        stats.forEachIndexed { i, (label, value, color) ->
            val x = margin + 12f + i * colW
            canvas.drawText(label, x, y + 18f, small)
            canvas.drawText(value, x, y + 36f, paint(12f, bold = true, color = color))
        }
        if (hasAdvance) {
            val ly = y + 54f
            canvas.drawLine(margin + 12f, ly, pageW - margin - 12f, ly, strokePaint)
            val s = trip.settlement
            val text = when {
                s > 0 -> "Uang muka ${Fmt.rupiah(trip.advance)} − realisasi ${Fmt.rupiah(trip.totalActual)} = sisa ${Fmt.rupiah(s)} dikembalikan ke kantor"
                s < 0 -> "Uang muka ${Fmt.rupiah(trip.advance)} − realisasi ${Fmt.rupiah(trip.totalActual)} = kekurangan ${Fmt.rupiah(-s)} dibayarkan kantor (reimburse)"
                else -> "Uang muka ${Fmt.rupiah(trip.advance)} sama dengan realisasi — tidak ada selisih"
            }
            canvas.drawText("Penyelesaian Uang Muka", margin + 12f, ly + 15f, small)
            canvas.drawText(text, margin + 12f, ly + 30f, paint(8.5f, bold = true, color = if (s < 0) red else dark))
        }
        y += h + 18f
    }

    private fun budgetDetails() {
        sectionTitle("C. Rincian Anggaran (Plafon dari Kantor)")
        table(
            headers = listOf("No", "Kategori", "Uraian", "Jumlah"),
            widths = listOf(28f, 120f, 262f, 105f),
            aligns = listOf(Align.C, Align.L, Align.L, Align.R),
            rows = trip.budgets.sortedBy { it.category.ordinal }.mapIndexed { i, b ->
                listOf("${i + 1}", b.category.label, b.description.ifBlank { "-" }, Fmt.rupiah(b.amount))
            },
            footer = listOf("", "", "TOTAL ANGGARAN", Fmt.rupiah(trip.totalBudget)),
        )
    }

    private fun expenseDetails() {
        sectionTitle("D. Rincian Pengeluaran (Realisasi)")
        if (trip.expenses.isEmpty()) {
            ensure(20f)
            canvas.drawText("Belum ada pengeluaran yang dicatat.", margin, y + 10f, small)
            y += 24f
            return
        }
        table(
            headers = listOf("No", "Tanggal", "Kategori", "Uraian", "Pembayaran / No. Bukti", "Jumlah"),
            widths = listOf(24f, 62f, 82f, 157f, 100f, 90f),
            aligns = listOf(Align.C, Align.L, Align.L, Align.L, Align.L, Align.R),
            rows = trip.expenses.mapIndexed { i, e ->
                val desc = if (e.note.isBlank()) e.description else "${e.description}\n(${e.note})"
                val pay = if (e.receiptNo.isBlank()) e.paymentMethod else "${e.paymentMethod}\n${e.receiptNo}"
                listOf("${i + 1}", Fmt.date(e.day), e.category.label, desc, pay, Fmt.rupiah(e.amount))
            },
            footer = listOf("", "", "", "TOTAL REALISASI", "", Fmt.rupiah(trip.totalActual)),
        )
    }

    private fun notes() {
        sectionTitle("Catatan")
        val lines = wrap(trip.notes, body, contentW)
        lines.forEach { line ->
            ensure(lineH(body))
            canvas.drawText(line, margin, y + body.textSize, body)
            y += lineH(body)
        }
        y += 12f
    }

    private fun signatures() {
        val statement = "Dengan ini saya menyatakan bahwa rincian pengeluaran di atas benar-benar dikeluarkan untuk " +
            "keperluan perjalanan dinas sesuai bukti yang ada."
        val lines = wrap(statement, small, contentW)
        ensure(lines.size * lineH(small) + 130f)
        lines.forEach { line ->
            canvas.drawText(line, margin, y + small.textSize, small)
            y += lineH(small)
        }
        y += 16f

        val colW = contentW / 2
        val city = trip.origin.ifBlank { "" }
        val dateLine = listOf(city, Fmt.date(Fmt.today())).filter { it.isNotBlank() }.joinToString(", ")
        drawAligned(dateLine, body, margin + colW, margin + contentW, y + body.textSize, Align.C)
        y += 16f

        val blocks = listOf(
            Triple("Dilaporkan oleh,", trip.travelerName.ifBlank { profile.name }, listOf(profile.position, trip.employeeId).filter { it.isNotBlank() }.joinToString(" · ")),
            Triple("Disetujui oleh,", profile.approverName, profile.approverTitle),
        )
        blocks.forEachIndexed { i, (role, name, sub) ->
            val l = margin + i * colW
            val r = l + colW
            drawAligned(role, body, l, r, y + body.textSize, Align.C)
            val lineY = y + 70f
            canvas.drawLine(l + 40f, lineY, r - 40f, lineY, strokePaint)
            drawAligned(name.ifBlank { "(.................................)" }, bodyBold, l, r, lineY + 12f, Align.C)
            if (sub.isNotBlank()) drawAligned(sub, small, l, r, lineY + 24f, Align.C)
        }
        y += 100f
    }
}
