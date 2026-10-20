package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.ui.components.DateField
import id.abati.perjadin.ui.components.DropdownField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.MoneyField
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.ui.components.TipCard
import id.abati.perjadin.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    trip: Trip?,
    profile: Profile,
    existingOdr: Set<String>,
    onBack: () -> Unit,
    onSave: (Trip) -> Unit,
) {
    val isNew = trip == null
    var odr by rememberSaveable { mutableStateOf(trip?.odrNumber ?: "") }
    var purpose by rememberSaveable { mutableStateOf(trip?.purpose ?: "") }
    var origin by rememberSaveable { mutableStateOf(trip?.origin ?: "") }
    var destination by rememberSaveable { mutableStateOf(trip?.destination ?: "") }
    var start by rememberSaveable { mutableLongStateOf(trip?.startDay ?: Fmt.today()) }
    var end by rememberSaveable { mutableLongStateOf(trip?.endDay ?: (Fmt.today() + 2)) }
    var traveler by rememberSaveable { mutableStateOf(trip?.travelerName ?: profile.name) }
    var employeeId by rememberSaveable { mutableStateOf(trip?.employeeId ?: profile.employeeId) }
    var department by rememberSaveable { mutableStateOf(trip?.department ?: profile.department) }
    var advance by rememberSaveable { mutableLongStateOf(trip?.advance ?: 0L) }
    var status by rememberSaveable { mutableStateOf(trip?.status ?: TripStatus.PLANNED) }
    var notes by rememberSaveable { mutableStateOf(trip?.notes ?: "") }
    var useActual by rememberSaveable { mutableStateOf(trip?.hasActualDates ?: false) }
    var actualStart by rememberSaveable { mutableLongStateOf(trip?.effectiveStartDay ?: Fmt.today()) }
    var actualEnd by rememberSaveable { mutableLongStateOf(trip?.effectiveEndDay ?: (Fmt.today() + 2)) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val odrError = when {
        odr.isBlank() -> "Nomor ODR wajib diisi"
        odr.trim().lowercase() in existingOdr -> "Nomor ODR ini sudah dipakai perjalanan lain"
        else -> null
    }
    val purposeError = if (purpose.isBlank()) "Keperluan wajib diisi" else null
    val destError = if (destination.isBlank()) "Kota tujuan wajib diisi" else null
    val dateError = if (end < start) "Tanggal pulang tidak boleh sebelum tanggal berangkat" else null
    val actualError = if (useActual && actualEnd < actualStart) "Tanggal aktual pulang tidak boleh sebelum berangkat" else null
    val valid = listOf(odrError, purposeError, destError, dateError, actualError).all { it == null }

    fun save() {
        submitted = true
        if (!valid) return
        val base = trip ?: Trip(startDay = start, endDay = end)
        onSave(
            base.copy(
                odrNumber = odr.trim(),
                purpose = purpose.trim(),
                origin = origin.trim(),
                destination = destination.trim(),
                startDay = start,
                endDay = end,
                travelerName = traveler.trim(),
                employeeId = employeeId.trim(),
                department = department.trim(),
                advance = advance,
                status = status,
                notes = notes.trim(),
                actualStartDay = if (useActual) actualStart else -1,
                actualEndDay = if (useActual) actualEnd else -1,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Perjalanan Baru" else "Ubah Perjalanan", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { TextButton(onClick = ::save) { Text("Simpan", fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isNew) {
                TipCard("Salin data dari dokumen On Duty Request (ODR) / Surat Perintah Perjalanan Dinas Anda. Kolom bertanda * wajib diisi. Anggaran per kategori diisi setelah perjalanan disimpan.")
            }

            SectionTitle("Dokumen", subtitle = "Identitas perjalanan sesuai surat tugas")
            FormField(
                value = odr, onValueChange = { odr = it },
                label = "Nomor ODR / Surat Tugas *",
                placeholder = "Contoh: ODR/2026/10/0123",
                supporting = if (submitted && odrError != null) odrError else "Nomor On Duty Request dari sistem / HRD",
                isError = submitted && odrError != null,
                leading = Icons.Filled.Tag,
                capitalization = KeyboardCapitalization.Characters,
            )
            DropdownField(
                label = "Status Perjalanan",
                options = TripStatus.entries,
                selected = status,
                optionLabel = { it.label },
                onSelect = { status = it },
                leading = Icons.Filled.Flag,
                supporting = "Ubah ke \"Dilaporkan\" setelah laporan dikirim ke kantor",
            )

            SectionTitle("Detail Perjalanan")
            FormField(
                value = purpose, onValueChange = { purpose = it },
                label = "Keperluan / Tujuan Dinas *",
                placeholder = "Contoh: Audit stok gudang cabang Medan",
                supporting = if (submitted && purposeError != null) purposeError else "Tuliskan singkat & jelas, akan tampil di laporan",
                isError = submitted && purposeError != null,
                leading = Icons.Filled.Work,
                singleLine = false,
            )
            FormField(
                value = origin, onValueChange = { origin = it },
                label = "Kota Asal",
                placeholder = "Contoh: Jakarta",
                leading = Icons.Filled.FlightTakeoff,
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = destination, onValueChange = { destination = it },
                label = "Kota Tujuan *",
                placeholder = "Contoh: Medan, Sumatera Utara",
                supporting = if (submitted && destError != null) destError else null,
                isError = submitted && destError != null,
                leading = Icons.Filled.Place,
                capitalization = KeyboardCapitalization.Words,
            )
            DateField(day = start, onChange = { start = it; if (end < it) end = it }, label = "Tanggal Berangkat")
            DateField(
                day = end, onChange = { end = it }, label = "Tanggal Pulang",
                supporting = dateError ?: "Durasi: ${end - start + 1} hari",
                isError = dateError != null,
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Tanggal aktual berbeda dari rencana", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Aktifkan jika pulang lebih cepat / dinas diperpanjang. Uang harian lumpsum dihitung dari tanggal aktual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useActual, onCheckedChange = { on ->
                    if (on && !useActual) { actualStart = start; actualEnd = end }
                    useActual = on
                })
            }
            if (useActual) {
                DateField(day = actualStart, onChange = { actualStart = it; if (actualEnd < it) actualEnd = it }, label = "Berangkat (aktual)")
                DateField(
                    day = actualEnd, onChange = { actualEnd = it }, label = "Pulang (aktual)",
                    supporting = actualError ?: "Aktual ${actualEnd - actualStart + 1} hari · rencana ${end - start + 1} hari",
                    isError = actualError != null,
                )
            }

            SectionTitle("Pelaksana", subtitle = "Otomatis terisi dari Profil (bisa diubah)")
            FormField(
                value = traveler, onValueChange = { traveler = it },
                label = "Nama Pelaksana",
                placeholder = "Contoh: Budi Santoso",
                leading = Icons.Filled.Person,
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = employeeId, onValueChange = { employeeId = it },
                label = "NIK / No. Karyawan",
                placeholder = "Contoh: EMP-00421",
                leading = Icons.Filled.Badge,
                capitalization = KeyboardCapitalization.Characters,
            )
            FormField(
                value = department, onValueChange = { department = it },
                label = "Departemen / Divisi",
                placeholder = "Contoh: Operasional",
                leading = Icons.Filled.Business,
                capitalization = KeyboardCapitalization.Words,
            )

            SectionTitle("Keuangan")
            MoneyField(
                value = advance, onValueChange = { advance = it },
                label = "Uang Muka Diterima (opsional)",
                placeholder = "Contoh: 5.000.000",
                supporting = "Uang yang sudah diterima dari kantor sebelum berangkat. Kosongkan jika sistem reimburse.",
            )
            FormField(
                value = notes, onValueChange = { notes = it },
                label = "Catatan (opsional)",
                placeholder = "Contoh: Berangkat bersama tim IT, hotel dipesan oleh GA",
                singleLine = false,
                minLines = 3,
            )

            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (isNew) "Simpan & Lanjut Isi Anggaran" else "Simpan Perubahan")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
