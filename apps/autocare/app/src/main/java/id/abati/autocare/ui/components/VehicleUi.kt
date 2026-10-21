package id.abati.autocare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Vehicle
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.ReminderState
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.util.Fmt

/** Kendaraan aktif yang dipilih di beranda (fallback ke kendaraan pertama). */
fun AppData.selectedVehicle(settings: AppSettings): Vehicle? =
    activeVehicles.firstOrNull { it.id == settings.selectedVehicleId } ?: activeVehicles.firstOrNull()

fun Vehicle.subtitle(): String = listOfNotNull(plate.takeIf { it.isNotBlank() }, year?.toString(), variant.takeIf { it.isNotBlank() }).joinToString(" · ")

/** Pilihan kendaraan sebagai chip; [allLabel] != null menambahkan opsi "semua". */
@Composable
fun VehicleChips(data: AppData, selectedId: String?, onSelect: (String?) -> Unit, allLabel: String? = null, modifier: Modifier = Modifier) {
    val vehicles = data.activeVehicles
    if (vehicles.size <= 1 && allLabel == null) return
    val options: List<String?> = (if (allLabel != null) listOf<String?>(null) else emptyList()) + vehicles.map { it.id }
    ChoiceChips(
        options = options,
        selected = selectedId,
        label = { id -> if (id == null) allLabel ?: "" else data.vehicleById[id]?.let { v -> v.plate.ifBlank { v.displayName } } ?: "" },
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
fun VehiclePhoto(vehicle: Vehicle, size: Int = 56) {
    val mod = Modifier.size(size.dp).clip(RoundedCornerShape(14.dp))
    if (vehicle.photo.isNotBlank()) {
        Thumbnail(vehicle.photo, "image/jpeg", mod)
    } else {
        Card(modifier = mod, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(top = (size / 4).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DirectionsCar, null, Modifier.size((size / 2).dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun VehicleSummaryRow(data: AppData, vehicle: Vehicle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VehiclePhoto(vehicle)
        Column(Modifier.weight(1f)) {
            Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vehicle.subtitle().isNotBlank()) Text(vehicle.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val odo = data.currentOdometer(vehicle.id)
            Text(
                odo?.let { "${Fmt.km(it.km)} · dicatat ${Fmt.date(it.day)}" } ?: "Odometer belum dicatat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Satu baris pengingat dengan status, jatuh tempo, sisa, dan progres. */
@Composable
fun ReminderRow(state: ReminderState, data: AppData, showVehicle: Boolean, onClick: () -> Unit, today: Long = Fmt.today()) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.reminder.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showVehicle && state.vehicle != null) {
                    Text(state.vehicle.plate.ifBlank { state.vehicle.displayName }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusBadge(state.info.status)
        }
        Text(
            Fmt.dueSummary(state.info),
            style = MaterialTheme.typography.bodyMedium,
        )
        val remaining = Fmt.remainingSummary(state.info)
        if (remaining.isNotBlank()) {
            Text(remaining, style = MaterialTheme.typography.bodySmall, color = statusColor(state.info.status))
        }
        val p = state.info.progress(today, data.currentOdometer(state.reminder.vehicleId)?.km)
        if (p != null && state.info.status != DueStatus.DONE) {
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = statusColor(state.info.status),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
