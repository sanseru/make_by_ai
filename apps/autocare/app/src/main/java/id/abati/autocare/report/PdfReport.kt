package id.abati.autocare.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import id.abati.autocare.data.AppData
import id.abati.autocare.data.CheckStatus
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.domain.CostGroup
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.Money
import id.abati.autocare.domain.Period
import id.abati.autocare.domain.componentStates
import id.abati.autocare.domain.costEntries
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.reminderStates
import id.abati.autocare.domain.summarize
import id.abati.autocare.domain.visitCost
import id.abati.autocare.util.AttachmentStore
import id.abati.autocare.util.Fmt
import java.io.File
import java.io.FileOutputStream

/**
 * Laporan riwayat kendaraan (A4) dengan android.graphics.pdf.PdfDocument — tanpa library
 * pihak ketiga. Cocok sebagai dokumentasi saat menjual mobil atau klaim garansi.
 * Catatan: PDF bukan pengganti backup lengkap.
 */
object PdfReport {
    data class Options(
        val period: Period = Period.ALL,
        val periodLabel: String = "Semua waktu",
        val includeReminders: Boolean = true,
        val includeTasks: Boolean = true,
        val includeExpenses: Boolean = true,
        val includeComponents: Boolean = true,
        val includeInspections: Boolean = true,
    )

    fun fileName(v: Vehicle): String {
        val plate = v.plate.replace(Regex("[^A-Za-z0-9]+"), "").ifBlank { v.displayName.replace(Regex("[^A-Za-z0-9]+"), "-") }
        return "AutoCare_${plate}_${java.time.LocalDate.now()}.pdf"
    }

    fun generate(context: Context, data: AppData, vehicle: Vehicle, options: Options): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // hanya simpan laporan terakhir
        val file = File(dir, fileName(vehicle))
        val doc = PdfDocument()
        try {
            ReportLayout(doc, data, vehicle, options).render()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = AttachmentStore.uriOf(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Bagikan laporan PDF"))
    }

    fun view(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(AttachmentStore.uriOf(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada aplikasi pembaca PDF di perangkat", Toast.LENGTH_LONG).show()
        }
    }
}

private class ReportLayout(
    private val doc: PdfDocument,
    private val data: AppData,
    private val vehicle: Vehicle,
    private val opt: PdfReport.Options,
) {
    // A4 dalam point (1/72 inci)
    private val pageW = 595f
    private val pageH = 842f
    private val margin = 40f
    private val contentW = pageW - 2 * margin
    private val bottom = pageH - margin - 18f

    private val brand = 0xFF1D4ED8.toInt()
    private val brandLight = 0xFFE8EEFC.toInt()
    private val dark = 0xFF111827.toInt()
    private val muted = 0xFF6B7280.toInt()
    private val border = 0xFFD1D5DB.toInt()
    private val red = 0xFFDC2626.toInt()
    private val amber = 0xFFB45309.toInt()

    private val title = paint(16f, bold = true, color = brand)
    private val h2 = paint(11.5f, bold = true, color = brand)
    private val body = paint(8.5f)
    private val bold = paint(8.5f, bold = true)
    private val small = paint(7.5f, color = muted)

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0
    private val today = Fmt.today()

    private fun paint(size: Float, bold: Boolean = false, color: Int = dark) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun render() {
        newPage()
        header()
        summary()
        if (opt.includeReminders) reminders()
        visits()
        if (opt.includeExpenses) expenses()
        if (opt.includeComponents) components()
        if (opt.includeInspections) inspections()
        y += 10f
        para("Laporan ini dibuat dari catatan pengguna di aplikasi AutoCare. Laporan PDF bukan pengganti backup data lengkap.", small)
        finishPage()
    }

    // ---------------------------------------------------------------- halaman

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
        val c = p.canvas
        val footer = "AutoCare · ${vehicle.displayName} ${vehicle.plate} · dicetak ${Fmt.date(today)}"
        c.drawText(footer, margin, pageH - margin + 4f, small)
        val pn = "Hal. $pageNo"
        c.drawText(pn, pageW - margin - small.measureText(pn), pageH - margin + 4f, small)
        doc.finishPage(p)
        page = null
    }

    private fun ensure(h: Float) {
        if (y + h > bottom) newPage()
    }

    private val c: Canvas get() = canvas!!

    // ---------------------------------------------------------------- primitif

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = mutableListOf<String>()
        text.split('\n').forEach { para ->
            var line = ""
            para.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(candidate) <= width) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) out += line
                    // kata sangat panjang dipotong paksa
                    var w = word
                    while (p.measureText(w) > width && w.length > 1) {
                        var cut = w.length
                        while (cut > 1 && p.measureText(w.substring(0, cut)) > width) cut--
                        out += w.substring(0, cut)
                        w = w.substring(cut)
                    }
                    line = w
                }
            }
            out += line
        }
        return out
    }

    private fun para(text: String, p: Paint = body, indent: Float = 0f) {
        val lh = p.textSize * 1.35f
        wrap(text, p, contentW - indent).forEach {
            ensure(lh)
            c.drawText(it, margin + indent, y + p.textSize, p)
            y += lh
        }
    }

    private fun section(name: String) {
        ensure(40f)
        y += 10f
        c.drawText(name, margin, y + h2.textSize, h2)
        y += h2.textSize + 4f
        c.drawLine(margin, y, pageW - margin, y, Paint().apply { color = brand; strokeWidth = 1f })
        y += 6f
    }

    private enum class Align { L, R }

    private data class Col(val title: String, val weight: Float, val align: Align = Align.L)

    /** Tabel sederhana dengan header berulang di halaman baru dan teks terbungkus. */
    private fun table(cols: List<Col>, rows: List<List<String>>, rowColors: List<Int?> = emptyList()) {
        val total = cols.sumOf { it.weight.toDouble() }.toFloat()
        val widths = cols.map { it.weight / total * contentW }
        val pad = 3f
        fun drawHeader() {
            ensure(18f)
            c.drawRect(margin, y, pageW - margin, y + 14f, Paint().apply { color = brandLight })
            var x = margin
            cols.forEachIndexed { i, col ->
                val tx = if (col.align == Align.R) x + widths[i] - pad - bold.measureText(col.title) else x + pad
                c.drawText(col.title, tx, y + 10f, bold)
                x += widths[i]
            }
            y += 14f
        }
        drawHeader()
        val line = Paint().apply { color = border; strokeWidth = 0.5f }
        rows.forEachIndexed { r, row ->
            val wrapped = row.mapIndexed { i, cell -> wrap(cell, body, widths[i] - 2 * pad) }
            val h = wrapped.maxOf { it.size } * body.textSize * 1.3f + 4f
            if (y + h > bottom) {
                newPage()
                drawHeader()
            }
            var x = margin
            wrapped.forEachIndexed { i, lines ->
                val p = if (i == 0 && rowColors.getOrNull(r) != null) paint(8.5f, color = rowColors[r]!!) else body
                lines.forEachIndexed { li, text ->
                    val tx = if (cols[i].align == Align.R) x + widths[i] - pad - p.measureText(text) else x + pad
                    c.drawText(text, tx, y + 2f + body.textSize * (1 + li * 1.3f), p)
                }
                x += widths[i]
            }
            y += h
            c.drawLine(margin, y, pageW - margin, y, line)
        }
        y += 4f
    }

    private fun keyValues(pairs: List<Pair<String, String>>) {
        val half = contentW / 2
        pairs.chunked(2).forEach { chunk ->
            ensure(14f)
            chunk.forEachIndexed { i, (k, v) ->
                val x = margin + i * half
                c.drawText(k, x, y + 9f, small)
                c.drawText(v.take(48), x + 92f, y + 9f, bold)
            }
            y += 13f
        }
    }

    // ---------------------------------------------------------------- isi

    private fun header() {
        c.drawText("Laporan Riwayat Kendaraan", margin, y + title.textSize, title)
        y += title.textSize + 6f
        val nameP = paint(13f, bold = true)
        c.drawText(listOf(vehicle.displayName, vehicle.variant, vehicle.year?.toString()).filter { !it.isNullOrBlank() }.joinToString(" "), margin, y + 13f, nameP)
        y += 20f
        val odo = data.currentOdometer(vehicle.id)
        keyValues(
            listOf(
                "Nomor polisi" to vehicle.plate.ifBlank { "–" },
                "Tahun" to (vehicle.year?.toString() ?: "–"),
                "Nomor rangka" to vehicle.vin.ifBlank { "–" },
                "Nomor mesin" to vehicle.engineNumber.ifBlank { "–" },
                "Bahan bakar" to vehicle.fuel.label,
                "Transmisi" to vehicle.transmission.label,
                "Odometer terkini" to (odo?.let { "${Fmt.km(it.km)} (${Fmt.date(it.day)})" } ?: "–"),
                "Tanggal beli" to Fmt.date(vehicle.purchaseDay),
                "Periode laporan" to opt.periodLabel,
                "Warna" to vehicle.color.ifBlank { "–" },
            ),
        )
        if (vehicle.notes.isNotBlank()) {
            y += 2f
            para("Catatan: ${vehicle.notes}", small)
        }
    }

    private fun summary() {
        val entries = data.costEntries(vehicle.id, opt.period)
        val s = summarize(entries)
        section("Ringkasan biaya")
        keyValues(
            listOf(
                "Total biaya" to Fmt.rupiah(s.total),
                "Jumlah kunjungan" to "${s.visitCount} kunjungan",
            ) + CostGroup.entries.mapNotNull { g -> s.byGroup[g]?.let { g.label to Fmt.rupiah(it) } },
        )
    }

    private fun reminders() {
        val states = data.reminderStates(vehicle.id, today).filter { it.info.status != DueStatus.DONE }
        if (states.isEmpty()) return
        section("Status jadwal perawatan")
        table(
            listOf(Col("Perawatan", 3f), Col("Jatuh tempo", 2.6f), Col("Status", 1.6f)),
            states.map { listOf(it.reminder.title, Fmt.dueSummary(it.info), it.info.status.label) },
            states.map {
                when (it.info.status) {
                    DueStatus.OVERDUE, DueStatus.DUE -> red
                    DueStatus.DUE_SOON -> amber
                    else -> null
                }
            },
        )
    }

    private fun visits() {
        val visits = data.visits.filter { it.vehicleId == vehicle.id && it.day in opt.period }.sortedByDescending { it.day }
        section("Riwayat servis & perbaikan (${visits.size})")
        if (visits.isEmpty()) {
            para("Belum ada catatan servis pada periode ini.", small)
            return
        }
        if (!opt.includeTasks) {
            table(
                listOf(Col("Tanggal", 1.4f), Col("Km", 1.2f, Align.R), Col("Jenis", 1.5f), Col("Bengkel", 2.2f), Col("Total", 1.5f, Align.R)),
                visits.map {
                    listOf(Fmt.date(it.day), it.odometer?.let(Fmt::number) ?: "–", it.type.label + if (it.status == VisitStatus.DRAFT) " (draft)" else "", it.workshopName.ifBlank { "–" }, Fmt.rupiah(data.visitCost(it).total))
                },
            )
            return
        }
        visits.forEach { v ->
            ensure(60f)
            val cost = data.visitCost(v)
            val head = "${Fmt.date(v.day)} · ${Fmt.km(v.odometer)} · ${v.type.label}" + if (v.status == VisitStatus.DRAFT) " · DRAFT" else ""
            c.drawRect(margin, y, pageW - margin, y + 15f, Paint().apply { color = brandLight })
            c.drawText(head, margin + 4f, y + 10.5f, bold)
            val tot = Fmt.rupiah(cost.total)
            c.drawText(tot, pageW - margin - 4f - bold.measureText(tot), y + 10.5f, bold)
            y += 18f
            val meta = listOfNotNull(
                v.workshopName.takeIf { it.isNotBlank() }?.let { "Bengkel: $it" },
                v.mechanic.takeIf { it.isNotBlank() }?.let { "Mekanik: $it" },
                v.invoiceNumber.takeIf { it.isNotBlank() }?.let { "Invoice: $it" },
                v.warrantyUntilDay?.let { "Garansi s.d. ${Fmt.date(it)}" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) para(meta, small)
            val tasks = data.tasksByVisit[v.id] ?: emptyList()
            if (tasks.isNotEmpty()) {
                table(
                    listOf(Col("Pekerjaan", 3f), Col("Tindakan", 1.4f), Col("Suku cadang", 1.5f, Align.R), Col("Jasa", 1.2f, Align.R)),
                    tasks.map { t ->
                        val detail = listOf(t.brand, t.partNumber).filter { it.isNotBlank() }.joinToString(" ")
                        val qty = if (t.quantityMilli != 1000L || t.unit.isNotBlank()) " (${Money.formatQuantity(t.quantityMilli)} ${t.unit})".replace(" )", ")") else ""
                        listOf(t.name + qty + if (detail.isNotEmpty()) " – $detail" else "", t.action.label, Fmt.rupiah(Money.partsCost(t)), Fmt.rupiah(t.laborCost))
                    },
                )
            }
            if (cost.extraFee != 0L || cost.discount != 0L) {
                para("Biaya tambahan ${Fmt.rupiah(cost.extraFee)}${v.extraFeeNote.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} · Diskon ${Fmt.rupiah(cost.discount)}", small)
            }
            listOf("Keluhan" to v.complaint, "Diagnosis" to v.diagnosis, "Rekomendasi" to v.recommendation, "Catatan" to v.notes)
                .filter { it.second.isNotBlank() }
                .forEach { (k, t) -> para("$k: $t", body) }
            y += 6f
        }
    }

    private fun expenses() {
        val list = data.expenses.filter { it.vehicleId == vehicle.id && it.day in opt.period }.sortedByDescending { it.day }
        if (list.isEmpty()) return
        section("Pengeluaran lain")
        table(
            listOf(Col("Tanggal", 1.3f), Col("Kategori", 1.6f), Col("Keterangan", 3f), Col("Jumlah", 1.5f, Align.R)),
            list.map { listOf(Fmt.date(it.day), it.category.label, it.description.ifBlank { "–" }, Fmt.rupiah(it.amount)) },
        )
    }

    private fun components() {
        val states = data.componentStates(vehicle.id, today)
        if (states.isEmpty()) return
        section("Riwayat komponen")
        states.forEach { s ->
            ensure(40f)
            para("${s.component.name} — ${s.conditionLabel}", bold)
            if (s.history.isEmpty()) {
                para("Belum ada catatan pemasangan.", small, 8f)
                return@forEach
            }
            table(
                listOf(Col("Dipasang", 1.9f), Col("Merek/tipe", 2.4f), Col("Dilepas", 1.9f), Col("Harga", 1.3f, Align.R)),
                s.history.map { i ->
                    listOf(
                        "${Fmt.date(i.installedDay)}\n${Fmt.km(i.installedKm)}",
                        listOf(i.brand, i.type, i.serialNumber).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "–" },
                        if (i.removed) "${Fmt.date(i.removedDay)}\n${Fmt.km(i.removedKm)}" else "Terpasang",
                        if (i.price > 0) Fmt.rupiah(i.price) else "–",
                    )
                },
            )
        }
    }

    private fun inspections() {
        val list = data.inspections.filter { it.vehicleId == vehicle.id && it.day in opt.period }.sortedByDescending { it.day }
        if (list.isEmpty()) return
        section("Inspeksi kondisi")
        list.forEach { r ->
            val res = data.resultsByInspection[r.id] ?: emptyList()
            val issues = res.filter { it.status == CheckStatus.PROBLEM || it.status == CheckStatus.ATTENTION }
            para("${Fmt.date(r.day)} · ${Fmt.km(r.odometer)} · ${res.count { it.status == CheckStatus.OK }} baik, ${issues.size} perlu tindakan", bold)
            issues.forEach { para("• ${it.name}: ${it.status.label}${if (it.note.isNotBlank()) " – ${it.note}" else ""}", body, 8f) }
            if (r.notes.isNotBlank()) para(r.notes, small, 8f)
            y += 4f
        }
    }
}
